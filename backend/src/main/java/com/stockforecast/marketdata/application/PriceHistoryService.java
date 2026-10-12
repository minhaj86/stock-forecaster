package com.stockforecast.marketdata.application;

import com.stockforecast.marketdata.DailyBar;
import com.stockforecast.marketdata.MarketDataStatus;
import com.stockforecast.marketdata.PriceHistory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
class PriceHistoryService implements PriceHistory {

    private static final LocalDate FAR_FUTURE = LocalDate.of(9999, 12, 31);

    private final MarketDataStore store;
    private final MarketDataProvider provider;

    PriceHistoryService(MarketDataStore store, MarketDataProvider provider) {
        this.store = store;
        this.provider = provider;
    }

    @Override
    public Map<UUID, List<DailyBar>> findDailyBars(Collection<UUID> stockIds, LocalDate fromInclusive) {
        return stockIds.isEmpty() ? Map.of() : store.findBars(stockIds, fromInclusive, FAR_FUTURE);
    }

    @Override
    public List<DailyBar> findDailyBars(UUID stockId, LocalDate fromInclusive, LocalDate toInclusive) {
        return store.findBars(List.of(stockId), fromInclusive, toInclusive).getOrDefault(stockId, List.of());
    }

    @Override
    public Map<UUID, MarketDataStatus> findStatuses(Collection<UUID> stockIds) {
        Map<UUID, MarketDataStatus> statuses = new HashMap<>(store.findStatuses(stockIds));
        stockIds.forEach(id -> statuses.putIfAbsent(id, MarketDataStatus.notTracked(id)));
        return statuses;
    }

    @Override
    public ProviderInfo activeProvider() {
        return provider.info();
    }
}
