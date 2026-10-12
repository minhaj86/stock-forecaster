package com.stockforecast.marketdata.infrastructure.health;

import com.stockforecast.marketdata.ProviderHealth;
import com.stockforecast.marketdata.application.MarketDataProviderHealthService;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.stereotype.Component;

/**
 * Actuator view of the market-data provider ({@code /actuator/health/marketDataProvider}). Passive: it reports the
 * last checked state and never calls the provider, so probes cannot use up the vendor's quota. A provider outage is
 * reported as {@code DEGRADED} (HTTP 200): stored data keeps being served, so the application stays available and
 * readiness is unaffected.
 */
@Component("marketDataProvider")
class MarketDataProviderHealthIndicator implements HealthIndicator {

    static final Status DEGRADED = new Status("DEGRADED", "Market-data provider is degraded or unavailable");

    private final MarketDataProviderHealthService healthService;

    MarketDataProviderHealthIndicator(MarketDataProviderHealthService healthService) {
        this.healthService = healthService;
    }

    @Override
    public Health health() {
        ProviderHealth health = healthService.lastKnown();
        Health.Builder builder = switch (health.status()) {
            case HEALTHY, UNKNOWN -> Health.up();
            case DEGRADED, UNAVAILABLE, MISCONFIGURED -> Health.status(DEGRADED);
        };
        builder.withDetail("provider", health.provider()).withDetail("status", health.status().name());
        if (health.checkedAt() != null) {
            builder.withDetail("checkedAt", health.checkedAt().toString());
        }
        if (health.message() != null) {
            builder.withDetail("message", health.message());
        }
        return builder.build();
    }
}
