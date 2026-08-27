package com.stockforecast.forecasting.domain.model;

import com.stockforecast.forecasting.domain.FittedParameter;
import com.stockforecast.forecasting.domain.ForecastRequest;
import com.stockforecast.forecasting.domain.ForecastTarget;
import com.stockforecast.forecasting.domain.ModelForecast;
import com.stockforecast.forecasting.domain.ModelParameters;
import com.stockforecast.forecasting.domain.PathPoint;
import com.stockforecast.forecasting.domain.TestSeries;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Golden values from {@code numpy.polyfit} on the log closes of the last 60 points of
 * {@code 100 * exp(0.001 t + 0.02 sin(0.5 t))}, t = 0..99.
 */
class LinearRegressionForecastModelTest {

    private final LinearRegressionForecastModel model =
            new LinearRegressionForecastModel(new EmpiricalIntervalCalibrator(250, 20), 252);

    @Test
    void fitsTheLogPriceTrendOverTheLookback() {
        double[] closes = new double[100];
        for (int t = 0; t < closes.length; t++) {
            closes[t] = 100 * Math.exp(0.001 * t + 0.02 * Math.sin(0.5 * t));
        }

        ModelForecast forecast = model.forecast(TestSeries.of(closes), request(Map.of("lookback", "60"), 3));

        Map<String, Double> fitted = fitted(forecast);
        assertThat(fitted.get("slope")).isCloseTo(0.0008938929691590417, within(1e-12));
        assertThat(fitted.get("intercept")).isCloseTo(4.64813274102384, within(1e-10));
        assertThat(fitted.get("rSquared")).isCloseTo(0.5414415350352251, within(1e-10));
        assertThat(fitted.get("annualizedTrend")).isCloseTo(0.25264965043913157, within(1e-9));
        assertThat(fitted.get("trendValue")).isCloseTo(110.0431351005289, within(1e-8));
        double[] expected = {110.14154586309093, 110.2400446336708, 110.33863149097327};
        assertThat(forecast.path()).extracting(PathPoint::step).containsExactly(1, 2, 3);
        for (int k = 1; k <= 3; k++) {
            assertThat(forecast.step(k).expected()).isCloseTo(expected[k - 1], within(1e-8));
        }
        assertThat(forecast.specification()).isEqualTo("Log-linear trend over the last 60 trading days");
    }

    @Test
    void extrapolatesExactExponentialGrowthExactly() {
        double rate = 0.001;
        ModelForecast forecast = model.forecast(TestSeries.of(TestSeries.exponentialGrowth(300, 50, rate)),
                request(Map.of(), 10));

        assertThat(fitted(forecast).get("rSquared")).isCloseTo(1, within(1e-12));
        assertThat(forecast.step(10).expected()).isCloseTo(50 * Math.exp(rate * 309), within(1e-8));
        assertThat(forecast.specification()).isEqualTo("Log-linear trend over the last 252 trading days");
    }

    @Test
    void usesAllHistoryWhenTheLookbackIsNotYetAvailable() {
        ModelForecast forecast = model.forecast(TestSeries.of(TestSeries.randomWalk(100, 20, 0, 0.01, 3)),
                request(Map.of(), 1));

        assertThat(fitted(forecast).get("observations")).isEqualTo(100);
        assertThat(model.minimumObservations(ModelParameters.none()))
                .isEqualTo(LinearRegressionForecastModel.MINIMUM_FIT_OBSERVATIONS);
    }

    private static Map<String, Double> fitted(ModelForecast forecast) {
        return forecast.fitted().stream().collect(Collectors.toMap(FittedParameter::key, FittedParameter::value));
    }

    private static ForecastRequest request(Map<String, String> parameters, int horizon) {
        return new ForecastRequest(ForecastTarget.PRICE, horizon, 0.8, ModelParameters.of(parameters));
    }
}
