package com.stockforecast.marketdata;

import com.stockforecast.marketdata.PriceHistory.ProviderInfo;

import java.time.Instant;
import java.util.List;

/**
 * Provider administration: configuration, capabilities, usage, health and the synchronous lookups the admin console
 * needs to register stocks. These are the only provider calls made while serving a request; they are restricted to
 * administrators, rate limited and time-limited. Credentials are never exposed.
 */
public interface MarketDataProviders {

    ProviderOverview overview();

    /**
     * @param refresh run an active check now instead of returning the cached result (throttled)
     */
    ProviderHealth health(boolean refresh);

    /** The last known health without calling the provider. */
    ProviderHealth lastKnownHealth();

    ProviderSearch search(String query);

    /**
     * @param mic market identifier code disambiguating listings, or {@code null}
     */
    ProviderStockMetadata metadata(String symbol, String mic);

    /**
     * @param apiKeyRequired   {@code false} for providers that need no credentials (synthetic demo data)
     * @param apiKeyConfigured an API key is present (never the key itself)
     */
    record ProviderOverview(ProviderInfo info, boolean apiKeyRequired, boolean apiKeyConfigured,
                            ProviderCapabilities capabilities, ProviderHealth health, ProviderUsage usage,
                            List<ProviderDescriptor> available, String configurationHint) {
    }

    /**
     * Requests made today (UTC) by this application, counted per vendor request across all instances.
     */
    record ProviderUsage(long requestsToday, long failuresToday, long rateLimitedToday, Instant lastSuccessAt,
                         Instant lastFailureAt, String lastFailureMessage, RateLimitStatus rateLimit) {
    }

    /**
     * @param requestsPerMinute configured limit, {@code null} when the provider is not rate limited
     * @param availablePermits  requests that can be made right now on this instance, {@code null} when unlimited
     * @param throttledUntil    set while the provider asked us to back off
     */
    record RateLimitStatus(Integer requestsPerMinute, Long availablePermits, Instant throttledUntil) {
    }

    record ProviderDescriptor(String id, String name, boolean active) {
    }

    /**
     * @param supported {@code false} if the provider cannot search (e.g. the synthetic demo provider)
     */
    record ProviderSearch(boolean supported, List<ProviderInstrumentMatch> results) {
    }
}
