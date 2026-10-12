package com.stockforecast.marketdata.domain;

import com.stockforecast.marketdata.ExistingDataStrategy;
import com.stockforecast.marketdata.MarketDataInterval;
import com.stockforecast.marketdata.MarketDataJobStatus;
import com.stockforecast.marketdata.MarketDataJobTrigger;
import com.stockforecast.marketdata.MarketDataJobType;
import com.stockforecast.marketdata.domain.MarketDataExceptions.MarketDataJobNotCancellableException;
import com.stockforecast.marketdata.domain.MarketDataExceptions.MarketDataJobNotRetryableException;
import com.stockforecast.shared.domain.Identifiers;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * One market-data synchronisation job for one stock: requested by an administrator, by a user watching a new stock or
 * by the scheduler, executed asynchronously by the job worker.
 *
 * <p>Lifecycle: {@code QUEUED → RUNNING → COMPLETED | PARTIALLY_COMPLETED | FAILED}; a transient failure puts the job
 * back to {@code QUEUED} with a later {@code nextAttemptAt} (exponential backoff) until {@code maxAttempts} is
 * reached; queued jobs can be cancelled. A failed job with a transient error can be retried, which creates a new job
 * linked to it so the history of attempts stays visible.
 */
@Entity
@Table(name = "market_data_sync_jobs")
public class MarketDataJob {

    static final int MAX_STATUS_MESSAGE_LENGTH = 300;
    static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 20, updatable = false)
    private MarketDataJobType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private MarketDataJobStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_source", nullable = false, length = 16, updatable = false)
    private MarketDataJobTrigger trigger;

    @Column(name = "stock_id", nullable = false, updatable = false)
    private UUID stockId;

    @Column(nullable = false, length = 32, updatable = false)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "bar_interval", nullable = false, length = 16, updatable = false)
    private MarketDataInterval interval;

    @Enumerated(EnumType.STRING)
    @Column(name = "existing_data_strategy", length = 10, updatable = false)
    private ExistingDataStrategy existingDataStrategy;

    /** Requested range; incremental jobs record the range they actually fetched when they run. */
    @Column(name = "from_date")
    private LocalDate fromDate;

    @Column(name = "to_date")
    private LocalDate toDate;

    /** Administrator who requested the job; {@code null} for system jobs. Not a foreign key: jobs outlive accounts. */
    @Column(name = "requested_by", updatable = false)
    private UUID requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Liveness of the worker running the job; a stale heartbeat means the worker died. */
    @Column(name = "heartbeat_at")
    private Instant heartbeatAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false, updatable = false)
    private int maxAttempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(nullable = false)
    private int progress;

    @Column(name = "status_message", length = MAX_STATUS_MESSAGE_LENGTH)
    private String statusMessage;

    @Column(name = "records_fetched", nullable = false)
    private int recordsFetched;

    @Column(name = "records_inserted", nullable = false)
    private int recordsInserted;

    @Column(name = "records_updated", nullable = false)
    private int recordsUpdated;

    @Column(name = "records_skipped", nullable = false)
    private int recordsSkipped;

    @Column(name = "records_failed", nullable = false)
    private int recordsFailed;

    @Column(name = "records_deleted", nullable = false)
    private int recordsDeleted;

    @Column(name = "error_code", length = 40)
    private String errorCode;

    @Column(name = "error_message", length = MAX_ERROR_MESSAGE_LENGTH)
    private String errorMessage;

    @Column(nullable = false)
    private boolean retryable;

    @Column(name = "retry_of_job_id", updatable = false)
    private UUID retryOfJobId;

    @Column(name = "batch_id", updatable = false)
    private UUID batchId;

    @Version
    private Long version;

    protected MarketDataJob() {
        // required by JPA
    }

    private MarketDataJob(Request request, String provider, int maxAttempts, UUID retryOfJobId, Instant now) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        if (request.fromDate() != null && request.toDate() != null && request.fromDate().isAfter(request.toDate())) {
            throw new IllegalArgumentException("fromDate must not be after toDate");
        }
        this.id = Identifiers.newId();
        this.type = Objects.requireNonNull(request.type(), "type");
        this.trigger = Objects.requireNonNull(request.trigger(), "trigger");
        this.stockId = Objects.requireNonNull(request.stockId(), "stockId");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.interval = Objects.requireNonNull(request.interval(), "interval");
        this.existingDataStrategy = request.existingDataStrategy();
        this.fromDate = request.fromDate();
        this.toDate = request.toDate();
        this.requestedBy = request.requestedBy();
        this.batchId = request.batchId();
        this.retryOfJobId = retryOfJobId;
        this.maxAttempts = maxAttempts;
        this.status = MarketDataJobStatus.QUEUED;
        this.requestedAt = Objects.requireNonNull(now, "now");
        this.statusMessage = "Waiting for the job worker";
    }

    public static MarketDataJob queue(Request request, String provider, int maxAttempts, Instant now) {
        return new MarketDataJob(request, provider, maxAttempts, null, now);
    }

    /** True if this active job already does what the request asks for (duplicate submissions are not queued). */
    public boolean isEquivalentActiveJob(Request request) {
        return status.isActive() && type == request.type() && stockId.equals(request.stockId())
                && interval == request.interval() && existingDataStrategy == request.existingDataStrategy()
                && (isIncremental() || (Objects.equals(fromDate, request.fromDate())
                && Objects.equals(toDate, request.toDate())));
    }

    /** The range is derived from the stored data when the job runs. */
    public boolean isIncremental() {
        return type == MarketDataJobType.LATEST_UPDATE || type == MarketDataJobType.AUTOMATIC_SYNC
                || type == MarketDataJobType.INITIAL_LOAD;
    }

    public void start(Instant now) {
        requireStatus(MarketDataJobStatus.QUEUED, "start");
        this.status = MarketDataJobStatus.RUNNING;
        this.attempts++;
        if (startedAt == null) {
            this.startedAt = now;
        }
        this.heartbeatAt = now;
        this.nextAttemptAt = null;
        this.progress = 0;
        this.errorCode = null;
        this.errorMessage = null;
        this.retryable = false;
        this.statusMessage = attempts > 1 ? "Attempt %d of %d started".formatted(attempts, maxAttempts) : "Started";
    }

    public void defineRange(LocalDate from, LocalDate to) {
        requireStatus(MarketDataJobStatus.RUNNING, "define the range of");
        this.fromDate = from;
        this.toDate = to;
    }

    public void reportProgress(int percent, String message, Instant now) {
        requireStatus(MarketDataJobStatus.RUNNING, "report progress of");
        this.progress = Math.clamp(percent, 0, 99);
        this.statusMessage = truncate(message, MAX_STATUS_MESSAGE_LENGTH);
        this.heartbeatAt = Objects.requireNonNull(now, "now");
    }

    public void complete(JobCounters counters, String message, Instant now) {
        requireStatus(MarketDataJobStatus.RUNNING, "complete");
        applyCounters(counters);
        this.status = counters.failed() > 0 ? MarketDataJobStatus.PARTIALLY_COMPLETED : MarketDataJobStatus.COMPLETED;
        this.progress = 100;
        this.statusMessage = truncate(message, MAX_STATUS_MESSAGE_LENGTH);
        finish(now);
    }

    /**
     * @param transientFailure the cause may go away (timeouts, 5xx, rate limits): the job may be retried manually
     * @param counters         what was fetched and rejected before the failure (nothing was written), or null
     */
    public void fail(String code, String message, boolean transientFailure, JobCounters counters, Instant now) {
        requireStatus(MarketDataJobStatus.RUNNING, "fail");
        if (counters != null) {
            applyCounters(counters);
        }
        this.status = MarketDataJobStatus.FAILED;
        this.errorCode = Objects.requireNonNull(code, "code");
        this.errorMessage = truncate(message, MAX_ERROR_MESSAGE_LENGTH);
        this.retryable = transientFailure;
        this.statusMessage = "Failed";
        finish(now);
    }

    /** A transient failure with attempts left: back to the queue after the backoff. */
    public void scheduleRetry(String code, String message, Instant retryAt) {
        requireStatus(MarketDataJobStatus.RUNNING, "reschedule");
        this.status = MarketDataJobStatus.QUEUED;
        this.errorCode = Objects.requireNonNull(code, "code");
        this.errorMessage = truncate(message, MAX_ERROR_MESSAGE_LENGTH);
        this.retryable = true;
        this.nextAttemptAt = Objects.requireNonNull(retryAt, "retryAt");
        this.heartbeatAt = null;
        this.progress = 0;
        this.statusMessage = "Attempt %d of %d failed; retrying automatically".formatted(attempts, maxAttempts);
    }

    /**
     * The provider's rate limit was reached before anything was fetched: the job waits without using an attempt.
     */
    public void deferForRateLimit(Instant resumeAt, String message) {
        requireStatus(MarketDataJobStatus.RUNNING, "defer");
        this.status = MarketDataJobStatus.QUEUED;
        this.attempts = Math.max(0, attempts - 1);
        this.errorCode = "PROVIDER_RATE_LIMITED";
        this.errorMessage = truncate(message, MAX_ERROR_MESSAGE_LENGTH);
        this.retryable = true;
        this.nextAttemptAt = Objects.requireNonNull(resumeAt, "resumeAt");
        this.heartbeatAt = null;
        this.progress = 0;
        this.statusMessage = "Provider rate limit reached; the job resumes automatically";
    }

    public void cancel(Instant now) {
        if (status != MarketDataJobStatus.QUEUED) {
            throw new MarketDataJobNotCancellableException(status);
        }
        this.status = MarketDataJobStatus.CANCELLED;
        this.statusMessage = "Cancelled by an administrator";
        finish(now);
    }

    /**
     * The worker running this job stopped (its heartbeat is stale): queue it again, or fail it if no attempts remain.
     */
    public void recoverAbandoned(Instant now, Duration retryDelay) {
        requireStatus(MarketDataJobStatus.RUNNING, "recover");
        String message = "The job worker stopped while running this job";
        if (attempts < maxAttempts) {
            scheduleRetry("INTERNAL_ERROR", message, now.plus(retryDelay));
        } else {
            fail("INTERNAL_ERROR", message, true, null, now);
        }
    }

    public boolean hasAttemptsLeft() {
        return attempts < maxAttempts;
    }

    public boolean canBeRetried() {
        return status == MarketDataJobStatus.FAILED && retryable;
    }

    /**
     * A new job with the same parameters, linked to this failed one.
     */
    public MarketDataJob retry(UUID retriedBy, int newMaxAttempts, Instant now) {
        if (!canBeRetried()) {
            throw new MarketDataJobNotRetryableException(status, errorCode);
        }
        Request request = new Request(type, MarketDataJobTrigger.ADMIN, stockId, interval, existingDataStrategy,
                isIncremental() ? null : fromDate, isIncremental() ? null : toDate, retriedBy, null);
        return new MarketDataJob(request, provider, newMaxAttempts, id, now);
    }

    public Long durationMillis() {
        return startedAt == null || completedAt == null ? null : Duration.between(startedAt, completedAt).toMillis();
    }

    private void finish(Instant now) {
        this.completedAt = Objects.requireNonNull(now, "now");
        this.heartbeatAt = null;
        this.nextAttemptAt = null;
    }

    private void applyCounters(JobCounters counters) {
        this.recordsFetched = counters.fetched();
        this.recordsInserted = counters.inserted();
        this.recordsUpdated = counters.updated();
        this.recordsSkipped = counters.skipped();
        this.recordsFailed = counters.failed();
        this.recordsDeleted = counters.deleted();
    }

    private void requireStatus(MarketDataJobStatus expected, String action) {
        if (status != expected) {
            throw new IllegalStateException("Cannot %s job %s in status %s".formatted(action, id, status));
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 1) + "…";
    }

    public UUID getId() {
        return id;
    }

    public MarketDataJobType getType() {
        return type;
    }

    public MarketDataJobStatus getStatus() {
        return status;
    }

    public MarketDataJobTrigger getTrigger() {
        return trigger;
    }

    public UUID getStockId() {
        return stockId;
    }

    public String getProvider() {
        return provider;
    }

    public MarketDataInterval getInterval() {
        return interval;
    }

    public ExistingDataStrategy getExistingDataStrategy() {
        return existingDataStrategy;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getHeartbeatAt() {
        return heartbeatAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public int getProgress() {
        return progress;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public JobCounters getCounters() {
        return new JobCounters(recordsFetched, recordsInserted, recordsUpdated, recordsSkipped, recordsFailed,
                recordsDeleted);
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public UUID getRetryOfJobId() {
        return retryOfJobId;
    }

    public UUID getBatchId() {
        return batchId;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof MarketDataJob job && id.equals(job.getId());
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "MarketDataJob[id=" + id + ", type=" + type + ", status=" + status + ", stockId=" + stockId + "]";
    }

    /**
     * What to do. Dates are required for {@code BACKFILL} and {@code REPOPULATE}; incremental jobs derive them.
     */
    public record Request(MarketDataJobType type, MarketDataJobTrigger trigger, UUID stockId,
                          MarketDataInterval interval, ExistingDataStrategy existingDataStrategy, LocalDate fromDate,
                          LocalDate toDate, UUID requestedBy, UUID batchId) {
    }
}
