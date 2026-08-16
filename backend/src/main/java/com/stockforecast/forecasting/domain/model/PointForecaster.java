package com.stockforecast.forecasting.domain.model;

/**
 * A model fitted to one training series that can produce central price forecasts from origins inside that series.
 * A forecast from {@code origin} may only use observations {@code 0..origin}; this is what lets the interval
 * calibration measure genuine out-of-sample errors.
 */
public interface PointForecaster {

    /** The earliest origin (index into the training series) from which a forecast can be made. */
    int firstOrigin();

    /** Central price estimates for steps {@code 1..steps} after {@code origin}. */
    double[] forecastFrom(int origin, int steps);
}
