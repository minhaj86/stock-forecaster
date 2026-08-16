package com.stockforecast.forecasting.domain.model;

import com.stockforecast.forecasting.domain.IntervalCalibration;
import com.stockforecast.forecasting.domain.IntervalCalibration.Method;
import com.stockforecast.quant.Indicators;

/**
 * Derives uncertainty ranges from the model's own past errors instead of textbook formulas that assume independent
 * residuals (which daily prices violate, giving overconfident ranges).
 *
 * <p>For each of the last {@code calibrationOrigins} origins of the training series, the forecaster predicts the
 * following steps using only data up to that origin; the root-mean-square log error at each step {@code k} gives a
 * scale {@code s(k)}. Ranges are {@code F(k) * exp(±z * s(k))}, so they widen with the horizon and never include
 * negative prices. Steps with fewer than {@code minimumSamples} measured errors are extrapolated with
 * {@code s(K) * sqrt(k / K)}; the scales are made non-decreasing in {@code k}.
 */
public final class EmpiricalIntervalCalibrator {

    private static final int VOLATILITY_FALLBACK_DAYS = 250;

    private final int calibrationOrigins;
    private final int minimumSamples;

    public EmpiricalIntervalCalibrator(int calibrationOrigins, int minimumSamples) {
        if (calibrationOrigins < 1 || minimumSamples < 2) {
            throw new IllegalArgumentException("calibrationOrigins must be >= 1 and minimumSamples >= 2");
        }
        this.calibrationOrigins = calibrationOrigins;
        this.minimumSamples = minimumSamples;
    }

    public Calibration calibrate(PointForecaster forecaster, double[] closes, int horizon) {
        int last = closes.length - 1;
        double[] sumOfSquares = new double[horizon + 1];
        int[] counts = new int[horizon + 1];
        int origins = 0;
        for (int origin = Math.max(forecaster.firstOrigin(), last - calibrationOrigins); origin < last; origin++) {
            int steps = Math.min(horizon, last - origin);
            double[] forecast = forecaster.forecastFrom(origin, steps);
            for (int k = 1; k <= steps; k++) {
                double error = Math.log(closes[origin + k] / forecast[k - 1]);
                sumOfSquares[k] += error * error;
                counts[k]++;
            }
            origins++;
        }
        int measured = 0;
        while (measured < horizon && counts[measured + 1] >= minimumSamples) {
            measured++;
        }
        double[] scales = new double[horizon];
        Method method;
        if (measured == 0) {
            double dailyVolatility = dailyVolatility(closes);
            for (int k = 1; k <= horizon; k++) {
                scales[k - 1] = dailyVolatility * Math.sqrt(k);
            }
            method = Method.VOLATILITY_SCALED;
        } else {
            for (int k = 1; k <= measured; k++) {
                scales[k - 1] = Math.sqrt(sumOfSquares[k] / counts[k]);
            }
            for (int k = measured + 1; k <= horizon; k++) {
                scales[k - 1] = scales[measured - 1] * Math.sqrt((double) k / measured);
            }
            method = measured == horizon ? Method.EMPIRICAL : Method.EMPIRICAL_EXTRAPOLATED;
        }
        for (int k = 1; k < horizon; k++) {
            scales[k] = Math.max(scales[k], scales[k - 1]);
        }
        return new Calibration(scales, new IntervalCalibration(method, origins, measured));
    }

    private static double dailyVolatility(double[] closes) {
        int window = Math.min(closes.length - 1, VOLATILITY_FALLBACK_DAYS);
        if (window < 2) {
            return 0;
        }
        return Indicators.last(Indicators.rollingVolatility(closes, window))
                / Math.sqrt(Indicators.TRADING_DAYS_PER_YEAR);
    }

    /**
     * @param scales log-scale error scale per step ({@code scales[k - 1]} is step {@code k})
     */
    public record Calibration(double[] scales, IntervalCalibration summary) {

        public double scale(int step) {
            return scales[step - 1];
        }
    }
}
