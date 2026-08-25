package com.stockforecast.forecasting.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelParametersTest {

    private static final List<ParameterSpec> SPECS = List.of(
            ParameterSpec.integer("window", "Window", 20, 5, 200, "Closes averaged"),
            ParameterSpec.choice("variant", "Variant", "AUTO", List.of("AUTO", "SIMPLE"), "Variant"),
            ParameterSpec.optionalDecimal("alpha", "Alpha", 0.0001, 0.9999, "Smoothing"));

    @Test
    void fillsDefaultsAndNormalisesValues() {
        ModelParameters resolved = ModelParameters.of(Map.of("window", " 050 ", "variant", "simple"))
                .resolve(ForecastModelType.MOVING_AVERAGE, SPECS);

        assertThat(resolved.values()).containsExactly(Map.entry("variant", "SIMPLE"), Map.entry("window", "50"));
        assertThat(resolved.integer("window")).isEqualTo(50);
        assertThat(resolved.decimal("alpha")).isEmpty();
        assertThat(resolved.canonical()).isEqualTo("variant=SIMPLE;window=50");
    }

    @Test
    void resolutionIsIdempotent() {
        ModelParameters once = ModelParameters.of(Map.of("alpha", "0.50")).resolve(ForecastModelType.NAIVE, SPECS);

        assertThat(once.resolve(ForecastModelType.NAIVE, SPECS)).isEqualTo(once);
        assertThat(once.decimal("alpha")).hasValue(0.5);
    }

    @Test
    void rejectsUnknownMalformedAndOutOfRangeValues() {
        assertThatThrownBy(() -> ModelParameters.of(Map.of("lookback", "10")).resolve(ForecastModelType.NAIVE, SPECS))
                .isInstanceOf(InvalidForecastParameterException.class)
                .hasMessage("Parameter 'lookback' is not supported by the NAIVE model");
        assertThatThrownBy(() -> ModelParameters.of(Map.of("window", "abc")).resolve(ForecastModelType.NAIVE, SPECS))
                .hasMessage("Parameter 'window' must be a whole number");
        assertThatThrownBy(() -> ModelParameters.of(Map.of("window", "4")).resolve(ForecastModelType.NAIVE, SPECS))
                .hasMessage("Parameter 'window' must be between 5 and 200");
        assertThatThrownBy(() -> ModelParameters.of(Map.of("variant", "x")).resolve(ForecastModelType.NAIVE, SPECS))
                .hasMessage("Parameter 'variant' must be one of AUTO, SIMPLE");
        assertThatThrownBy(() -> ModelParameters.of(Map.of("alpha", "NaN")).resolve(ForecastModelType.NAIVE, SPECS))
                .hasMessage("Parameter 'alpha' must be a finite number");
    }

    @Test
    void invalidParameterErrorsCarryTheParameterName() {
        InvalidForecastParameterException error = new InvalidForecastParameterException("window", "is wrong");

        assertThat(error.code()).isEqualTo("INVALID_FORECAST_PARAMETER");
        assertThat(error.details()).containsEntry("parameter", "window");
    }
}
