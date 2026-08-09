package com.stockforecast.forecasting.domain;

import com.stockforecast.shared.domain.DomainException;
import com.stockforecast.shared.domain.ErrorCategory;

import java.util.Map;

public class InvalidForecastHorizonException extends DomainException {

    public InvalidForecastHorizonException(int horizon, int maximum) {
        super("INVALID_FORECAST_HORIZON", ErrorCategory.VALIDATION,
                "The horizon must be between 1 and %d trading days, but was %d".formatted(maximum, horizon),
                Map.of("minimum", 1, "maximum", maximum));
    }
}
