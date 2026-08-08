package com.stockforecast.forecasting.domain;

/**
 * Every forecasting model the platform knows about. Availability is decided by the registered providers: a type
 * without an implementation is reported as planned and rejected when requested, never answered with a stand-in.
 */
public enum ForecastModelType {
    NAIVE,
    MOVING_AVERAGE,
    LINEAR_REGRESSION,
    EXPONENTIAL_SMOOTHING,
    MONTE_CARLO,
    ARIMA
}
