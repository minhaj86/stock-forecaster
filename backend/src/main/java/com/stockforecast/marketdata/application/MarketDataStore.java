package com.stockforecast.marketdata.application;

import com.stockforecast.marketdata.DailyBar;
import com.stockforecast.marketdata.MarketDataStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistence port for daily bars and synchronisation state. Bars are keyed by (stock, trading date).
 */
public interface MarketDataStore {

    /** Insert-or-update keyed by (stock, date). */
    void upsertBars(UUID stockId, String provider, List<DailyBar> bars, Instant ingestedAt);

    /** Inserts bars for dates that are not stored yet; existing bars stay untouched. */
    void insertMissingBars(UUID stockId, String provider, List<DailyBar> bars, Instant ingestedAt);

    Map<UUID, List<DailyBar>> findBars(Collection<UUID> stockIds, LocalDate fromInclusive, LocalDate toInclusive);

    long countBars(UUID stockId, LocalDate fromInclusive, LocalDate toInclusive);

    Map<UUID, MarketDataStatus> findStatuses(Collection<UUID> stockIds);

    /** First/last stored date and number of bars per stock (stocks without bars are absent). */
    Map<UUID, Coverage> findCoverage(Collection<UUID> stockIds);

    /** Marks a stock as tracked (status row without data = pending) unless it already is. */
    void ensureTracked(UUID stockId, Instant now);

    void recordAttempt(UUID stockId, Instant now);

    /**
     * Records a successful synchronisation: {@code last_success_at} is the data version that versions analytics and
     * forecast caches, and the newest stored bar is re-derived from the table (backfills of old ranges never move it
     * backwards).
     */
    void recordSuccess(UUID stockId, String provider, Instant now);

    void recordFailure(UUID stockId, String error, Instant now);

    TrackingStatistics trackingStatistics();

    // --- repopulation: stage, verify, swap -------------------------------------------------------------------

    void stageBars(UUID jobId, List<DailyBar> bars);

    StagedSummary summarizeStaged(UUID jobId);

    /**
     * Replaces the stored bars of the stock between both dates with the staged bars of the job, in the caller's
     * transaction: stored dates the provider no longer reports are deleted, changed bars updated, new ones inserted;
     * the staged rows are removed.
     */
    ReplaceResult replaceWithStaged(UUID stockId, UUID jobId, LocalDate fromInclusive, LocalDate toInclusive,
                                    String provider, Instant ingestedAt);

    void clearStaged(UUID jobId);

    record Coverage(LocalDate firstBarDate, LocalDate lastBarDate, long barCount) {
    }

    record TrackingStatistics(long trackedStocks, long stocksWithData, Instant lastSuccessAt) {
    }

    record StagedSummary(long count, LocalDate firstDate, LocalDate lastDate) {
    }

    record ReplaceResult(int inserted, int updated, int unchanged, int deleted) {
    }
}
