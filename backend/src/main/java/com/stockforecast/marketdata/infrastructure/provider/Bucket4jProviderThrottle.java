package com.stockforecast.marketdata.infrastructure.provider;

import com.stockforecast.marketdata.MarketDataProviders.RateLimitStatus;
import com.stockforecast.marketdata.application.MarketDataProviderException;
import com.stockforecast.marketdata.application.ProviderErrorCode;
import com.stockforecast.marketdata.application.ProviderThrottle;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.EstimationProbe;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Token bucket sized from the vendor plan (requests per minute, refilled continuously). Callers wait for permits up to
 * a maximum; beyond that the call fails fast as rate limited, so jobs are re-queued instead of blocking a worker.
 * A 429 from the vendor puts the bucket into debt for the requested back-off.
 *
 * <p>The bucket is local to the instance; job execution is serialised cluster-wide, so jobs never exceed the plan.
 */
public class Bucket4jProviderThrottle implements ProviderThrottle {

    private final String provider;
    private final int requestsPerMinute;
    private final Bucket bucket;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final AtomicReference<Instant> pausedUntil = new AtomicReference<>();

    public Bucket4jProviderThrottle(String provider, int requestsPerMinute, Clock clock, MeterRegistry meterRegistry) {
        if (requestsPerMinute < 1) {
            throw new IllegalArgumentException("requestsPerMinute must be positive");
        }
        this.provider = provider;
        this.requestsPerMinute = requestsPerMinute;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.bucket = Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(requestsPerMinute)
                        .refillGreedy(requestsPerMinute, Duration.ofMinutes(1)).build())
                .build();
    }

    @Override
    public void acquire(int permits, Duration maxWait) {
        try {
            boolean acquired = maxWait == null || maxWait.isZero() || maxWait.isNegative()
                    ? bucket.tryConsume(permits)
                    : bucket.asBlocking().tryConsume(permits, maxWait);
            if (acquired) {
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw rateLimited(Duration.ofSeconds(1));
        }
        EstimationProbe estimate = bucket.estimateAbilityToConsume(permits);
        meterRegistry.counter("market_data_provider_rate_limit", "provider", provider, "source", "client").increment();
        throw rateLimited(Duration.ofNanos(Math.max(estimate.getNanosToWaitForRefill(), 1_000_000_000L)));
    }

    @Override
    public void pauseFor(Duration duration) {
        long available = Math.max(0, bucket.getAvailableTokens());
        long debt = (long) Math.ceil(duration.toMillis() / 60_000.0 * requestsPerMinute);
        bucket.consumeIgnoringRateLimits(available + Math.max(1, debt));
        pausedUntil.set(clock.instant().plus(duration));
    }

    @Override
    public RateLimitStatus status() {
        Instant until = pausedUntil.get();
        Instant now = clock.instant();
        return new RateLimitStatus(requestsPerMinute, Math.max(0, bucket.getAvailableTokens()),
                until != null && until.isAfter(now) ? until : null);
    }

    private MarketDataProviderException rateLimited(Duration retryAfter) {
        return new MarketDataProviderException(ProviderErrorCode.RATE_LIMITED,
                "Client-side rate limit for %s reached (%d requests per minute)".formatted(provider, requestsPerMinute),
                retryAfter, null);
    }
}
