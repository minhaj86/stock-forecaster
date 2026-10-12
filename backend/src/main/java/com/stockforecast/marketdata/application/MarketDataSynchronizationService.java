package com.stockforecast.marketdata.application;

import com.stockforecast.marketdata.ExistingDataStrategy;
import com.stockforecast.marketdata.MarketDataJobStatus;
import com.stockforecast.marketdata.MarketDataJobType;
import com.stockforecast.marketdata.MarketDataUpdated;
import com.stockforecast.marketdata.application.MarketDataBackfillService.BackfillResult;
import com.stockforecast.marketdata.domain.JobCounters;
import com.stockforecast.marketdata.domain.MarketDataJob;
import com.stockforecast.marketdata.domain.MarketDataJobRepository;
import com.stockforecast.stock.StockCatalog;
import com.stockforecast.stock.StockRef;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Executes queued market-data jobs — the single synchronisation implementation behind the scheduler, watchlist
 * onboarding and the admin console.
 *
 * <p>Per job: claim it (short transaction, {@code FOR UPDATE SKIP LOCKED}) → fetch and validate outside any
 * transaction → write in one short transaction → record the outcome. Transient provider failures are retried with
 * exponential backoff and jitter; when the provider's rate limit is reached the job waits (without using an attempt)
 * and the run ends, so the worker never hammers a throttled provider. Every run is serialised cluster-wide by the
 * worker's lock, which keeps provider usage within one client-side rate limiter.
 */
@Service
public class MarketDataSynchronizationService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataSynchronizationService.class);
    private static final Duration DEFAULT_RATE_LIMIT_WAIT = Duration.ofMinutes(1);

    private final MarketDataJobRepository jobs;
    private final StockCatalog stockCatalog;
    private final MarketDataStore store;
    private final MarketDataBackfillService backfill;
    private final MarketDataRepopulationService repopulation;
    private final MarketDataProvider provider;
    private final MarketDataProperties properties;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    /** Epoch seconds of the last successful synchronisation, exported as a gauge. */
    private final AtomicLong lastSuccessEpochSeconds = new AtomicLong();

    public MarketDataSynchronizationService(MarketDataJobRepository jobs, StockCatalog stockCatalog,
                                            MarketDataStore store, MarketDataBackfillService backfill,
                                            MarketDataRepopulationService repopulation, MarketDataProvider provider,
                                            MarketDataProperties properties, TransactionTemplate transactions,
                                            ApplicationEventPublisher events, Clock clock,
                                            MeterRegistry meterRegistry) {
        this.jobs = jobs;
        this.stockCatalog = stockCatalog;
        this.store = store;
        this.backfill = backfill;
        this.repopulation = repopulation;
        this.provider = provider;
        this.properties = properties;
        this.transactions = transactions;
        this.events = events;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        meterRegistry.gauge("market_data_last_success", lastSuccessEpochSeconds);
    }

    /**
     * Runs due jobs one after another until the queue is empty, the provider's rate limit is reached or the run
     * budget is used up.
     *
     * @return number of jobs executed
     */
    public int processQueue() {
        Instant deadline = clock.instant().plus(properties.jobs().runBudget());
        recoverAbandonedJobs();
        int executed = 0;
        while (clock.instant().isBefore(deadline)) {
            Optional<MarketDataJob> claimed = transactions.execute(status ->
                    jobs.claimNextRunnable(clock.instant()).map(job -> {
                        job.start(clock.instant());
                        return jobs.save(job);
                    }));
            if (claimed == null || claimed.isEmpty()) {
                break;
            }
            executed++;
            if (execute(claimed.get()) == Outcome.RATE_LIMITED) {
                break;
            }
        }
        return executed;
    }

    /** Removes finished jobs past the retention period. */
    public int purgeFinishedJobs() {
        Integer deleted = transactions.execute(status ->
                jobs.deleteFinishedBefore(clock.instant().minus(properties.jobs().retention())));
        return deleted == null ? 0 : deleted;
    }

    Outcome execute(MarketDataJob job) {
        Optional<StockRef> stock = stockCatalog.findById(job.getStockId());
        String symbol = stock.map(StockRef::symbol).orElse("?");
        MDC.put("jobId", job.getId().toString());
        MDC.put("stockSymbol", symbol);
        MDC.put("provider", job.getProvider());
        MDC.put("operation", job.getType().name());
        Timer.Sample sample = Timer.start(meterRegistry);
        Outcome outcome = Outcome.FAILED;
        try {
            log.info("Running {} job {} for {} (attempt {} of {})", job.getType(), job.getId(), symbol,
                    job.getAttempts(), job.getMaxAttempts());
            outcome = run(job, stock);
            return outcome;
        } finally {
            sample.stop(meterRegistry.timer("market_data_sync_duration", "type", job.getType().name(),
                    "outcome", outcome.name()));
            MDC.remove("jobId");
            MDC.remove("stockSymbol");
            MDC.remove("provider");
            MDC.remove("operation");
        }
    }

    private Outcome run(MarketDataJob job, Optional<StockRef> stockLookup) {
        UUID jobId = job.getId();
        if (stockLookup.isEmpty()) {
            return failWithoutProviderError(job, "INTERNAL_ERROR", "The stock no longer exists", false, null);
        }
        StockRef stock = stockLookup.get();
        if (stockCatalog.findAllActiveByIds(List.of(stock.id())).isEmpty()) {
            return failWithoutProviderError(job, "STOCK_INACTIVE", stock.symbol() + " is inactive", false, null);
        }
        if (!provider.capabilities().supports(job.getInterval())) {
            return failWithoutProviderError(job, "UNSUPPORTED_INTERVAL", "Interval %s is not supported by %s"
                    .formatted(job.getInterval(), provider.info().name()), false, null);
        }
        if (!provider.isConfigured()) {
            return failWithoutProviderError(job, "PROVIDER_NOT_CONFIGURED", provider.info().name()
                    + " is not configured (missing API key)", false, null);
        }
        store.recordAttempt(stock.id(), clock.instant());
        JobProgress progress = (percent, message) -> update(jobId, current ->
                current.reportProgress(percent, message, clock.instant()));
        try {
            if (job.getType() == MarketDataJobType.REPOPULATE) {
                JobCounters counters = repopulation.repopulate(jobId, stock, job.getFromDate(), job.getToDate(),
                        job.getInterval(), progress);
                return complete(job, stock, counters, true, "History re-populated from %s → %s"
                        .formatted(job.getFromDate(), job.getToDate()));
            }
            LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
            LocalDate from = job.isIncremental() ? incrementalStart(stock, today) : job.getFromDate();
            LocalDate to = job.isIncremental() ? today : job.getToDate();
            if (job.isIncremental()) {
                update(jobId, current -> current.defineRange(from, to));
            }
            ExistingDataStrategy strategy = job.getExistingDataStrategy() == null ? ExistingDataStrategy.UPDATE
                    : job.getExistingDataStrategy();
            BackfillResult result = backfill.backfill(stock, from, to, job.getInterval(), strategy, progress);
            return complete(job, stock, result.counters(), false, completionMessage(result, from, to));
        } catch (MarketDataProviderException e) {
            return providerFailure(job, stock, e);
        } catch (JobExecutionException e) {
            log.warn("Job {} for {} rejected: {}", jobId, stock.symbol(), e.getMessage());
            store.recordFailure(stock.id(), e.getMessage(), clock.instant());
            return failWithoutProviderError(job, e.errorCode(), e.getMessage(), false, e.counters());
        } catch (RuntimeException e) {
            // Job boundary: an unexpected error must end the job (and be logged), never leave it running.
            log.error("Market-data job {} for {} failed unexpectedly", jobId, stock.symbol(), e);
            store.recordFailure(stock.id(), "Unexpected error while synchronising", clock.instant());
            return failWithoutProviderError(job, "INTERNAL_ERROR",
                    "Unexpected error while processing the job (reference: job " + jobId + ")", true, null);
        }
    }

    /** Incremental updates start after the newest stored bar, re-fetching a short trailing window for corrections. */
    private LocalDate incrementalStart(StockRef stock, LocalDate today) {
        LocalDate lastBar = store.findCoverage(List.of(stock.id())).getOrDefault(stock.id(),
                new MarketDataStore.Coverage(null, null, 0)).lastBarDate();
        if (lastBar == null) {
            return today.minusYears(properties.historyYears());
        }
        LocalDate start = lastBar.plusDays(1).minusDays(properties.refreshOverlapDays());
        return start.isAfter(today) ? today : start;
    }

    private static String completionMessage(BackfillResult result, LocalDate from, LocalDate to) {
        StringBuilder message = new StringBuilder("Synchronised %s → %s".formatted(from, to));
        if (result.validationIssues() != null) {
            message.append(". ").append(result.validationIssues());
        }
        if (result.adjustmentChanged()) {
            message.append(". The provider adjusted already stored prices (dividend or split): re-populate the "
                    + "history to refresh older bars.");
        }
        return message.toString();
    }

    private Outcome providerFailure(MarketDataJob job, StockRef stock, MarketDataProviderException e) {
        String code = e.code().jobErrorCode();
        meterRegistry.counter("market_data_sync_failures", "type", job.getType().name(), "code", code).increment();
        if (e.code() == ProviderErrorCode.RATE_LIMITED) {
            Instant resumeAt = clock.instant().plus(e.retryAfter().orElse(DEFAULT_RATE_LIMIT_WAIT));
            update(job.getId(), current -> current.deferForRateLimit(resumeAt, e.getMessage()));
            log.info("Provider rate limit reached; job {} for {} resumes at {}", job.getId(), stock.symbol(),
                    resumeAt);
            count(job, "DEFERRED");
            return Outcome.RATE_LIMITED;
        }
        store.recordFailure(stock.id(), "Market-data provider error: " + e.getMessage(), clock.instant());
        if (e.isRetryable() && job.hasAttemptsLeft()) {
            Duration delay = backoff(job.getAttempts(), properties.jobs().retryBaseDelay(),
                    properties.jobs().retryMaxDelay(), ThreadLocalRandom.current().nextDouble());
            Instant retryAt = clock.instant().plus(delay);
            update(job.getId(), current -> current.scheduleRetry(code, e.getMessage(), retryAt));
            log.warn("Job {} for {} failed ({}), retrying in {}: {}", job.getId(), stock.symbol(), code, delay,
                    e.getMessage());
            count(job, "RETRY_SCHEDULED");
            return Outcome.RETRY_SCHEDULED;
        }
        log.warn("Job {} for {} failed permanently ({}): {}", job.getId(), stock.symbol(), code, e.getMessage());
        return fail(job, code, e.getMessage(), e.isRetryable(), null);
    }

    private Outcome complete(MarketDataJob job, StockRef stock, JobCounters counters, boolean historyReplaced,
                             String message) {
        Instant now = clock.instant();
        transactions.executeWithoutResult(status -> {
            MarketDataJob current = jobs.findById(job.getId()).orElseThrow();
            current.complete(counters, message, now);
            jobs.save(current);
            if (counters.changedData() || historyReplaced) {
                // Delivered to transactional listeners after this commit.
                events.publishEvent(new MarketDataUpdated(stock.id(), stock.symbol(), job.getType(),
                        current.getFromDate(), current.getToDate(), counters.inserted(), counters.updated(),
                        counters.deleted(), historyReplaced, now));
            }
        });
        lastSuccessEpochSeconds.set(now.getEpochSecond());
        meterRegistry.counter("market_data_records_downloaded", "provider", job.getProvider())
                .increment(counters.fetched());
        recordWrites(job.getProvider(), "inserted", counters.inserted());
        recordWrites(job.getProvider(), "updated", counters.updated());
        recordWrites(job.getProvider(), "deleted", counters.deleted());
        count(job, counters.failed() > 0 ? MarketDataJobStatus.PARTIALLY_COMPLETED.name()
                : MarketDataJobStatus.COMPLETED.name());
        log.info("{} job {} for {} finished: {} fetched, {} inserted, {} updated, {} skipped, {} failed, {} deleted",
                job.getType(), job.getId(), stock.symbol(), counters.fetched(), counters.inserted(),
                counters.updated(), counters.skipped(), counters.failed(), counters.deleted());
        return Outcome.COMPLETED;
    }

    /** Ends the job as failed; provider failures are counted where they are classified. */
    private Outcome fail(MarketDataJob job, String code, String message, boolean transientFailure,
                         JobCounters counters) {
        update(job.getId(), current -> current.fail(code, message, transientFailure, counters, clock.instant()));
        count(job, MarketDataJobStatus.FAILED.name());
        return Outcome.FAILED;
    }

    private Outcome failWithoutProviderError(MarketDataJob job, String code, String message, boolean transientFailure,
                                             JobCounters counters) {
        meterRegistry.counter("market_data_sync_failures", "type", job.getType().name(), "code", code).increment();
        return fail(job, code, message, transientFailure, counters);
    }

    private void recoverAbandonedJobs() {
        Instant now = clock.instant();
        transactions.executeWithoutResult(status ->
                jobs.findByStatusAndHeartbeatAtBefore(MarketDataJobStatus.RUNNING,
                        now.minus(properties.jobs().leaseTimeout())).forEach(job -> {
                    log.warn("Job {} was abandoned by its worker; recovering it", job.getId());
                    job.recoverAbandoned(now, properties.jobs().retryBaseDelay());
                    jobs.save(job);
                }));
    }

    private void update(UUID jobId, Consumer<MarketDataJob> change) {
        transactions.executeWithoutResult(status -> jobs.findById(jobId).ifPresent(job -> {
            change.accept(job);
            jobs.save(job);
        }));
    }

    private void count(MarketDataJob job, String result) {
        meterRegistry.counter("market_data_sync_jobs", "type", job.getType().name(), "result", result).increment();
    }

    private void recordWrites(String providerId, String operation, int records) {
        if (records > 0) {
            meterRegistry.counter("market_data_records_updated", "provider", providerId, "operation", operation)
                    .increment(records);
        }
    }

    /**
     * Exponential backoff with ±20 % jitter: base × 2^(attempt − 1), capped.
     *
     * @param random uniform in [0, 1)
     */
    static Duration backoff(int attempt, Duration base, Duration max, double random) {
        long factor = 1L << Math.clamp(attempt - 1, 0, 20);
        long millis = Math.min(base.toMillis() * factor, max.toMillis());
        double jitter = 0.8 + 0.4 * random;
        return Duration.ofMillis(Math.max(1, Math.round(millis * jitter)));
    }

    enum Outcome {
        COMPLETED,
        RETRY_SCHEDULED,
        RATE_LIMITED,
        FAILED
    }
}
