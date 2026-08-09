package com.stockforecast.forecasting.domain;

/**
 * How a forecast's uncertainty range was derived.
 *
 * @param origins       number of past forecast origins whose errors were measured
 * @param measuredSteps the furthest step with enough measured errors; later steps are extrapolated
 */
public record IntervalCalibration(Method method, int origins, int measuredSteps) {

    public enum Method {
        /** Every step's width comes from the model's own measured past errors. */
        EMPIRICAL,
        /** Measured up to {@code measuredSteps}, then widened with the square root of time. */
        EMPIRICAL_EXTRAPOLATED,
        /** Too little history to measure errors: recent volatility scaled with the square root of time. */
        VOLATILITY_SCALED
    }
}
