package com.stockforecast.forecasting.domain.model;

import com.stockforecast.forecasting.domain.model.ExponentialSmoothing.Solution;
import com.stockforecast.forecasting.domain.model.ExponentialSmoothing.Variant;
import com.stockforecast.forecasting.domain.model.ExponentialSmoothing.Weights;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Reference values from statsmodels 0.14 ({@code SimpleExpSmoothing} / {@code Holt} with
 * {@code initialization_method='estimated'}), an independent implementation that estimates the initial state
 * numerically. Our closed-form least-squares initial state must match it, and our optimised weights must reach the
 * same error level within our (slightly narrower) parameter bounds.
 */
class ExponentialSmoothingTest {

    /** Same formula as the Python script that produced the reference values. */
    static double[] referenceSeries() {
        double[] y = new double[120];
        for (int t = 0; t < y.length; t++) {
            y[t] = Math.log(100) + 0.0008 * t + 0.02 * Math.sin(0.7 * t) + 0.015 * Math.cos(1.3 * t + 0.5)
                    + 0.01 * Math.sin(0.13 * t * t);
        }
        return y;
    }

    @Test
    void simpleSmoothingMatchesStatsmodelsForFixedAlpha() {
        Solution solution = ExponentialSmoothing.solve(referenceSeries(), Weights.simple(0.3));

        assertThat(solution.sse()).isCloseTo(0.055737040102932775, within(1e-10));
        assertThat(solution.initialLevel()).isCloseTo(4.617335704337012, within(1e-6));
        assertThat(solution.forecast(119, 3)).containsExactly(new double[]{4.704282082190357, 4.704282082190357,
                4.704282082190357}, within(1e-7));
    }

    @Test
    void holtLinearTrendMatchesStatsmodelsForFixedWeights() {
        Solution solution = ExponentialSmoothing.solve(referenceSeries(), Weights.holt(0.5, 0.2));

        assertThat(solution.sse()).isCloseTo(0.06780940105174736, within(1e-10));
        assertThat(solution.initialLevel()).isCloseTo(4.620954692564969, within(1e-6));
        assertThat(solution.initialTrend()).isCloseTo(-0.0011179724241401883, within(1e-7));
        assertThat(solution.forecast(119, 5)).containsExactly(new double[]{4.71415813848039, 4.717350205455734,
                4.720542272431078, 4.7237343394064215, 4.726926406381765}, within(1e-7));
    }

    @Test
    void dampedTrendMatchesStatsmodelsForFixedWeights() {
        Solution solution = ExponentialSmoothing.solve(referenceSeries(), Weights.damped(0.5, 0.2, 0.9));

        assertThat(solution.sse()).isCloseTo(0.06489010248827093, within(1e-10));
        assertThat(solution.initialLevel()).isCloseTo(4.622237908038641, within(1e-6));
        assertThat(solution.initialTrend()).isCloseTo(-0.0018951558669267675, within(1e-7));
        assertThat(solution.forecast(119, 5)).containsExactly(new double[]{4.713510740223904, 4.715910464293533,
                4.7180702159562, 4.7200139924526, 4.721763391299359}, within(1e-7));
    }

    @Test
    void estimatedWeightsReachTheConstrainedOptimum() {
        double[] y = referenceSeries();

        Weights simple = ExponentialSmoothing.estimate(y, Variant.SIMPLE, null, null, null);
        Weights holt = ExponentialSmoothing.estimate(y, Variant.HOLT, null, null, null);
        Weights damped = ExponentialSmoothing.estimate(y, Variant.DAMPED, null, null, null);

        // statsmodels' unconstrained optimum is alpha ~ 1, beta ~ 0 (phi 0.995). Within our bounds the reference is
        // statsmodels evaluated at the bound (alpha 0.9999; beta 0.0001; phi 0.98 with the best beta of a grid).
        assertThat(simple.alpha()).isGreaterThan(0.9998);
        assertThat(ExponentialSmoothing.solve(y, simple).sse()).isCloseTo(0.04342432279860761, within(1e-9));
        assertThat(ExponentialSmoothing.solve(y, holt).sse())
                .isBetween(0.04334302049265717 - 1e-9, 0.043349540564387494 + 1e-9);
        assertThat(holt.beta()).isLessThan(0.01);
        assertThat(ExponentialSmoothing.solve(y, damped).sse())
                .isBetween(0.04335781952365288 - 1e-9, 0.04340310571370661 + 1e-9);
        assertThat(damped.phi()).isBetween(ExponentialSmoothing.MIN_PHI, ExponentialSmoothing.MAX_PHI);
    }

    @Test
    void fixedWeightsAreRespected() {
        Weights weights = ExponentialSmoothing.estimate(referenceSeries(), Variant.DAMPED, 0.4, null, 0.85);

        assertThat(weights.alpha()).isEqualTo(0.4);
        assertThat(weights.phi()).isEqualTo(0.85);
        assertThat(weights.beta()).isBetween(ExponentialSmoothing.MIN_WEIGHT, ExponentialSmoothing.MAX_WEIGHT);
    }

    @Test
    void aiccPenalisesAdditionalParameters() {
        Solution solution = ExponentialSmoothing.solve(referenceSeries(), Weights.holt(0.5, 0.2));
        int n = 120;
        int k = 4;
        double expected = n * Math.log(solution.sse() / n) + 2 * k + 2.0 * k * (k + 1) / (n - k - 1);

        assertThat(solution.aicc(2)).isCloseTo(expected, within(1e-9));
    }
}
