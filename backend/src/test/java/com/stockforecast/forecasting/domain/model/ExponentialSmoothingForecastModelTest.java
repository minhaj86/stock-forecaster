package com.stockforecast.forecasting.domain.model;

import com.stockforecast.forecasting.domain.FittedParameter;
import com.stockforecast.forecasting.domain.ForecastInput;
import com.stockforecast.forecasting.domain.ForecastRequest;
import com.stockforecast.forecasting.domain.ForecastTarget;
import com.stockforecast.forecasting.domain.InvalidForecastParameterException;
import com.stockforecast.forecasting.domain.ModelForecast;
import com.stockforecast.forecasting.domain.ModelParameters;
import com.stockforecast.forecasting.domain.TestSeries;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ExponentialSmoothingForecastModelTest {

    private static final EmpiricalIntervalCalibrator CALIBRATOR = new EmpiricalIntervalCalibrator(250, 20);
    private final ExponentialSmoothingForecastModel model = new ExponentialSmoothingForecastModel(CALIBRATOR,
            new ExponentialSmoothingForecastModel.Settings(504, true, 0.5, 0.1, 0.95));
    private final ForecastInput randomWalk = TestSeries.of(TestSeries.randomWalk(600, 80, 0.0004, 0.015, 11));

    @Test
    void autoSelectsTheVariantWithTheLowestAicc() {
        ModelForecast automatic = model.forecast(randomWalk, request(Map.of()));

        double chosen = fitted(automatic).get("aicc");
        for (String variant : new String[]{"SIMPLE", "HOLT", "DAMPED"}) {
            double candidate = fitted(model.forecast(randomWalk, request(Map.of("variant", variant)))).get("aicc");
            assertThat(chosen).as("AICc versus %s", variant).isLessThanOrEqualTo(candidate + 1e-9);
        }
        assertThat(automatic.specification()).endsWith(", selected by AICc");
        assertThat(automatic.notes()).anyMatch(note -> note.startsWith("AICc (lower is better): simple"));
    }

    @Test
    void holtExtrapolatesExactExponentialGrowth() {
        double rate = 0.0015;
        ModelForecast forecast = model.forecast(TestSeries.of(TestSeries.exponentialGrowth(300, 40, rate)),
                request(Map.of("variant", "HOLT")));

        assertThat(forecast.step(5).expected()).isCloseTo(40 * Math.exp(rate * 304), within(1e-6));
        assertThat(forecast.specification()).startsWith("Holt linear trend");
    }

    @Test
    void fixedWeightsAreUsedAsGiven() {
        ModelForecast forecast = model.forecast(randomWalk,
                request(Map.of("variant", "DAMPED", "alpha", "0.4", "beta", "0.05", "phi", "0.9")));

        Map<String, Double> fitted = fitted(forecast);
        assertThat(fitted).containsEntry("alpha", 0.4).containsEntry("beta", 0.05).containsEntry("phi", 0.9);
        assertThat(forecast.notes()).contains("Smoothing weights were fixed rather than estimated from the data.");
    }

    @Test
    void configuredDefaultsApplyWhenOptimisationIsDisabled() {
        ExponentialSmoothingForecastModel fixed = new ExponentialSmoothingForecastModel(CALIBRATOR,
                new ExponentialSmoothingForecastModel.Settings(504, false, 0.3, 0.2, 0.9));

        Map<String, Double> fitted = fitted(fixed.forecast(randomWalk, request(Map.of("variant", "HOLT"))));

        assertThat(fitted).containsEntry("alpha", 0.3).containsEntry("beta", 0.2);
    }

    @Test
    void fitsOnlyTheLookbackWindow() {
        ModelForecast forecast = model.forecast(randomWalk, request(Map.of("variant", "SIMPLE", "lookback", "100")));

        assertThat(fitted(forecast).get("observations")).isEqualTo(100);
        assertThat(forecast.path()).allSatisfy(point -> assertThat(point.lower()).isPositive());
    }

    @Test
    void rejectsWeightsThatTheVariantDoesNotUse() {
        assertThatThrownBy(() -> model.resolveParameters(ModelParameters.of(Map.of("alpha", "0.5"))))
                .isInstanceOf(InvalidForecastParameterException.class)
                .hasMessageContaining("can only be fixed for an explicit variant");
        assertThatThrownBy(() -> model.resolveParameters(ModelParameters.of(Map.of("variant", "SIMPLE",
                "beta", "0.1")))).hasMessage("Parameter 'beta' is not used by the SIMPLE variant");
        assertThatThrownBy(() -> model.resolveParameters(ModelParameters.of(Map.of("variant", "HOLT", "phi", "0.9"))))
                .hasMessage("Parameter 'phi' is not used by the HOLT variant");
        assertThatThrownBy(() -> model.resolveParameters(ModelParameters.of(Map.of("variant", "DAMPED",
                "phi", "0.99")))).hasMessage("Parameter 'phi' must be between 0.8 and 0.98");
    }

    private static Map<String, Double> fitted(ModelForecast forecast) {
        return forecast.fitted().stream().collect(Collectors.toMap(FittedParameter::key, FittedParameter::value));
    }

    private static ForecastRequest request(Map<String, String> parameters) {
        return new ForecastRequest(ForecastTarget.PRICE, 10, 0.8, ModelParameters.of(parameters));
    }
}
