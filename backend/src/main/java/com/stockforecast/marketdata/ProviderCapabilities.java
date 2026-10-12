package com.stockforecast.marketdata;

import java.util.Set;

/**
 * What a market-data provider adapter supports.
 *
 * @param maxBarsPerRequest largest number of bars one vendor request returns; longer ranges are split by the adapter
 * @param adjustment        human-readable description of how prices are adjusted for corporate actions
 */
public record ProviderCapabilities(Set<MarketDataInterval> supportedIntervals, boolean symbolSearch,
                                   boolean metadataLookup, boolean latestQuote, int maxBarsPerRequest,
                                   String adjustment) {

    public ProviderCapabilities {
        supportedIntervals = Set.copyOf(supportedIntervals);
    }

    public boolean supports(MarketDataInterval interval) {
        return supportedIntervals.contains(interval);
    }
}
