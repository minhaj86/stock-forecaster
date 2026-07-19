package com.stockforecast.marketdata.application;

/**
 * A provider call failed. {@code retryable} distinguishes transient failures (timeouts, 5xx, rate limits) from
 * permanent ones (unknown symbol, invalid credentials).
 */
public class MarketDataProviderException extends RuntimeException {

    private final boolean retryable;

    public MarketDataProviderException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
