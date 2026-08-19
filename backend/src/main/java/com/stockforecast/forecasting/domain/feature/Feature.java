package com.stockforecast.forecasting.domain.feature;

/**
 * Model inputs derived from prices and volume. Every feature at day {@code t} uses observations up to {@code t}
 * only; definitions come from {@link com.stockforecast.quant.Indicators}.
 */
public enum Feature {
    DAILY_RETURN,
    LOG_RETURN,
    SMA_5,
    SMA_10,
    SMA_20,
    SMA_50,
    SMA_200,
    EMA_12,
    EMA_26,
    RSI_14,
    /** 20-day rate of change. */
    MOMENTUM_20,
    /** Annualised volatility of the last 20 daily log returns. */
    VOLATILITY_20,
    /** Volume relative to the previous day, minus one. */
    VOLUME_CHANGE,
    /** Close divided by its 20-day SMA. */
    PRICE_TO_SMA_20,
    /** 20-day SMA divided by the 50-day SMA (above 1 in an up-trend). */
    SMA_20_TO_SMA_50,
    /** Highest close of the last 20 days. */
    ROLLING_HIGH_20,
    /** Lowest close of the last 20 days. */
    ROLLING_LOW_20
}
