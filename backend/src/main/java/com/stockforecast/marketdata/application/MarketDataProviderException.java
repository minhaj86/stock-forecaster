package com.stockforecast.marketdata.application;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * A provider call failed. The message is client-safe (no URLs, keys or stack traces): it is stored on jobs and
 * shown to administrators.
 */
public class MarketDataProviderException extends RuntimeException {

    private final ProviderErrorCode code;
    private final Duration retryAfter;

    public MarketDataProviderException(ProviderErrorCode code, String message, Duration retryAfter, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
        this.retryAfter = retryAfter;
    }

    public MarketDataProviderException(ProviderErrorCode code, String message) {
        this(code, message, null, null);
    }

    public ProviderErrorCode code() {
        return code;
    }

    public boolean isRetryable() {
        return code.isTransient();
    }

    /** How long the provider asked us to wait (rate limits), if it said so. */
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
