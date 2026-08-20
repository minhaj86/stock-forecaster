package com.stockforecast.forecasting.application;

import com.stockforecast.forecasting.domain.ForecastModelType;
import com.stockforecast.forecasting.domain.ForecastTarget;

import java.util.Map;

/**
 * A client's forecast request; {@code null} fields take configured defaults.
 *
 * @param parameters model-specific settings as raw strings, validated by the model
 */
public record ForecastQuery(ForecastModelType model, ForecastTarget target, Integer horizon,
                            Map<String, String> parameters) {

    public ForecastQuery {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
