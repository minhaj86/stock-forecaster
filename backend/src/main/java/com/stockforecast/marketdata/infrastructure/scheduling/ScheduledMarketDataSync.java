package com.stockforecast.marketdata.infrastructure.scheduling;

import com.stockforecast.marketdata.MarketDataJobs.SyncSummary;
import com.stockforecast.marketdata.application.MarketDataJobService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily synchronisation of every watched stock: queues an incremental update per stock (only watched, active stocks
 * are ever downloaded automatically). The job worker executes them within the provider's rate limits.
 */
@Component
class ScheduledMarketDataSync {

    private static final Logger log = LoggerFactory.getLogger(ScheduledMarketDataSync.class);

    private final MarketDataJobService jobService;

    ScheduledMarketDataSync(MarketDataJobService jobService) {
        this.jobService = jobService;
    }

    @Scheduled(cron = "${app.market-data.sync.cron}", zone = "UTC")
    @SchedulerLock(name = "market-data-sync", lockAtLeastFor = "PT1M")
    public void queueWatchedStocks() {
        SyncSummary summary = jobService.queueScheduledSynchronisation();
        log.info("Scheduled market-data sync: {} jobs queued for {} watched stocks", summary.jobsQueued(),
                summary.stocksConsidered());
    }
}
