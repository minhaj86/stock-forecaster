package com.stockforecast.forecasting.domain.backtest;

import com.stockforecast.forecasting.domain.ForecastTarget;
import com.stockforecast.forecasting.domain.backtest.BacktestMetrics.BaselineComparison;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Golden values computed with numpy for four forecasts (origin, actual, predicted price; range):
 * (100, 104, 102; 97-107), (104, 101, 105; 102-111), (101, 103, 101; 96-106), (103, 99, 104; 98-110).
 */
class BacktestMetricsTest {

    private static final double[][] CASES = {
            {100, 104, 102, 97, 107}, {104, 101, 105, 102, 111}, {101, 103, 101, 96, 106}, {103, 99, 104, 98, 110}};

    @Test
    void priceMetrics() {
        BacktestMetrics metrics = BacktestMetrics.compute(observations(ForecastTarget.PRICE), ForecastTarget.PRICE,
                false);

        assertThat(metrics.observations()).isEqualTo(4);
        assertThat(metrics.mae()).isCloseTo(3.25, within(1e-12));
        assertThat(metrics.rmse()).isCloseTo(3.5, within(1e-12));
        assertThat(metrics.meanError()).isCloseTo(-1.25, within(1e-12));
        assertThat(metrics.mape()).isCloseTo(0.03218931396500367, within(1e-12));
        assertThat(metrics.smape()).isCloseTo(0.03178033851639082, within(1e-12));
        assertThat(metrics.intervalCoverage()).isEqualTo(0.75);
        assertThat(metrics.naiveRmse()).isCloseTo(3.3541019662496847, within(1e-12));
        assertThat(metrics.skillVsNaive()).isCloseTo(-0.0434983894999017, within(1e-12));
        assertThat(metrics.baselineComparison()).isEqualTo(BaselineComparison.SIMILAR);
    }

    @Test
    void directionAccuracyOnlyCountsDirectionalCalls() {
        BacktestMetrics metrics = BacktestMetrics.compute(observations(ForecastTarget.PRICE), ForecastTarget.PRICE,
                false);

        // The third forecast predicts no change, so it makes no call.
        assertThat(metrics.directionalCalls()).isEqualTo(3);
        assertThat(metrics.directionAccuracy()).isCloseTo(1.0 / 3, within(1e-12));
        assertThat(metrics.upMoveShare()).isEqualTo(0.5);
    }

    @Test
    void returnMetricsAreInReturnUnitsWithoutPercentageErrors() {
        BacktestMetrics metrics = BacktestMetrics.compute(observations(ForecastTarget.RETURN), ForecastTarget.RETURN,
                false);

        assertThat(metrics.mae()).isCloseTo(0.03170180199498665, within(1e-12));
        assertThat(metrics.rmse()).isCloseTo(0.034014328353745106, within(1e-12));
        assertThat(metrics.naiveRmse()).isCloseTo(0.03291038007260306, within(1e-12));
        assertThat(metrics.mape()).isNull();
        assertThat(metrics.smape()).isNull();
        assertThat(metrics.directionAccuracy()).isCloseTo(1.0 / 3, within(1e-12));
    }

    @Test
    void theBaselineIsNotComparedWithItself() {
        BacktestMetrics metrics = BacktestMetrics.compute(observations(ForecastTarget.PRICE), ForecastTarget.PRICE,
                true);

        assertThat(metrics.skillVsNaive()).isNull();
        assertThat(metrics.baselineComparison()).isEqualTo(BaselineComparison.NOT_APPLICABLE);
    }

    @Test
    void materiallyLowerErrorThanTheBaselineIsBetter() {
        BacktestObservation good = observation(ForecastTarget.PRICE, new double[]{100, 110, 109, 100, 120});

        BacktestMetrics metrics = BacktestMetrics.compute(List.of(good), ForecastTarget.PRICE, false);

        assertThat(metrics.skillVsNaive()).isCloseTo(0.9, within(1e-12));
        assertThat(metrics.baselineComparison()).isEqualTo(BaselineComparison.BETTER);
    }

    private static List<BacktestObservation> observations(ForecastTarget target) {
        return java.util.Arrays.stream(CASES).map(values -> observation(target, values)).toList();
    }

    private static BacktestObservation observation(ForecastTarget target, double[] values) {
        double origin = values[0];
        LocalDate date = LocalDate.of(2026, 1, 5);
        return new BacktestObservation(date, date.plusDays(7), origin, values[1], values[2],
                target.fromPrice(values[1], origin), target.fromPrice(values[2], origin),
                target.fromPrice(values[3], origin), target.fromPrice(values[4], origin));
    }
}
