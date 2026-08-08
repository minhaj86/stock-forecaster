package com.stockforecast.forecasting.domain;

import java.util.Objects;

/**
 * What to forecast and how far ahead.
 *
 * @param horizon    number of future trading days (steps) to forecast
 * @param confidence nominal coverage of the uncertainty range, e.g. 0.8 for an 80% range
 * @param parameters model-specific settings; models validate them and fill in defaults
 */
public record ForecastRequest(ForecastTarget target, int horizon, double confidence, ModelParameters parameters) {

    /** Hard upper bound (one trading year); deployments configure a lower operational limit. */
    public static final int MAX_HORIZON = 252;

    public ForecastRequest {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(parameters, "parameters");
        if (horizon < 1 || horizon > MAX_HORIZON) {
            throw new InvalidForecastHorizonException(horizon, MAX_HORIZON);
        }
        if (!(confidence > 0.5 && confidence < 1)) {
            throw new IllegalArgumentException("confidence must be between 0.5 and 1 (exclusive)");
        }
    }

    public ForecastRequest withParameters(ModelParameters resolved) {
        return new ForecastRequest(target, horizon, confidence, resolved);
    }
}
