package com.stockforecast.forecasting.domain;

import com.stockforecast.shared.domain.DomainException;
import com.stockforecast.shared.domain.ErrorCategory;

import java.util.Map;

public class UnsupportedForecastModelException extends DomainException {

    public UnsupportedForecastModelException(ForecastModelType model) {
        super("UNSUPPORTED_FORECAST_MODEL", ErrorCategory.VALIDATION,
                "The %s forecasting model is not available yet".formatted(model.name()),
                Map.of("model", model.name()));
    }
}
