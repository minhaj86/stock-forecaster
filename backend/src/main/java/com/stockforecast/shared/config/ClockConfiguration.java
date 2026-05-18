package com.stockforecast.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * Single source of time. Inject {@link Clock} instead of calling {@code Instant.now()} so time-dependent logic
 * (token expiry, market sessions, analytics windows) is deterministic under test.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

    /**
     * Ticks in microseconds, the precision of PostgreSQL timestamps, so values returned right after a write are
     * identical to the values read back later.
     */
    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }
}
