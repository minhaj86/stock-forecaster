package com.stockforecast.marketdata.infrastructure.provider;

import com.stockforecast.marketdata.application.MarketDataProviderException;
import com.stockforecast.marketdata.application.ProviderErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Bucket4jProviderThrottleTest {

    private static final Instant NOW = Instant.parse("2026-10-11T22:00:00Z");

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final Bucket4jProviderThrottle throttle = new Bucket4jProviderThrottle("fake", 2,
            Clock.fixed(NOW, ZoneOffset.UTC), meterRegistry);

    @Test
    void allowsThePlanRateAndThenFailsFastAsRateLimited() {
        throttle.acquire(1, Duration.ZERO);
        throttle.acquire(1, Duration.ZERO);
        assertThat(throttle.status().availablePermits()).isZero();

        assertThatThrownBy(() -> throttle.acquire(1, Duration.ofMillis(10)))
                .isInstanceOfSatisfying(MarketDataProviderException.class, error -> {
                    assertThat(error.code()).isEqualTo(ProviderErrorCode.RATE_LIMITED);
                    assertThat(error.retryAfter()).hasValueSatisfying(wait ->
                            assertThat(wait).isBetween(Duration.ofSeconds(1), Duration.ofSeconds(31)));
                });
        assertThat(meterRegistry.counter("market_data_provider_rate_limit", "provider", "fake", "source", "client")
                .count()).isEqualTo(1.0);
    }

    @Test
    void aProviderBackOffDrainsTheBucket() {
        throttle.pauseFor(Duration.ofSeconds(60));

        assertThat(throttle.status().throttledUntil()).isEqualTo(NOW.plusSeconds(60));
        assertThat(throttle.status().requestsPerMinute()).isEqualTo(2);
        assertThatThrownBy(() -> throttle.acquire(1, Duration.ZERO)).isInstanceOf(MarketDataProviderException.class);
    }
}
