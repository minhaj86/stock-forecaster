package com.stockforecast.forecasting.domain;

import com.stockforecast.shared.domain.DomainException;
import com.stockforecast.shared.domain.ErrorCategory;

import java.util.Map;

public class InvalidForecastParameterException extends DomainException {

    public InvalidForecastParameterException(String parameter, String problem) {
        super("INVALID_FORECAST_PARAMETER", ErrorCategory.VALIDATION,
                "Parameter '%s' %s".formatted(parameter, problem), Map.of("parameter", parameter));
    }
}
