package com.stockforecast.admin.application;

import com.stockforecast.admin.domain.AuditAction;
import com.stockforecast.admin.domain.AuditEntityType;
import com.stockforecast.identity.AccountAdministration;
import com.stockforecast.identity.AuthenticatedUser;
import com.stockforecast.marketdata.MarketDataJobView;
import com.stockforecast.marketdata.MarketDataJobs;
import com.stockforecast.marketdata.MarketDataJobs.BackfillRequest;
import com.stockforecast.marketdata.MarketDataJobs.BulkSubmission;
import com.stockforecast.marketdata.MarketDataJobs.JobPage;
import com.stockforecast.marketdata.MarketDataJobs.JobQuery;
import com.stockforecast.marketdata.MarketDataJobs.JobSubmission;
import com.stockforecast.marketdata.MarketDataJobs.RepopulationRequest;
import com.stockforecast.marketdata.MarketDataJobs.SyncSummary;
import com.stockforecast.marketdata.MarketDataJobStatus;
import com.stockforecast.marketdata.MarketDataJobTrigger;
import com.stockforecast.marketdata.MarketDataJobType;
import com.stockforecast.marketdata.MarketDataProviders;
import com.stockforecast.marketdata.MarketDataProviders.ProviderOverview;
import com.stockforecast.marketdata.PriceHistory;
import com.stockforecast.marketdata.ProviderHealth;
import com.stockforecast.stock.ManagedStock;
import com.stockforecast.stock.StockAdministration;
import com.stockforecast.stock.UnknownStockException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Market-data operations of the admin console. Every request only queues a job (the worker executes it) and is
 * audited; job views are enriched with the requesting administrator's e-mail.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminMarketDataService {

    private final MarketDataJobs jobs;
    private final MarketDataProviders providers;
    private final StockAdministration stocks;
    private final AccountAdministration accounts;
    private final PriceHistory priceHistory;
    private final AuditLog audit;

    public AdminMarketDataService(MarketDataJobs jobs, MarketDataProviders providers, StockAdministration stocks,
                                  AccountAdministration accounts, PriceHistory priceHistory, AuditLog audit) {
        this.jobs = jobs;
        this.providers = providers;
        this.stocks = stocks;
        this.accounts = accounts;
        this.priceHistory = priceHistory;
        this.audit = audit;
    }

    @Transactional
    public Submission requestLatestUpdate(AuthenticatedUser actor, String symbol) {
        ManagedStock stock = stocks.getBySymbol(symbol);
        JobSubmission submission = jobs.requestLatestUpdate(stock.id(), actor.id());
        if (submission.created()) {
            audit.record(actor, AuditAction.MARKET_DATA_REFRESH_STARTED, AuditEntityType.STOCK, stock.symbol(),
                    "Queued a latest-data update for " + stock.symbol(), jobMetadata(submission.job()));
        }
        return submission(submission);
    }

    @Transactional
    public Submission requestBackfill(AuthenticatedUser actor, String symbol, BackfillRequest request) {
        ManagedStock stock = stocks.getBySymbol(symbol);
        JobSubmission submission = jobs.requestBackfill(stock.id(), request, actor.id());
        if (submission.created()) {
            audit.record(actor, AuditAction.MARKET_DATA_BACKFILL_STARTED, AuditEntityType.STOCK, stock.symbol(),
                    "Queued a historical backfill of %s for %s → %s".formatted(stock.symbol(),
                            submission.job().fromDate(), submission.job().toDate()), jobMetadata(submission.job()));
        }
        return submission(submission);
    }

    @Transactional
    public Submission requestRepopulation(AuthenticatedUser actor, String symbol, RepopulationRequest request) {
        ManagedStock stock = stocks.getBySymbol(symbol);
        JobSubmission submission = jobs.requestRepopulation(stock.id(), request, actor.id());
        if (submission.created()) {
            audit.record(actor, AuditAction.MARKET_DATA_REPOPULATION_STARTED, AuditEntityType.STOCK, stock.symbol(),
                    "Queued a re-population of %s for %s → %s".formatted(stock.symbol(), submission.job().fromDate(),
                            submission.job().toDate()), jobMetadata(submission.job()));
        }
        return submission(submission);
    }

    @Transactional
    public BulkLoad requestBulkLoad(AuthenticatedUser actor, Collection<String> symbols, BackfillRequest request) {
        List<ManagedStock> resolved = new ArrayList<>();
        for (String symbol : symbols.stream().distinct().toList()) {
            resolved.add(stocks.findBySymbol(symbol).orElseThrow(() -> new UnknownStockException(symbol)));
        }
        BulkSubmission submission = jobs.requestBulkBackfill(resolved.stream().map(ManagedStock::id).toList(),
                request, actor.id());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("batchId", submission.batchId().toString());
        metadata.put("symbols", resolved.stream().map(ManagedStock::symbol).toList());
        metadata.put("jobs", submission.jobs().size());
        metadata.put("fromDate", String.valueOf(request.fromDate()));
        metadata.put("toDate", String.valueOf(request.toDate()));
        metadata.put("existingDataStrategy", String.valueOf(submission.jobs().getFirst().existingDataStrategy()));
        audit.record(actor, AuditAction.MARKET_DATA_BULK_LOAD_STARTED, AuditEntityType.MARKET_DATA_JOB,
                submission.batchId().toString(), "Queued a bulk historical load of %d stocks".formatted(resolved.size()),
                metadata);
        return new BulkLoad(submission.batchId(), withRequesters(submission.jobs()));
    }

    @Transactional
    public SyncSummary synchronizeWatchedStocks(AuthenticatedUser actor) {
        SyncSummary summary = jobs.synchronizeWatchedStocks(actor.id());
        audit.record(actor, AuditAction.MARKET_DATA_SYNC_TRIGGERED, AuditEntityType.MARKET_DATA_PROVIDER,
                priceHistory.activeProvider().id(), "Triggered synchronisation of %d watched stocks (%d jobs queued)"
                        .formatted(summary.stocksConsidered(), summary.jobsQueued()),
                Map.of("stocksConsidered", summary.stocksConsidered(), "jobsQueued", summary.jobsQueued()));
        return summary;
    }

    @Transactional(readOnly = true)
    public JobList findJobs(MarketDataJobStatus status, MarketDataJobType type, MarketDataJobTrigger trigger,
                            String symbol, int page, int size) {
        UUID stockId = null;
        if (symbol != null && !symbol.isBlank()) {
            Optional<ManagedStock> stock = stocks.findBySymbol(symbol);
            if (stock.isEmpty()) {
                return new JobList(List.of(), page, size, 0);
            }
            stockId = stock.get().id();
        }
        JobPage result = jobs.findJobs(new JobQuery(status, type, trigger, stockId, page, size));
        return new JobList(withRequesters(result.content()), page, size, result.totalElements());
    }

    @Transactional(readOnly = true)
    public JobWithRequester getJob(UUID jobId) {
        return withRequesters(List.of(jobs.getJob(jobId))).getFirst();
    }

    @Transactional
    public Submission retry(AuthenticatedUser actor, UUID jobId) {
        JobSubmission submission = jobs.retry(jobId, actor.id());
        Map<String, Object> metadata = jobMetadata(submission.job());
        metadata.put("retryOfJobId", jobId.toString());
        audit.record(actor, AuditAction.MARKET_DATA_JOB_RETRIED, AuditEntityType.MARKET_DATA_JOB,
                submission.job().id().toString(), "Retried %s job of %s".formatted(submission.job().type(),
                        submission.job().symbol()), metadata);
        return submission(submission);
    }

    @Transactional
    public JobWithRequester cancel(AuthenticatedUser actor, UUID jobId) {
        MarketDataJobView cancelled = jobs.cancel(jobId);
        audit.record(actor, AuditAction.MARKET_DATA_JOB_CANCELLED, AuditEntityType.MARKET_DATA_JOB,
                cancelled.id().toString(), "Cancelled %s job of %s".formatted(cancelled.type(), cancelled.symbol()),
                jobMetadata(cancelled));
        return withRequesters(List.of(cancelled)).getFirst();
    }

    public ProviderOverview providerOverview() {
        return providers.overview();
    }

    public ProviderHealth providerHealth(boolean refresh) {
        return providers.health(refresh);
    }

    private Submission submission(JobSubmission submission) {
        return new Submission(withRequesters(List.of(submission.job())).getFirst(), submission.created());
    }

    private List<JobWithRequester> withRequesters(List<MarketDataJobView> views) {
        List<UUID> requesters = views.stream().map(MarketDataJobView::requestedBy).filter(Objects::nonNull)
                .distinct().toList();
        Map<UUID, String> emails = accounts.findEmails(requesters);
        return views.stream().map(view -> new JobWithRequester(view,
                view.requestedBy() == null ? null : emails.get(view.requestedBy()))).toList();
    }

    private static Map<String, Object> jobMetadata(MarketDataJobView job) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("jobId", job.id().toString());
        metadata.put("jobType", job.type().name());
        metadata.put("symbol", job.symbol());
        if (job.fromDate() != null) {
            metadata.put("fromDate", job.fromDate().toString());
        }
        if (job.toDate() != null) {
            metadata.put("toDate", job.toDate().toString());
        }
        metadata.put("interval", job.interval().name());
        if (job.existingDataStrategy() != null) {
            metadata.put("existingDataStrategy", job.existingDataStrategy().name());
        }
        return metadata;
    }

    /**
     * @param requestedByEmail e-mail of the requesting administrator; {@code null} for system jobs or deleted users
     */
    public record JobWithRequester(MarketDataJobView job, String requestedByEmail) {
    }

    public record Submission(JobWithRequester job, boolean created) {
    }

    public record BulkLoad(UUID batchId, List<JobWithRequester> jobs) {
    }

    public record JobList(List<JobWithRequester> content, int page, int size, long totalElements) {
    }
}
