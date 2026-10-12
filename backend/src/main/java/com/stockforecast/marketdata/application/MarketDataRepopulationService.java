package com.stockforecast.marketdata.application;

import com.stockforecast.marketdata.DailyBar;
import com.stockforecast.marketdata.MarketDataInterval;
import com.stockforecast.marketdata.application.MarketDataProvider.HistoricalPrices;
import com.stockforecast.marketdata.application.MarketDataProvider.ProviderInstrument;
import com.stockforecast.marketdata.application.MarketDataStore.ReplaceResult;
import com.stockforecast.marketdata.application.MarketDataStore.StagedSummary;
import com.stockforecast.marketdata.application.MarketDataValidationService.ValidationReport;
import com.stockforecast.marketdata.domain.JobCounters;
import com.stockforecast.stock.StockRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Rebuilds the stored history of a stock in a date range from the provider — safely:
 *
 * <pre>
 * FETCH → VALIDATE → STAGE → VERIFY → TRANSACTIONAL REPLACE → COMMIT
 * </pre>
 *
 * Nothing is deleted before the complete replacement is fetched, fully validated and staged; the swap runs in a single
 * transaction. If the provider fails, returns invalid data or returns nothing for a range that has data, the stored
 * history stays untouched. Only the span the provider actually covered is replaced, so a vendor that returns a
 * shorter history than requested never deletes older stored bars.
 */
@Service
public class MarketDataRepopulationService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataRepopulationService.class);

    private final MarketDataProvider provider;
    private final MarketDataValidationService validation;
    private final MarketDataStore store;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public MarketDataRepopulationService(MarketDataProvider provider, MarketDataValidationService validation,
                                         MarketDataStore store, TransactionTemplate transactions, Clock clock) {
        this.provider = provider;
        this.validation = validation;
        this.store = store;
        this.transactions = transactions;
        this.clock = clock;
    }

    public JobCounters repopulate(UUID jobId, StockRef stock, LocalDate from, LocalDate to,
                                  MarketDataInterval interval, JobProgress progress) {
        progress.report(10, "Fetching %s → %s from %s".formatted(from, to, provider.info().name()));
        HistoricalPrices response = provider.getHistoricalPrices(new ProviderInstrument(stock.symbol(), stock.mic()),
                from, to, interval);

        progress.report(50, "Validating %d bars".formatted(response.prices().size()));
        ValidationReport report = validation.validate(stock, from, to, interval, response);
        if (report.isRejected()) {
            throw rejected(report.rejectionReason(), report, report.fetched());
        }
        if (report.hasViolations()) {
            throw rejected("A re-population needs a completely valid dataset; nothing was changed. "
                    + report.violationSummary(), report, report.violations().size());
        }
        List<DailyBar> bars = report.validBars();
        if (bars.isEmpty()) {
            long stored = store.countBars(stock.id(), from, to);
            if (stored > 0) {
                throw new JobExecutionException("REPOPULATION_EMPTY_RESULT",
                        "The provider returned no data for %s → %s; the %d stored bars were kept"
                                .formatted(from, to, stored), new JobCounters(0, 0, 0, 0, 0, 0));
            }
            return JobCounters.NONE;
        }

        try {
            progress.report(65, "Staging %d validated bars".formatted(bars.size()));
            transactions.executeWithoutResult(status -> {
                store.clearStaged(jobId);
                store.stageBars(jobId, bars);
            });

            progress.report(75, "Verifying staged data");
            StagedSummary staged = store.summarizeStaged(jobId);
            if (staged.count() != bars.size() || !bars.getFirst().date().equals(staged.firstDate())
                    || !bars.getLast().date().equals(staged.lastDate())) {
                throw new JobExecutionException("INTERNAL_ERROR",
                        "Staged data does not match the validated provider data; nothing was changed",
                        new JobCounters(report.fetched(), 0, 0, 0, 0, 0));
            }

            LocalDate spanFrom = staged.firstDate().isAfter(from) ? staged.firstDate() : from;
            LocalDate spanTo = staged.lastDate().isBefore(to) ? staged.lastDate() : to;
            progress.report(85, "Replacing stored bars %s → %s".formatted(spanFrom, spanTo));
            String providerId = provider.info().id();
            ReplaceResult result = transactions.execute(status -> {
                Instant now = clock.instant();
                ReplaceResult replaced = store.replaceWithStaged(stock.id(), jobId, spanFrom, spanTo, providerId, now);
                store.recordSuccess(stock.id(), providerId, now);
                return replaced;
            });
            log.info("Re-populated {} {} → {}: {} inserted, {} updated, {} unchanged, {} deleted", stock.symbol(),
                    spanFrom, spanTo, result.inserted(), result.updated(), result.unchanged(), result.deleted());
            return new JobCounters(report.fetched(), result.inserted(), result.updated(), result.unchanged(), 0,
                    result.deleted());
        } finally {
            discardStaging(jobId);
        }
    }

    private void discardStaging(UUID jobId) {
        try {
            store.clearStaged(jobId);
        } catch (DataAccessException e) {
            // Staged rows are also removed with the job; a leftover never affects stored prices.
            log.warn("Could not discard staged bars of job {}: {}", jobId, e.getMessage());
        }
    }

    private static JobExecutionException rejected(String message, ValidationReport report, int failed) {
        return new JobExecutionException("INVALID_PROVIDER_DATA", message,
                new JobCounters(report.fetched(), 0, 0, 0, failed, 0));
    }
}
