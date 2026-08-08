package com.stockforecast.forecasting.domain;

/**
 * What a forecast estimates. Price-path models support {@link #PRICE} and {@link #RETURN} (the cumulative return
 * implied by the price path, relative to the close at the forecast origin). {@link #DIRECTION} and
 * {@link #VOLATILITY} are reserved for models that estimate them natively.
 */
public enum ForecastTarget {
    PRICE,
    RETURN,
    DIRECTION,
    VOLATILITY;

    public boolean isDerivedFromPricePath() {
        return this == PRICE || this == RETURN;
    }

    /**
     * The value of this target implied by a price at a future step of a forecast made when the price was
     * {@code originPrice}.
     */
    public double fromPrice(double price, double originPrice) {
        return switch (this) {
            case PRICE -> price;
            case RETURN -> price / originPrice - 1;
            case DIRECTION, VOLATILITY -> throw new UnsupportedForecastTargetException(this);
        };
    }
}
