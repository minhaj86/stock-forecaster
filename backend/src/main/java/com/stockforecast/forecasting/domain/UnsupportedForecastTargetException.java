package com.stockforecast.forecasting.domain;

import com.stockforecast.shared.domain.DomainException;
import com.stockforecast.shared.domain.ErrorCategory;

import java.util.Map;

public class UnsupportedForecastTargetException extends DomainException {

    public UnsupportedForecastTargetException(ForecastTarget target) {
        super("UNSUPPORTED_FORECAST_TARGET", ErrorCategory.VALIDATION,
                "Forecasting the %s target is not supported yet; use PRICE or RETURN".formatted(target.name()),
                Map.of("target", target.name()));
    }
}
