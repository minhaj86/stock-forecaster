package com.stockforecast.marketdata.application;

import com.stockforecast.marketdata.ExistingDataStrategy;
import com.stockforecast.marketdata.MarketDataInterval;
import com.stockforecast.marketdata.MarketDataJobStatus;
import com.stockforecast.marketdata.MarketDataJobTrigger;
import com.stockforecast.marketdata.MarketDataJobType;
import com.stockforecast.marketdata.MarketDataJobView;
import com.stockforecast.marketdata.MarketDataJobs;
import com.stockforecast.marketdata.MarketDataStatus;
import com.stockforecast.marketdata.domain.MarketDataExceptions.InvalidDateRangeException;
import com.stockforecast.marketdata.domain.MarketDataExceptions.MarketDataJobNotFoundException;
import com.stockforecast.marketdata.domain.MarketDataExceptions.StockInactiveException;
import com.stockforecast.marketdata.domain.MarketDataExceptions.UnsupportedMarketDataIntervalException;
import com.stockforecast.marketdata.domain.MarketDataJob;
import com.stockforecast.marketdata.domain.MarketDataJobRepository;
import com.stockforecast.marketdata.domain.MarketDataJobSearch;
import com.stockforecast.shared.domain.Identifiers;
import com.stockforecast.stock.StockCatalog;
import com.stockforecast.stock.StockRef;
import com.stockforecast.stock.UnknownStockException;
import com.stockforecast.watchlist.WatchlistQueries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Queues market-data jobs. Every trigger — administrators, users watching a stock and the scheduler — creates jobs
 * here; the worker ({@link MarketDataSynchronizationService}) executes them, so all paths share one implementation.
 * Identical active jobs are never queued twice.
 */
@Service
public class MarketDataJobService implements MarketDataJobs {

    public static final Set<MarketDataJobStatus> ACTIVE = EnumSet.of(MarketDataJobStatus.QUEUED, MarketDataJobStatus.RUNNING);
    private static final LocalDate EARLIEST_DATE = LocalDate.of(1970, 1, 1);
    private static final int MAX_BULK_STOCKS = 50;

    private static final Logger log = LoggerFactory.getLogger(MarketDataJobService.class);

    private final MarketDataJobRepository jobs;
    private final MarketDataJobSearch jobSearch;
    private final MarketDataJobViews views;
    private final StockCatalog stockCatalog;
    private final WatchlistQueries watchlists;
    private final MarketDataStore store;
    private final MarketDataProvider provider;
    private final MarketDataProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public MarketDataJobService(MarketDataJobRepository jobs, MarketDataJobSearch jobSearch, MarketDataJobViews views,
                                StockCatalog stockCatalog, WatchlistQueries watchlists, MarketDataStore store,
                                MarketDataProvider provider, MarketDataProperties properties,
                                ApplicationEventPublisher events, Clock clock) {
        this.jobs = jobs;
        this.jobSearch = jobSearch;
        this.views = views;
        this.stockCatalog = stockCatalog;
        this.watchlists = watchlists;
        this.store = store;
        this.provider = provider;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
    }

    // --- administration -------------------------------------------------------------------------------------

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public JobSubmission requestLatestUpdate(UUID stockId, UUID requestedBy) {
        return submit(request(MarketDataJobType.LATEST_UPDATE, MarketDataJobTrigger.ADMIN, stockId, null, null,
                null, requestedBy, null));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public JobSubmission requestBackfill(UUID stockId, BackfillRequest request, UUID requestedBy) {
        validate(request.fromDate(), request.toDate(), request.interval());
        return submit(request(MarketDataJobType.BACKFILL, MarketDataJobTrigger.ADMIN, stockId,
                strategyOrDefault(request.existingDataStrategy()), request.fromDate(), request.toDate(), requestedBy,
                null));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public JobSubmission requestRepopulation(UUID stockId, RepopulationRequest request, UUID requestedBy) {
        validate(request.fromDate(), request.toDate(), request.interval());
        return submit(request(MarketDataJobType.REPOPULATE, MarketDataJobTrigger.ADMIN, stockId, null,
                request.fromDate(), request.toDate(), requestedBy, null));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public JobSubmission requestInitialLoad(UUID stockId, UUID requestedBy) {
        return submit(request(MarketDataJobType.INITIAL_LOAD, MarketDataJobTrigger.ADMIN, stockId, null, null, null,
                requestedBy, null));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public BulkSubmission requestBulkBackfill(List<UUID> stockIds, BackfillRequest request, UUID requestedBy) {
        if (stockIds.isEmpty() || stockIds.size() > MAX_BULK_STOCKS) {
            throw new IllegalArgumentException("A bulk load covers 1-%d stocks".formatted(MAX_BULK_STOCKS));
        }
        validate(request.fromDate(), request.toDate(), request.interval());
        UUID batchId = Identifiers.newId();
        List<MarketDataJobView> queued = new ArrayList<>();
        for (UUID stockId : stockIds.stream().distinct().toList()) {
            queued.add(submit(request(MarketDataJobType.BACKFILL, MarketDataJobTrigger.ADMIN, stockId,
                    strategyOrDefault(request.existingDataStrategy()), request.fromDate(), request.toDate(),
                    requestedBy, batchId)).job());
        }
        return new BulkSubmission(batchId, queued);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public SyncSummary synchronizeWatchedStocks(UUID requestedBy) {
        return queueWatchedStocks(MarketDataJobTrigger.ADMIN, requestedBy);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public JobPage findJobs(JobQuery query) {
        MarketDataJobSearch.SearchPage result = jobSearch.search(new MarketDataJobSearch.Criteria(query.status(),
                query.type(), query.trigger(), query.stockId(), query.page(), query.size()));
        return new JobPage(views.toViews(result.content()), query.page(), query.size(), result.totalElements());
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public Optional<MarketDataJobView> findJob(UUID jobId) {
        return jobs.findById(jobId).map(views::toView);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public MarketDataJobView getJob(UUID jobId) {
        return jobs.findById(jobId).map(views::toView).orElseThrow(MarketDataJobNotFoundException::new);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public JobSubmission retry(UUID jobId, UUID requestedBy) {
        MarketDataJob failed = jobs.findById(jobId).orElseThrow(MarketDataJobNotFoundException::new);
        MarketDataJob retry = failed.retry(requestedBy, properties.jobs().maxAttempts(), clock.instant());
        StockRef stock = activeStock(retry.getStockId());
        log.info("Job {} for {} retried as job {}", jobId, stock.symbol(), retry.getId());
        return new JobSubmission(enqueue(retry), true);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public MarketDataJobView cancel(UUID jobId) {
        MarketDataJob job = jobs.findById(jobId).orElseThrow(MarketDataJobNotFoundException::new);
        job.cancel(clock.instant());
        return views.toView(jobs.save(job));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public JobStatistics statistics() {
        Instant midnight = LocalDate.now(clock.withZone(ZoneOffset.UTC)).atStartOfDay(ZoneOffset.UTC).toInstant();
        Optional<MarketDataJob> lastFailure = jobs.findFirstByStatusOrderByCompletedAtDesc(MarketDataJobStatus.FAILED);
        return new JobStatistics(
                jobs.countByStatus(MarketDataJobStatus.QUEUED),
                jobs.countByStatus(MarketDataJobStatus.RUNNING),
                jobs.countByStatusInAndCompletedAtGreaterThanEqual(
                        EnumSet.of(MarketDataJobStatus.COMPLETED, MarketDataJobStatus.PARTIALLY_COMPLETED), midnight),
                jobs.countByStatusInAndCompletedAtGreaterThanEqual(EnumSet.of(MarketDataJobStatus.FAILED), midnight),
                lastFailure.map(MarketDataJob::getCompletedAt).orElse(null),
                lastFailure.map(MarketDataJob::getErrorMessage).orElse(null));
    }

    // --- system triggers ------------------------------------------------------------------------------------

    /**
     * Scheduled synchronisation (and start-up catch-up): an incremental update for every watched, active stock.
     */
    @Transactional
    public SyncSummary queueScheduledSynchronisation() {
        return queueWatchedStocks(MarketDataJobTrigger.SCHEDULE, null);
    }

    /**
     * A user watched a stock (called inside the watchlist transaction): load its history if it has none, or catch up
     * if the newest stored bar is older than the last completed weekday. Popular stocks therefore cause no work.
     */
    @Transactional
    public void onStockWatched(UUID stockId) {
        Optional<StockRef> stock = stockCatalog.findById(stockId);
        if (stock.isEmpty()) {
            return;
        }
        MarketDataStatus status = store.findStatuses(List.of(stockId)).get(stockId);
        LocalDate lastBar = status == null ? null : status.lastBarDate();
        if (lastBar == null) {
            submit(request(MarketDataJobType.INITIAL_LOAD, MarketDataJobTrigger.WATCHLIST, stockId, null, null, null,
                    null, null));
        } else if (lastBar.isBefore(lastCompletedWeekday())) {
            submit(request(MarketDataJobType.AUTOMATIC_SYNC, MarketDataJobTrigger.WATCHLIST, stockId, null, null,
                    null, null, null));
        }
    }

    // --- internals ------------------------------------------------------------------------------------------

    private SyncSummary queueWatchedStocks(MarketDataJobTrigger trigger, UUID requestedBy) {
        List<StockRef> watched = stockCatalog.findAllActiveByIds(watchlists.findAllWatchedStockIds());
        Set<UUID> busy = jobs.findByStockIdInAndStatusIn(watched.stream().map(StockRef::id).toList(), ACTIVE).stream()
                .map(MarketDataJob::getStockId).collect(Collectors.toSet());
        int queued = 0;
        for (StockRef stock : watched) {
            if (!busy.contains(stock.id())) {
                submit(request(MarketDataJobType.AUTOMATIC_SYNC, trigger, stock.id(), null, null, null, requestedBy,
                        null));
                queued++;
            }
        }
        log.info("Queued {} market-data updates for {} watched stocks ({})", queued, watched.size(), trigger);
        return new SyncSummary(watched.size(), queued);
    }

    private JobSubmission submit(MarketDataJob.Request request) {
        activeStock(request.stockId());
        Optional<MarketDataJob> duplicate = jobs.findByStockIdInAndStatusIn(List.of(request.stockId()), ACTIVE).stream()
                .filter(job -> job.isEquivalentActiveJob(request)
                        || (job.isIncremental() && isIncremental(request.type())))
                .findFirst();
        if (duplicate.isPresent()) {
            return new JobSubmission(views.toView(duplicate.get()), false);
        }
        MarketDataJob job = MarketDataJob.queue(request, provider.info().id(), properties.jobs().maxAttempts(),
                clock.instant());
        return new JobSubmission(enqueue(job), true);
    }

    private MarketDataJobView enqueue(MarketDataJob job) {
        MarketDataJob saved = jobs.save(job);
        store.ensureTracked(saved.getStockId(), clock.instant());
        events.publishEvent(new MarketDataJobQueued(saved.getId()));
        log.debug("Queued {} job {} for stock {}", saved.getType(), saved.getId(), saved.getStockId());
        return views.toView(saved);
    }

    private StockRef activeStock(UUID stockId) {
        StockRef stock = stockCatalog.findById(stockId).orElseThrow(() -> new UnknownStockException(stockId.toString()));
        if (stockCatalog.findAllActiveByIds(List.of(stockId)).isEmpty()) {
            throw new StockInactiveException(stock.symbol());
        }
        return stock;
    }

    private void validate(LocalDate from, LocalDate to, MarketDataInterval interval) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        if (from == null || to == null) {
            throw new InvalidDateRangeException("Both fromDate and toDate are required");
        }
        if (from.isAfter(to)) {
            throw new InvalidDateRangeException("fromDate must not be after toDate");
        }
        if (to.isAfter(today)) {
            throw new InvalidDateRangeException("toDate must not be in the future (today is %s UTC)".formatted(today));
        }
        if (from.isBefore(EARLIEST_DATE)) {
            throw new InvalidDateRangeException("fromDate must be on or after " + EARLIEST_DATE);
        }
        MarketDataInterval effective = interval == null ? MarketDataInterval.ONE_DAY : interval;
        if (!provider.capabilities().supports(effective)) {
            throw new UnsupportedMarketDataIntervalException(effective, provider.info().name());
        }
    }

    private static MarketDataJob.Request request(MarketDataJobType type, MarketDataJobTrigger trigger, UUID stockId,
                                                 ExistingDataStrategy strategy, LocalDate from, LocalDate to,
                                                 UUID requestedBy, UUID batchId) {
        return new MarketDataJob.Request(type, trigger, stockId, MarketDataInterval.ONE_DAY, strategy, from, to,
                requestedBy, batchId);
    }

    private static ExistingDataStrategy strategyOrDefault(ExistingDataStrategy strategy) {
        return strategy == null ? ExistingDataStrategy.SKIP : strategy;
    }

    private static boolean isIncremental(MarketDataJobType type) {
        return type == MarketDataJobType.LATEST_UPDATE || type == MarketDataJobType.AUTOMATIC_SYNC
                || type == MarketDataJobType.INITIAL_LOAD;
    }

    private LocalDate lastCompletedWeekday() {
        LocalDate candidate = LocalDate.now(clock.withZone(ZoneOffset.UTC)).minusDays(1);
        while (candidate.getDayOfWeek() == DayOfWeek.SATURDAY || candidate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            candidate = candidate.minusDays(1);
        }
        return candidate;
    }
}
