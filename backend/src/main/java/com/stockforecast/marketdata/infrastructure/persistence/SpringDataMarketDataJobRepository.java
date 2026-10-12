package com.stockforecast.marketdata.infrastructure.persistence;

import com.stockforecast.marketdata.domain.MarketDataJob;
import com.stockforecast.marketdata.domain.MarketDataJobRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface SpringDataMarketDataJobRepository extends Repository<MarketDataJob, UUID>, MarketDataJobRepository {

    @Override
    @Query(value = """
            SELECT j.* FROM market_data_sync_jobs j
             WHERE j.status = 'QUEUED'
               AND (j.next_attempt_at IS NULL OR j.next_attempt_at <= :now)
               AND NOT EXISTS (SELECT 1 FROM market_data_sync_jobs r
                                WHERE r.stock_id = j.stock_id AND r.status = 'RUNNING')
             ORDER BY CASE j.trigger_source WHEN 'ADMIN' THEN 0 WHEN 'WATCHLIST' THEN 1 ELSE 2 END, j.requested_at
             LIMIT 1
             FOR UPDATE SKIP LOCKED""", nativeQuery = true)
    Optional<MarketDataJob> claimNextRunnable(Instant now);

    @Override
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from MarketDataJob j
             where j.status in (com.stockforecast.marketdata.MarketDataJobStatus.COMPLETED,
                                com.stockforecast.marketdata.MarketDataJobStatus.PARTIALLY_COMPLETED,
                                com.stockforecast.marketdata.MarketDataJobStatus.FAILED,
                                com.stockforecast.marketdata.MarketDataJobStatus.CANCELLED)
               and j.completedAt < :before""")
    int deleteFinishedBefore(Instant before);
}
