package com.stockforecast.marketdata;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * What market data is stored, per stock and overall (admin console). Derived from the stored bars and the
 * synchronisation state; nothing is duplicated.
 */
public interface MarketDataInventory {

    /** Summaries for every given stock (stocks without any market-data state are reported as not tracked). */
    Map<UUID, StockDataSummary> summaries(Collection<UUID> stockIds);

    InventoryStatistics statistics();

    record StockDataSummary(UUID stockId, MarketDataStatus.State dataStatus, String provider, LocalDate firstBarDate,
                            LocalDate lastBarDate, long barCount, Instant lastSuccessAt, Instant lastAttemptAt,
                            String lastError, ActiveJob activeJob) {
    }

    record ActiveJob(UUID id, MarketDataJobType type, MarketDataJobStatus status, int progress) {
    }

    /**
     * @param trackedStocks  stocks whose market data has been requested (watched or loaded by an administrator)
     * @param stocksWithData tracked stocks with at least one stored bar
     */
    record InventoryStatistics(long trackedStocks, long stocksWithData, Instant lastSuccessfulSyncAt) {
    }
}
