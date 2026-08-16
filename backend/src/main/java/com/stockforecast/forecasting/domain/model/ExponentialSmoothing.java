package com.stockforecast.forecasting.domain.model;

import org.hipparchus.exception.MathRuntimeException;
import org.hipparchus.optim.InitialGuess;
import org.hipparchus.optim.MaxEval;
import org.hipparchus.optim.PointValuePair;
import org.hipparchus.optim.SimpleBounds;
import org.hipparchus.optim.nonlinear.scalar.GoalType;
import org.hipparchus.optim.nonlinear.scalar.ObjectiveFunction;
import org.hipparchus.optim.nonlinear.scalar.noderiv.BOBYQAOptimizer;
import org.hipparchus.optim.univariate.BrentOptimizer;
import org.hipparchus.optim.univariate.SearchInterval;
import org.hipparchus.optim.univariate.UnivariateObjectiveFunction;
import org.hipparchus.optim.univariate.UnivariatePointValuePair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * Additive exponential smoothing in error-correction form, with optional (damped) trend:
 *
 * <pre>
 *   ŷ(t) = l(t-1) + φ·b(t-1)            e(t) = y(t) - ŷ(t)
 *   l(t) = l(t-1) + φ·b(t-1) + α·e(t)    b(t) = φ·b(t-1) + α·β·e(t)
 *   ŷ(t+h | t) = l(t) + (φ + φ² + … + φ^h)·b(t)
 * </pre>
 * <p>
 * Simple exponential smoothing has no trend ({@code b ≡ 0}); Holt's linear trend has {@code φ = 1}.
 *
 * <p>The initial state {@code (l0, b0)} minimises the one-step sum of squared errors (SSE) for given weights. The
 * errors are affine in the initial state, so this is an exact 2×2 linear least-squares problem: three recursions
 * (data with a zero state, and the zero-data responses to a unit level and a unit trend) give everything needed.
 * The smoothing weights themselves are estimated with Hipparchus' Brent (one weight) or BOBYQA (two or three
 * weights) optimisers, started from the best points of a coarse grid that includes the parameter bounds.
 */
final class ExponentialSmoothing {

    static final double MIN_WEIGHT = 1e-4;
    static final double MAX_WEIGHT = 0.9999;
    static final double MIN_PHI = 0.80;
    static final double MAX_PHI = 0.98;
    private static final int GRID_LEVELS_1D = 21;
    private static final int GRID_LEVELS_2D = 9;
    private static final int GRID_LEVELS_3D = 6;
    private static final int STARTS = 3;

    private ExponentialSmoothing() {
    }

    /**
     * @param trend {@code false} for simple exponential smoothing ({@code beta} and {@code phi} are ignored)
     */
    record Weights(double alpha, double beta, double phi, boolean trend) {

        static Weights simple(double alpha) {
            return new Weights(alpha, 0, 1, false);
        }

        static Weights holt(double alpha, double beta) {
            return new Weights(alpha, beta, 1, true);
        }

        static Weights damped(double alpha, double beta, double phi) {
            return new Weights(alpha, beta, phi, true);
        }
    }

    /**
     * A fitted model: weights, least-squares initial state and the states after every observation.
     */
    record Solution(Weights weights, double initialLevel, double initialTrend, double sse, double[] levels,
                    double[] trends) {

        int observations() {
            return levels.length;
        }

        /** Forecasts (in the units of the data) for steps {@code 1..steps} after observation {@code index}. */
        double[] forecast(int index, int steps) {
            double[] forecast = new double[steps];
            double damping = 0;
            double power = 1;
            for (int k = 1; k <= steps; k++) {
                power *= weights.phi();
                damping += power;
                forecast[k - 1] = levels[index] + (weights.trend() ? damping * trends[index] : 0);
            }
            return forecast;
        }

        /** Corrected Akaike information criterion; counts estimated weights plus the initial states. */
        double aicc(int estimatedWeights) {
            int n = observations();
            int k = estimatedWeights + (weights.trend() ? 2 : 1);
            double aic = n * Math.log(Math.max(sse, Double.MIN_NORMAL) / n) + 2 * k;
            return aic + 2.0 * k * (k + 1) / (n - k - 1);
        }
    }

    static Solution solve(double[] y, Weights weights) {
        double shift = y[0];
        double[] centred = centred(y, shift);
        double[] initial = initialState(centred, weights);
        int n = y.length;
        double[] levels = new double[n];
        double[] trends = new double[n];
        double level = initial[0];
        double trend = initial[1];
        double sse = 0;
        for (int t = 0; t < n; t++) {
            double predicted = level + (weights.trend() ? weights.phi() * trend : 0);
            double error = centred[t] - predicted;
            sse += error * error;
            level = predicted + weights.alpha() * error;
            if (weights.trend()) {
                trend = weights.phi() * trend + weights.alpha() * weights.beta() * error;
            }
            levels[t] = level + shift;
            trends[t] = trend;
        }
        return new Solution(weights, initial[0] + shift, initial[1], sse, levels, trends);
    }

    /** One-step SSE with the least-squares initial state; the optimiser's objective. */
    static double profiledSse(double[] centred, Weights weights) {
        Sums sums = sums(centred, weights);
        if (!weights.trend()) {
            return sums.ee - sums.el * sums.el / sums.ll;
        }
        double determinant = sums.ll * sums.bb - sums.lb * sums.lb;
        if (determinant <= 1e-12 * sums.ll * sums.bb) {
            return sums.ee - sums.el * sums.el / sums.ll;
        }
        return sums.ee - (sums.bb * sums.el * sums.el - 2 * sums.lb * sums.el * sums.eb + sums.ll * sums.eb * sums.eb)
                / determinant;
    }

    /**
     * Estimates the weights that are not fixed by minimising the profiled SSE.
     *
     * @param fixedAlpha fixed level weight, or {@code null} to estimate it (likewise for beta and phi)
     */
    static Weights estimate(double[] y, Variant variant, Double fixedAlpha, Double fixedBeta, Double fixedPhi) {
        double[] centred = centred(y, y[0]);
        List<double[]> bounds = new ArrayList<>();
        if (fixedAlpha == null) {
            bounds.add(new double[]{MIN_WEIGHT, MAX_WEIGHT});
        }
        if (variant != Variant.SIMPLE && fixedBeta == null) {
            bounds.add(new double[]{MIN_WEIGHT, MAX_WEIGHT});
        }
        if (variant == Variant.DAMPED && fixedPhi == null) {
            bounds.add(new double[]{MIN_PHI, MAX_PHI});
        }
        Function<double[], Weights> toWeights = free -> {
            int i = 0;
            double alpha = fixedAlpha != null ? fixedAlpha : free[i++];
            if (variant == Variant.SIMPLE) {
                return Weights.simple(alpha);
            }
            double beta = fixedBeta != null ? fixedBeta : free[i++];
            if (variant == Variant.HOLT) {
                return Weights.holt(alpha, beta);
            }
            return Weights.damped(alpha, beta, fixedPhi != null ? fixedPhi : free[i]);
        };
        int dimension = bounds.size();
        if (dimension == 0) {
            return toWeights.apply(new double[0]);
        }
        // Optimise in the unit cube so every dimension has the same scale.
        Function<double[], double[]> fromUnit = unit -> {
            double[] point = new double[dimension];
            for (int d = 0; d < dimension; d++) {
                double[] range = bounds.get(d);
                point[d] = range[0] + Math.min(1, Math.max(0, unit[d])) * (range[1] - range[0]);
            }
            return point;
        };
        Function<double[], Double> objective = unit -> profiledSse(centred, toWeights.apply(fromUnit.apply(unit)));
        // The error surface can have several local minima (often on the bounds), so the local optimiser is started
        // from the best few points of a grid that includes the bounds, and the lowest result wins.
        List<double[]> starts = gridSearch(dimension, objective);
        double[] best = starts.getFirst();
        double bestValue = objective.apply(best);
        for (double[] start : starts) {
            double[] refined = dimension == 1 ? refineWithBrent(start[0], objective) : refineWithBobyqa(start, objective);
            if (refined != null) {
                double value = objective.apply(refined);
                if (value < bestValue) {
                    best = refined;
                    bestValue = value;
                }
            }
        }
        return toWeights.apply(fromUnit.apply(best));
    }

    /** The {@value #STARTS} best points of a regular grid over the unit cube (bounds included), best first. */
    private static List<double[]> gridSearch(int dimension, Function<double[], Double> objective) {
        int levels = dimension == 1 ? GRID_LEVELS_1D : dimension == 2 ? GRID_LEVELS_2D : GRID_LEVELS_3D;
        int combinations = (int) Math.pow(levels, dimension);
        List<double[]> points = new ArrayList<>(combinations);
        List<Double> values = new ArrayList<>(combinations);
        for (int combination = 0; combination < combinations; combination++) {
            double[] unit = new double[dimension];
            int remainder = combination;
            for (int d = 0; d < dimension; d++) {
                unit[d] = (double) (remainder % levels) / (levels - 1);
                remainder /= levels;
            }
            points.add(unit);
            values.add(objective.apply(unit));
        }
        return IntStream.range(0, combinations).boxed()
                .sorted(Comparator.comparingDouble(values::get))
                .limit(STARTS).map(points::get).toList();
    }

    /** Brent on the grid cell around the start, so it stays in that start's basin. */
    private static double[] refineWithBrent(double start, Function<double[], Double> objective) {
        double halfCell = 1.0 / (GRID_LEVELS_1D - 1);
        double lower = Math.max(0, start - halfCell);
        double upper = Math.min(1, start + halfCell);
        try {
            UnivariatePointValuePair optimum = new BrentOptimizer(1e-10, 1e-12).optimize(
                    new MaxEval(500), GoalType.MINIMIZE,
                    new UnivariateObjectiveFunction(u -> objective.apply(new double[]{u})),
                    new SearchInterval(lower, upper, start));
            return new double[]{optimum.getPoint()};
        } catch (MathRuntimeException ex) {
            return null;
        }
    }

    private static double[] refineWithBobyqa(double[] start, Function<double[], Double> objective) {
        int dimension = start.length;
        double[] lower = new double[dimension];
        double[] upper = new double[dimension];
        Arrays.fill(upper, 1);
        try {
            PointValuePair optimum = new BOBYQAOptimizer(2 * dimension + 1, 0.1, 1e-8).optimize(
                    new MaxEval(3000), GoalType.MINIMIZE, new ObjectiveFunction(objective::apply),
                    new InitialGuess(start), new SimpleBounds(lower, upper));
            return optimum.getPoint();
        } catch (MathRuntimeException ex) {
            return null;
        }
    }

    /** Least-squares initial state {@code {l0, b0}} for centred data. */
    private static double[] initialState(double[] centred, Weights weights) {
        Sums sums = sums(centred, weights);
        if (!weights.trend()) {
            return new double[]{-sums.el / sums.ll, 0};
        }
        double determinant = sums.ll * sums.bb - sums.lb * sums.lb;
        if (determinant <= 1e-12 * sums.ll * sums.bb) {
            return new double[]{-sums.el / sums.ll, 0};
        }
        double level = -(sums.bb * sums.el - sums.lb * sums.eb) / determinant;
        double trend = -(sums.ll * sums.eb - sums.lb * sums.el) / determinant;
        return new double[]{level, trend};
    }

    /**
     * Runs three recursions at once: the data from a zero state ({@code e}), and with zero data the response to a
     * unit initial level ({@code l}) and a unit initial trend ({@code b}); returns their cross products.
     */
    private static Sums sums(double[] centred, Weights weights) {
        double alpha = weights.alpha();
        double trendGain = weights.alpha() * weights.beta();
        double phi = weights.trend() ? weights.phi() : 0;
        double eLevel = 0;
        double eTrend = 0;
        double lLevel = 1;
        double lTrend = 0;
        double bLevel = 0;
        double bTrend = 1;
        Sums sums = new Sums();
        for (double observation : centred) {
            double ePredicted = eLevel + phi * eTrend;
            double eError = observation - ePredicted;
            eLevel = ePredicted + alpha * eError;
            eTrend = phi * eTrend + trendGain * eError;

            double lPredicted = lLevel + phi * lTrend;
            double lError = -lPredicted;
            lLevel = lPredicted + alpha * lError;
            lTrend = phi * lTrend + trendGain * lError;

            double bPredicted = bLevel + phi * bTrend;
            double bError = -bPredicted;
            bLevel = bPredicted + alpha * bError;
            bTrend = phi * bTrend + trendGain * bError;

            sums.ee += eError * eError;
            sums.el += eError * lError;
            sums.eb += eError * bError;
            sums.ll += lError * lError;
            sums.lb += lError * bError;
            sums.bb += bError * bError;
        }
        return sums;
    }

    private static double[] centred(double[] y, double shift) {
        double[] centred = new double[y.length];
        for (int i = 0; i < y.length; i++) {
            centred[i] = y[i] - shift;
        }
        return centred;
    }

    enum Variant {
        SIMPLE,
        HOLT,
        DAMPED
    }

    private static final class Sums {
        private double ee;
        private double el;
        private double eb;
        private double ll;
        private double lb;
        private double bb;
    }
}
