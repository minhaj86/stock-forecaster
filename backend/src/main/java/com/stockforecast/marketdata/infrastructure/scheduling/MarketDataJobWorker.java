package com.stockforecast.marketdata.infrastructure.scheduling;

import com.stockforecast.marketdata.application.MarketDataSynchronizationService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Executes queued market-data jobs. The lock makes exactly one instance work the queue at a time (so the provider's
 * rate limit is never exceeded by several instances); each run stops after a time budget, releasing the lock.
 */
@Component
class MarketDataJobWorker {

    private static final Logger log = LoggerFactory.getLogger(MarketDataJobWorker.class);

    private final MarketDataSynchronizationService synchronization;

    MarketDataJobWorker(MarketDataSynchronizationService synchronization) {
        this.synchronization = synchronization;
    }

    @Scheduled(fixedDelayString = "${app.market-data.jobs.poll-interval:PT5S}",
            initialDelayString = "${app.market-data.jobs.poll-interval:PT5S}")
    @SchedulerLock(name = "market-data-job-worker", lockAtMostFor = "PT15M")
    public void processQueue() {
        int executed = synchronization.processQueue();
        if (executed > 0) {
            log.debug("Market-data job worker executed {} job(s)", executed);
        }
    }

    /** Daily housekeeping of the job history. */
    @Scheduled(cron = "${app.market-data.jobs.cleanup-cron:0 15 3 * * *}", zone = "UTC")
    @SchedulerLock(name = "market-data-job-cleanup", lockAtLeastFor = "PT1M")
    public void purgeFinishedJobs() {
        int deleted = synchronization.purgeFinishedJobs();
        if (deleted > 0) {
            log.info("Deleted {} finished market-data jobs past the retention period", deleted);
        }
    }
}
