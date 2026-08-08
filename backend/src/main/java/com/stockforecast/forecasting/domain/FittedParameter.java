package com.stockforecast.forecasting.domain;

import java.util.Objects;

/**
 * A value estimated while fitting a model (e.g. slope, R², smoothing weight), reported for transparency.
 *
 * @param format how clients should display {@code value}
 */
public record FittedParameter(String key, String label, double value, Format format) {

    public enum Format {
        /** Plain number. */
        NUMBER,
        /** Decimal fraction shown as a percentage (0.052 = 5.2%). */
        PERCENT,
        /** Amount in the stock's currency. */
        PRICE
    }

    public FittedParameter {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(format, "format");
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("fitted parameter '" + key + "' is not finite");
        }
    }
}
