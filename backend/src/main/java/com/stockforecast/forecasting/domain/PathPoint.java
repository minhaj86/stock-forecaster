package com.stockforecast.forecasting.domain;

/**
 * Price forecast for one future trading day: central estimate and uncertainty range.
 *
 * @param step     1-based number of trading days after the forecast origin
 * @param expected central (median) estimate
 * @param lower    lower bound of the range at the requested confidence
 * @param upper    upper bound of the range at the requested confidence
 */
public record PathPoint(int step, double expected, double lower, double upper) {

    public PathPoint {
        if (step < 1) {
            throw new IllegalArgumentException("step must be at least 1");
        }
        if (!(lower > 0 && lower <= expected && expected <= upper) || Double.isInfinite(upper)) {
            throw new IllegalArgumentException("invalid forecast range at step " + step);
        }
    }
}
