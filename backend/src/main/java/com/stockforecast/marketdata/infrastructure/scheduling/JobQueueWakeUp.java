package com.stockforecast.marketdata.infrastructure.scheduling;

import com.stockforecast.marketdata.application.MarketDataJobQueued;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Starts the worker right after a job was queued (and committed) instead of waiting for the next poll. Goes through
 * the worker's proxy, so the cluster-wide lock applies; if a run is already in progress it picks the job up.
 */
@Component
class JobQueueWakeUp {

    private final MarketDataJobWorker worker;

    JobQueueWakeUp(MarketDataJobWorker worker) {
        this.worker = worker;
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void on(MarketDataJobQueued event) {
        worker.processQueue();
    }
}
