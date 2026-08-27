package com.stockforecast.forecasting.domain.model;

import com.stockforecast.forecasting.domain.IntervalCalibration.Method;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Golden values computed with numpy: RMS of {@code ln(P(o + k) / P(o))} over all origins, i.e. the naive model's
 * k-step log errors.
 */
class EmpiricalIntervalCalibratorTest {

    private static final double[] CLOSES = {100, 101, 99, 102, 104, 103, 105, 107, 106, 108};

    @Test
    void scalesAreTheRootMeanSquareOfMeasuredLogErrors() {
        EmpiricalIntervalCalibrator.Calibration calibration = new EmpiricalIntervalCalibrator(100, 2)
                .calibrate(naive(), CLOSES, 2);

        assertThat(calibration.scale(1)).isCloseTo(0.018330538527467258, within(1e-12));
        assertThat(calibration.scale(2)).isCloseTo(0.02355963175549262, within(1e-12));
        assertThat(calibration.summary().method()).isEqualTo(Method.EMPIRICAL);
        assertThat(calibration.summary().origins()).isEqualTo(9);
        assertThat(calibration.summary().measuredSteps()).isEqualTo(2);
    }

    @Test
    void stepsWithTooFewErrorsAreExtrapolatedWithTheSquareRootOfTime() {
        EmpiricalIntervalCalibrator.Calibration calibration = new EmpiricalIntervalCalibrator(100, 5)
                .calibrate(naive(), CLOSES, 12);

        assertThat(calibration.summary().method()).isEqualTo(Method.EMPIRICAL_EXTRAPOLATED);
        assertThat(calibration.summary().measuredSteps()).isEqualTo(5);
        assertThat(calibration.scale(5)).isCloseTo(0.04759186654072047, within(1e-12));
        assertThat(calibration.scale(12)).isCloseTo(0.07372900261085455, within(1e-12));
        assertNonDecreasing(calibration.scales());
    }

    @Test
    void fallsBackToVolatilityScalingWithoutEnoughMeasuredErrors() {
        EmpiricalIntervalCalibrator.Calibration calibration = new EmpiricalIntervalCalibrator(100, 50)
                .calibrate(naive(), CLOSES, 4);
        double dailyVolatility = sampleStandardDeviationOfLogReturns(CLOSES);

        assertThat(calibration.summary().method()).isEqualTo(Method.VOLATILITY_SCALED);
        assertThat(calibration.summary().measuredSteps()).isZero();
        assertThat(calibration.scale(1)).isCloseTo(dailyVolatility, within(1e-12));
        assertThat(calibration.scale(4)).isCloseTo(dailyVolatility * 2, within(1e-12));
    }

    @Test
    void onlyTheMostRecentOriginsAreUsed() {
        EmpiricalIntervalCalibrator.Calibration calibration = new EmpiricalIntervalCalibrator(3, 2)
                .calibrate(naive(), CLOSES, 1);
        double expected = Math.sqrt((sq(Math.log(107.0 / 105)) + sq(Math.log(106.0 / 107)) + sq(Math.log(108.0 / 106)))
                / 3);

        assertThat(calibration.summary().origins()).isEqualTo(3);
        assertThat(calibration.scale(1)).isCloseTo(expected, within(1e-12));
    }

    private static PointForecaster naive() {
        return new PointForecaster() {
            @Override
            public int firstOrigin() {
                return 0;
            }

            @Override
            public double[] forecastFrom(int origin, int steps) {
                double[] forecast = new double[steps];
                Arrays.fill(forecast, CLOSES[origin]);
                return forecast;
            }
        };
    }

    static void assertNonDecreasing(double[] scales) {
        for (int k = 1; k < scales.length; k++) {
            assertThat(scales[k]).as("scale at step %d", k + 1).isGreaterThanOrEqualTo(scales[k - 1]);
        }
    }

    private static double sampleStandardDeviationOfLogReturns(double[] closes) {
        double[] returns = new double[closes.length - 1];
        for (int i = 1; i < closes.length; i++) {
            returns[i - 1] = Math.log(closes[i] / closes[i - 1]);
        }
        double mean = Arrays.stream(returns).average().orElseThrow();
        return Math.sqrt(Arrays.stream(returns).map(r -> sq(r - mean)).sum() / (returns.length - 1));
    }

    private static double sq(double value) {
        return value * value;
    }
}
