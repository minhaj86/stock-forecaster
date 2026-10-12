package com.stockforecast.marketdata;

import java.time.Instant;
import java.util.Map;

/**
 * Result of a provider health check.
 *
 * @param details client-safe facts (plan usage, limits); never credentials
 */
public record ProviderHealth(String provider, Status status, Instant checkedAt, Long latencyMs, String message,
                             Map<String, Object> details) {

    public ProviderHealth {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static ProviderHealth unknown(String provider) {
        return new ProviderHealth(provider, Status.UNKNOWN, null, null, "Not checked yet", Map.of());
    }

    public enum Status {
        HEALTHY,
        /** Reachable, but rate limited, close to its quota or recently failing. */
        DEGRADED,
        UNAVAILABLE,
        /** Required configuration (such as the API key) is missing or rejected. */
        MISCONFIGURED,
        UNKNOWN
    }
}
