package com.stockforecast.marketdata.application;

import com.stockforecast.marketdata.MarketDataProviders.ProviderUsage;
import com.stockforecast.marketdata.ProviderHealth;
import com.stockforecast.marketdata.ProviderHealth.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Provider health: an active check (one cheap vendor request) whose result is cached, so neither the admin console nor
 * monitoring can exhaust the provider's quota. Between active checks the health is derived passively from the outcome
 * of the most recent real provider request, which costs nothing. Missing configuration is reported without calling
 * the provider.
 */
@Service
public class MarketDataProviderHealthService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataProviderHealthService.class);

    private final MarketDataProvider provider;
    private final ProviderUsageRecorder usage;
    private final ProviderThrottle throttle;
    private final MarketDataProperties properties;
    private final Clock clock;
    private final AtomicReference<ProviderHealth> lastResult = new AtomicReference<>();

    public MarketDataProviderHealthService(MarketDataProvider provider, ProviderUsageRecorder usage,
                                           ProviderThrottle throttle, MarketDataProperties properties, Clock clock) {
        this.provider = provider;
        this.usage = usage;
        this.throttle = throttle;
        this.properties = properties;
        this.clock = clock;
    }

    /** Cached result if it is recent enough, otherwise a new active check. */
    public ProviderHealth check() {
        return check(false);
    }

    /**
     * @param force check now unless the last check is younger than the minimum refresh interval
     */
    public ProviderHealth check(boolean force) {
        ProviderHealth cached = lastResult.get();
        Instant now = clock.instant();
        if (cached != null && cached.checkedAt() != null) {
            Duration maxAge = force ? properties.health().minRefreshInterval() : properties.health().cacheTtl();
            if (Duration.between(cached.checkedAt(), now).compareTo(maxAge) < 0) {
                return cached;
            }
        }
        ProviderHealth result = probe(now);
        lastResult.set(result);
        if (result.status() != Status.HEALTHY) {
            log.warn("Market-data provider {} is {}: {}", result.provider(), result.status(), result.message());
        }
        return result;
    }

    /**
     * Never calls the provider: the most recent evidence of either the last active check or the last real request.
     */
    public ProviderHealth lastKnown() {
        if (!provider.isConfigured()) {
            return misconfigured(clock.instant());
        }
        ProviderHealth cached = lastResult.get();
        ProviderHealth passive = fromRecentRequests();
        if (cached == null) {
            return passive;
        }
        boolean passiveIsNewer = passive.checkedAt() != null && cached.checkedAt() != null
                && passive.checkedAt().isAfter(cached.checkedAt());
        return passiveIsNewer ? passive : cached;
    }

    private ProviderHealth fromRecentRequests() {
        String providerId = provider.info().id();
        ProviderUsage recent = usage.today(providerId, throttle.status());
        Instant lastSuccess = recent.lastSuccessAt();
        Instant lastFailure = recent.lastFailureAt();
        if (lastSuccess == null && lastFailure == null) {
            return ProviderHealth.unknown(providerId);
        }
        if (lastFailure == null || (lastSuccess != null && lastSuccess.isAfter(lastFailure))) {
            return new ProviderHealth(providerId, Status.HEALTHY, lastSuccess, null,
                    "The most recent provider request succeeded", Map.of("source", "recent requests"));
        }
        return new ProviderHealth(providerId, Status.DEGRADED, lastFailure, null,
                "The most recent provider request failed: " + recent.lastFailureMessage(),
                Map.of("source", "recent requests"));
    }

    private ProviderHealth probe(Instant now) {
        String providerId = provider.info().id();
        if (!provider.isConfigured()) {
            return misconfigured(now);
        }
        long started = System.nanoTime();
        try {
            return provider.checkHealth();
        } catch (MarketDataProviderException e) {
            long latency = (System.nanoTime() - started) / 1_000_000;
            Status status = switch (e.code()) {
                case AUTHENTICATION_FAILED, NOT_CONFIGURED, PLAN_RESTRICTED -> Status.MISCONFIGURED;
                case RATE_LIMITED -> Status.DEGRADED;
                default -> Status.UNAVAILABLE;
            };
            return new ProviderHealth(providerId, status, now, latency, e.getMessage(),
                    Map.of("errorCode", e.code().jobErrorCode()));
        }
    }

    private ProviderHealth misconfigured(Instant now) {
        return new ProviderHealth(provider.info().id(), Status.MISCONFIGURED, now, null,
                provider.info().name() + " is not configured: the API key is missing", Map.of());
    }
}
