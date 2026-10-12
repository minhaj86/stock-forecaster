package com.stockforecast.marketdata.application;

import com.stockforecast.marketdata.MarketDataInventory;
import com.stockforecast.marketdata.MarketDataJobStatus;
import com.stockforecast.marketdata.MarketDataStatus;
import com.stockforecast.marketdata.application.MarketDataStore.Coverage;
import com.stockforecast.marketdata.application.MarketDataStore.TrackingStatistics;
import com.stockforecast.marketdata.domain.MarketDataJob;
import com.stockforecast.marketdata.domain.MarketDataJobRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Stored-data inventory for the admin console, derived from the bars, the synchronisation state and active jobs.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class MarketDataInventoryService implements MarketDataInventory {

    private final MarketDataStore store;
    private final MarketDataJobRepository jobs;

    public MarketDataInventoryService(MarketDataStore store, MarketDataJobRepository jobs) {
        this.store = store;
        this.jobs = jobs;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, StockDataSummary> summaries(Collection<UUID> stockIds) {
        if (stockIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, MarketDataStatus> statuses = store.findStatuses(stockIds);
        Map<UUID, Coverage> coverage = store.findCoverage(stockIds);
        // Running jobs are more interesting than queued ones; among equals the oldest request runs first.
        Map<UUID, MarketDataJob> activeJobs = jobs.findByStockIdInAndStatusIn(stockIds, MarketDataJobService.ACTIVE)
                .stream()
                .collect(Collectors.toMap(MarketDataJob::getStockId, job -> job, (first, second) ->
                        List.of(first, second).stream().min(Comparator
                                .comparing((MarketDataJob job) -> job.getStatus() != MarketDataJobStatus.RUNNING)
                                .thenComparing(MarketDataJob::getRequestedAt)).orElseThrow()));
        Map<UUID, StockDataSummary> summaries = new HashMap<>();
        for (UUID stockId : stockIds) {
            MarketDataStatus status = statuses.getOrDefault(stockId, MarketDataStatus.notTracked(stockId));
            Coverage covered = coverage.get(stockId);
            MarketDataJob job = activeJobs.get(stockId);
            summaries.put(stockId, new StockDataSummary(stockId, status.state(), status.provider(),
                    covered == null ? null : covered.firstBarDate(), covered == null ? null : covered.lastBarDate(),
                    covered == null ? 0 : covered.barCount(), status.lastSuccessAt(), status.lastAttemptAt(),
                    status.lastError(), job == null ? null
                    : new ActiveJob(job.getId(), job.getType(), job.getStatus(), job.getProgress())));
        }
        return summaries;
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryStatistics statistics() {
        TrackingStatistics statistics = store.trackingStatistics();
        return new InventoryStatistics(statistics.trackedStocks(), statistics.stocksWithData(),
                statistics.lastSuccessAt());
    }
}
