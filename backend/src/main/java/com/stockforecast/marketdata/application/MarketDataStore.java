package com.stockforecast.marketdata.application;

import com.stockforecast.marketdata.DailyBar;
import com.stockforecast.marketdata.MarketDataStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for bars and synchronisation state.
 */
public interface MarketDataStore {

    /** Idempotent insert-or-update keyed by (stock, date). */
    void upsertBars(UUID stockId, String provider, List<DailyBar> bars, Instant ingestedAt);

    Map<UUID, List<DailyBar>> findBars(Collection<UUID> stockIds, LocalDate fromInclusive, LocalDate toInclusive);

    Map<UUID, MarketDataStatus> findStatuses(Collection<UUID> stockIds);

    /** Marks a stock as tracked (status row without data = pending) unless it already is. */
    void ensureTracked(UUID stockId, Instant now);

    /**
     * Atomically takes the synchronisation lease for a stock unless another worker holds a fresh lease or the stock
     * is backing off after failures.
     *
     * @return the claim (with the last stored bar date) or empty if the stock must not be synchronised now
     */
    Optional<SyncClaim> tryClaim(UUID stockId, Instant now, Instant staleLeaseBefore, boolean ignoreBackoff);

    void recordSuccess(UUID stockId, String provider, LocalDate lastBarDate, Instant now);

    void recordFailure(UUID stockId, String error, Instant now, Instant nextAttemptAt);

    record SyncClaim(UUID stockId, LocalDate lastBarDate, int consecutiveFailures) {
    }
}
