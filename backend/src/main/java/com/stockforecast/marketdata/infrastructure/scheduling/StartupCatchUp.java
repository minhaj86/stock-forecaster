package com.stockforecast.marketdata.infrastructure.scheduling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Catches up after downtime without delaying startup. Goes through the job's proxy so the ShedLock lock applies.
 */
@Component
@ConditionalOnProperty(prefix = "app.market-data.sync", name = "on-startup", havingValue = "true", matchIfMissing = true)
class StartupCatchUp {

    private final MarketDataSyncJob job;

    StartupCatchUp(MarketDataSyncJob job) {
        this.job = job;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        job.syncWatchedStocks();
    }
}
