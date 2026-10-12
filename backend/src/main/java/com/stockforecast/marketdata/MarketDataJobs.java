package com.stockforecast.marketdata;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Asynchronous market-data jobs. Requests only queue work and return immediately; the job worker executes jobs one
 * after another within the provider's rate limits. The scheduler, the watchlist onboarding and the admin console all
 * go through this API and therefore share the same synchronisation logic. Administrative methods require the ADMIN
 * role (method security).
 */
public interface MarketDataJobs {

    /** Incremental update from the newest stored bar (or the initial history if there is none). */
    JobSubmission requestLatestUpdate(UUID stockId, UUID requestedBy);

    JobSubmission requestBackfill(UUID stockId, BackfillRequest request, UUID requestedBy);

    /**
     * Safe rebuild of the stored history in the range: the provider data is fetched, validated and staged first and
     * replaces the stored bars in one transaction; on any failure the stored data stays untouched.
     */
    JobSubmission requestRepopulation(UUID stockId, RepopulationRequest request, UUID requestedBy);

    /** Loads the configured initial history (used when an administrator registers a stock). */
    JobSubmission requestInitialLoad(UUID stockId, UUID requestedBy);

    BulkSubmission requestBulkBackfill(List<UUID> stockIds, BackfillRequest request, UUID requestedBy);

    /** Queues an incremental update for every watched, active stock without an active job. */
    SyncSummary synchronizeWatchedStocks(UUID requestedBy);

    JobPage findJobs(JobQuery query);

    Optional<MarketDataJobView> findJob(UUID jobId);

    /**
     * @throws com.stockforecast.shared.domain.DomainException {@code MARKET_DATA_JOB_NOT_FOUND} (404)
     */
    MarketDataJobView getJob(UUID jobId);

    /** Queues a new job with the parameters of a failed, retryable job. */
    JobSubmission retry(UUID jobId, UUID requestedBy);

    MarketDataJobView cancel(UUID jobId);

    JobStatistics statistics();

    record BackfillRequest(LocalDate fromDate, LocalDate toDate, MarketDataInterval interval,
                           ExistingDataStrategy existingDataStrategy) {
    }

    record RepopulationRequest(LocalDate fromDate, LocalDate toDate, MarketDataInterval interval) {
    }

    /**
     * @param created {@code false} if an identical job was already queued or running and is returned instead
     */
    record JobSubmission(MarketDataJobView job, boolean created) {
    }

    record BulkSubmission(UUID batchId, List<MarketDataJobView> jobs) {
    }

    record SyncSummary(int stocksConsidered, int jobsQueued) {
    }

    /** All filters are optional. */
    record JobQuery(MarketDataJobStatus status, MarketDataJobType type, MarketDataJobTrigger trigger, UUID stockId,
                    int page, int size) {
    }

    record JobPage(List<MarketDataJobView> content, int page, int size, long totalElements) {
    }

    /**
     * @param completedToday completed or partially completed since midnight UTC
     * @param failedToday    failed since midnight UTC
     */
    record JobStatistics(long queued, long running, long completedToday, long failedToday, Instant lastFailedAt,
                         String lastFailureMessage) {
    }
}
