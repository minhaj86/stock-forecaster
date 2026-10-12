package com.stockforecast.marketdata;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read model of a market-data job for the admin console.
 *
 * @param requestedBy administrator who requested the job, {@code null} for system jobs
 * @param errorCode   stable code of the last failure (see the job error codes in the API documentation), or null
 * @param retryable   the job failed with a transient error and may be retried
 */
public record MarketDataJobView(UUID id, MarketDataJobType type, MarketDataJobStatus status,
                                MarketDataJobTrigger trigger, UUID stockId, String symbol, String companyName,
                                String provider, MarketDataInterval interval,
                                ExistingDataStrategy existingDataStrategy, LocalDate fromDate, LocalDate toDate,
                                UUID requestedBy, Instant requestedAt, Instant startedAt, Instant completedAt,
                                Long durationMs, int progress, String statusMessage, int attempts, int maxAttempts,
                                Instant nextAttemptAt, int recordsFetched, int recordsInserted, int recordsUpdated,
                                int recordsSkipped, int recordsFailed, int recordsDeleted, String errorCode,
                                String errorMessage, boolean retryable, UUID retryOfJobId, UUID batchId) {
}
