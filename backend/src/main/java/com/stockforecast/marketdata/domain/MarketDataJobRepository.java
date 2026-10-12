package com.stockforecast.marketdata.domain;

import com.stockforecast.marketdata.MarketDataJobStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link MarketDataJob}.
 */
public interface MarketDataJobRepository {

    MarketDataJob save(MarketDataJob job);

    Optional<MarketDataJob> findById(UUID id);

    List<MarketDataJob> findAllByIdIn(Collection<UUID> ids);

    List<MarketDataJob> findByStockIdInAndStatusIn(Collection<UUID> stockIds, Collection<MarketDataJobStatus> statuses);

    /**
     * Locks and returns the next runnable job ({@code SELECT … FOR UPDATE SKIP LOCKED}): queued, due, and for a stock
     * that has no running job. Administrator and watchlist requests go before scheduled ones, then oldest first.
     * Must run inside a transaction that also starts the job.
     */
    Optional<MarketDataJob> claimNextRunnable(Instant now);

    List<MarketDataJob> findByStatusAndHeartbeatAtBefore(MarketDataJobStatus status, Instant staleBefore);

    long countByStatus(MarketDataJobStatus status);

    long countByStatusInAndCompletedAtGreaterThanEqual(Collection<MarketDataJobStatus> statuses, Instant since);

    Optional<MarketDataJob> findFirstByStatusOrderByCompletedAtDesc(MarketDataJobStatus status);

    /** Housekeeping: removes finished jobs completed before the given instant. */
    int deleteFinishedBefore(Instant before);
}
