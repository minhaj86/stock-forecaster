package com.stockforecast.marketdata.application;

import com.stockforecast.marketdata.MarketDataProviders.RateLimitStatus;

import java.time.Duration;

/**
 * Client-side rate limiter for one provider (token bucket sized from the vendor plan). Adapters acquire permits
 * before every vendor request, so the application never exceeds the plan, no matter how many jobs are queued.
 */
public interface ProviderThrottle {

    /**
     * Waits up to {@code maxWait} for {@code permits} requests.
     *
     * @throws MarketDataProviderException {@link ProviderErrorCode#RATE_LIMITED} with the expected wait if the permits
     *                                     are not available in time
     */
    void acquire(int permits, Duration maxWait);

    /** The provider answered 429: stop issuing requests for the given time. */
    void pauseFor(Duration duration);

    RateLimitStatus status();

    /** No limits (synthetic provider). */
    ProviderThrottle UNLIMITED = new ProviderThrottle() {
        @Override
        public void acquire(int permits, Duration maxWait) {
            // nothing to wait for
        }

        @Override
        public void pauseFor(Duration duration) {
            // nothing to pause
        }

        @Override
        public RateLimitStatus status() {
            return new RateLimitStatus(null, null, null);
        }
    };
}
