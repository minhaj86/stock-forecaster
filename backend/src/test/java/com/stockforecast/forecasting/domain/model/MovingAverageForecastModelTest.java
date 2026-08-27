package com.stockforecast.forecasting.domain.model;

import com.stockforecast.forecasting.domain.FittedParameter;
import com.stockforecast.forecasting.domain.ForecastRequest;
import com.stockforecast.forecasting.domain.ForecastTarget;
import com.stockforecast.forecasting.domain.InsufficientForecastHistoryException;
import com.stockforecast.forecasting.domain.InvalidForecastParameterException;
import com.stockforecast.forecasting.domain.ModelForecast;
import com.stockforecast.forecasting.domain.ModelParameters;
import com.stockforecast.forecasting.domain.PathPoint;
import com.stockforecast.forecasting.domain.TestSeries;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

class MovingAverageForecastModelTest {

    private final MovingAverageForecastModel model =
            new MovingAverageForecastModel(new EmpiricalIntervalCalibrator(100, 2), 20);

    @Test
    void forecastsTheAverageOfTheLastWindowAsAFlatLine() {
        ModelForecast forecast = model.forecast(TestSeries.of(10, 11, 12, 13, 14, 15, 16, 17),
                request(Map.of("window", "5")));

        assertThat(forecast.specification()).isEqualTo("SMA(5)");
        assertThat(forecast.path()).extracting(PathPoint::expected).containsOnly(15.0);
        assertThat(forecast.fitted()).extracting(FittedParameter::key, FittedParameter::value)
                .containsExactly(tuple("movingAverage", 15.0),
                        tuple("lastCloseVsAverage", 17.0 / 15 - 1));
        assertThat(forecast.parameters().values()).containsEntry("window", "5");
    }

    @Test
    void usesTheConfiguredDefaultWindow() {
        double[] closes = TestSeries.randomWalk(300, 50, 0.0005, 0.01, 7);
        double expected = 0;
        for (int i = closes.length - 20; i < closes.length; i++) {
            expected += closes[i] / 20;
        }

        ModelForecast forecast = model.forecast(TestSeries.of(closes), request(Map.of()));

        assertThat(forecast.specification()).isEqualTo("SMA(20)");
        assertThat(forecast.step(3).expected()).isCloseTo(expected, within(1e-9));
        assertThat(forecast.step(3).lower()).isLessThan(expected);
        assertThat(forecast.step(3).upper()).isGreaterThan(expected);
    }

    @Test
    void requiresAtLeastOneFullWindowAndAValidWindow() {
        assertThat(model.minimumObservations(ModelParameters.of(Map.of("window", "50")))).isEqualTo(50);
        assertThatThrownBy(() -> model.forecast(TestSeries.of(1, 2, 3, 4), request(Map.of("window", "5"))))
                .isInstanceOf(InsufficientForecastHistoryException.class);
        assertThatThrownBy(() -> model.resolveParameters(ModelParameters.of(Map.of("window", "201"))))
                .isInstanceOf(InvalidForecastParameterException.class);
    }

    private static ForecastRequest request(Map<String, String> parameters) {
        return new ForecastRequest(ForecastTarget.PRICE, 5, 0.8, ModelParameters.of(parameters));
    }
}
