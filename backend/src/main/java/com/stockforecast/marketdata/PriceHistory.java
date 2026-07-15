package com.stockforecast.marketdata;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read access to stored daily bars (oldest first). Never calls a provider.
 */
public interface PriceHistory {

    Map<UUID, List<DailyBar>> findDailyBars(Collection<UUID> stockIds, LocalDate fromInclusive);

    List<DailyBar> findDailyBars(UUID stockId, LocalDate fromInclusive, LocalDate toInclusive);

    Map<UUID, MarketDataStatus> findStatuses(Collection<UUID> stockIds);

    ProviderInfo activeProvider();

    /**
     * @param synthetic {@code true} when prices are simulated and must be labelled as such in every UI
     */
    record ProviderInfo(String id, String name, boolean synthetic, String description) {
    }
}
