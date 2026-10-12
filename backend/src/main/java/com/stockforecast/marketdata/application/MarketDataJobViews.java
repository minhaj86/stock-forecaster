package com.stockforecast.marketdata.application;

import com.stockforecast.marketdata.MarketDataJobView;
import com.stockforecast.marketdata.domain.JobCounters;
import com.stockforecast.marketdata.domain.MarketDataJob;
import com.stockforecast.stock.StockCatalog;
import com.stockforecast.stock.StockRef;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Maps jobs to read models, resolving stock symbols in one catalogue lookup.
 */
@Component
class MarketDataJobViews {

    private final StockCatalog stockCatalog;

    MarketDataJobViews(StockCatalog stockCatalog) {
        this.stockCatalog = stockCatalog;
    }

    List<MarketDataJobView> toViews(Collection<MarketDataJob> jobs) {
        Map<UUID, StockRef> stocks = stockCatalog.findAllByIds(jobs.stream().map(MarketDataJob::getStockId)
                .distinct().toList()).stream().collect(Collectors.toMap(StockRef::id, Function.identity()));
        return jobs.stream().map(job -> toView(job, stocks.get(job.getStockId()))).toList();
    }

    MarketDataJobView toView(MarketDataJob job) {
        return toViews(List.of(job)).getFirst();
    }

    static MarketDataJobView toView(MarketDataJob job, StockRef stock) {
        JobCounters counters = job.getCounters();
        return new MarketDataJobView(job.getId(), job.getType(), job.getStatus(), job.getTrigger(), job.getStockId(),
                stock == null ? null : stock.symbol(), stock == null ? null : stock.companyName(), job.getProvider(),
                job.getInterval(), job.getExistingDataStrategy(), job.getFromDate(), job.getToDate(),
                job.getRequestedBy(), job.getRequestedAt(), job.getStartedAt(), job.getCompletedAt(),
                job.durationMillis(), job.getProgress(), job.getStatusMessage(), job.getAttempts(),
                job.getMaxAttempts(), job.getNextAttemptAt(), counters.fetched(), counters.inserted(),
                counters.updated(), counters.skipped(), counters.failed(), counters.deleted(), job.getErrorCode(),
                job.getErrorMessage(), job.canBeRetried(), job.getRetryOfJobId(),
                job.getBatchId());
    }
}
