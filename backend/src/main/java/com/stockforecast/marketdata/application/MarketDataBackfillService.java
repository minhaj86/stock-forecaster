package com.stockforecast.marketdata.application;

import com.stockforecast.marketdata.DailyBar;
import com.stockforecast.marketdata.ExistingDataStrategy;
import com.stockforecast.marketdata.MarketDataInterval;
import com.stockforecast.marketdata.application.MarketDataProvider.HistoricalPrices;
import com.stockforecast.marketdata.application.MarketDataProvider.ProviderInstrument;
import com.stockforecast.marketdata.application.MarketDataValidationService.ValidationReport;
import com.stockforecast.marketdata.domain.JobCounters;
import com.stockforecast.stock.StockRef;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Downloads a date range and merges it into the stored history (initial loads, incremental updates and backfills).
 *
 * <p>Flow: fetch (no transaction, no connection held) → validate → one short transaction that compares with the stored
 * bars, writes only new (and, with {@link ExistingDataStrategy#UPDATE}, changed) bars and records the successful
 * synchronisation. Bars that fail validation are skipped and counted; a response that fails as a whole is rejected
 * without writing anything.
 */
@Service
public class MarketDataBackfillService {

    /** Relative change of the adjusted close that indicates a new corporate-action adjustment. */
    private static final BigDecimal ADJUSTMENT_TOLERANCE = new BigDecimal("0.0001");

    private final MarketDataProvider provider;
    private final MarketDataValidationService validation;
    private final MarketDataStore store;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public MarketDataBackfillService(MarketDataProvider provider, MarketDataValidationService validation,
                                     MarketDataStore store, TransactionTemplate transactions, Clock clock) {
        this.provider = provider;
        this.validation = validation;
        this.store = store;
        this.transactions = transactions;
        this.clock = clock;
    }

    public BackfillResult backfill(StockRef stock, LocalDate from, LocalDate to, MarketDataInterval interval,
                                   ExistingDataStrategy strategy, JobProgress progress) {
        String providerName = provider.info().name();
        progress.report(10, "Fetching %s → %s from %s".formatted(from, to, providerName));
        HistoricalPrices response = provider.getHistoricalPrices(new ProviderInstrument(stock.symbol(), stock.mic()),
                from, to, interval);

        progress.report(60, "Validating %d bars".formatted(response.prices().size()));
        ValidationReport report = validation.validate(stock, from, to, interval, response);
        if (report.isRejected()) {
            throw new JobExecutionException("INVALID_PROVIDER_DATA", report.rejectionReason(),
                    new JobCounters(report.fetched(), 0, 0, 0, report.fetched(), 0));
        }
        if (report.validBars().isEmpty() && report.hasViolations()) {
            throw new JobExecutionException("INVALID_PROVIDER_DATA", report.violationSummary(),
                    new JobCounters(report.fetched(), 0, 0, 0, report.violations().size(), 0));
        }

        progress.report(80, "Storing %d validated bars".formatted(report.validBars().size()));
        String providerId = provider.info().id();
        Merge merge = transactions.execute(status -> {
            Instant now = clock.instant();
            Merge result = merge(stock.id(), report.validBars(), strategy, providerId, now);
            store.recordSuccess(stock.id(), providerId, now);
            return result;
        });
        JobCounters counters = new JobCounters(report.fetched(), merge.inserted(), merge.updated(), merge.skipped(),
                report.violations().size(), 0);
        return new BackfillResult(counters, merge.adjustmentChanged(),
                report.hasViolations() ? report.violationSummary() : null);
    }

    private Merge merge(UUID stockId, List<DailyBar> bars, ExistingDataStrategy strategy, String providerId,
                        Instant now) {
        if (bars.isEmpty()) {
            return new Merge(0, 0, 0, false);
        }
        Map<LocalDate, DailyBar> stored = store.findBars(List.of(stockId), bars.getFirst().date(),
                        bars.getLast().date()).getOrDefault(stockId, List.of()).stream()
                .collect(Collectors.toMap(DailyBar::date, Function.identity()));
        List<DailyBar> inserts = new ArrayList<>();
        List<DailyBar> changes = new ArrayList<>();
        int adjustedCloseChanges = 0;
        int sameTradePrices = 0;
        for (DailyBar bar : bars) {
            DailyBar existing = stored.get(bar.date());
            if (existing == null) {
                inserts.add(bar);
            } else if (!sameValues(existing, bar)) {
                changes.add(bar);
                if (sameTradePrices(existing, bar)) {
                    sameTradePrices++;
                    if (relativeDifference(existing.adjustedClose(), bar.adjustedClose())
                            .compareTo(ADJUSTMENT_TOLERANCE) > 0) {
                        adjustedCloseChanges++;
                    }
                }
            }
        }
        store.insertMissingBars(stockId, providerId, inserts, now);
        int updated = 0;
        if (strategy == ExistingDataStrategy.UPDATE && !changes.isEmpty()) {
            store.upsertBars(stockId, providerId, changes, now);
            updated = changes.size();
        }
        int skipped = bars.size() - inserts.size() - updated;
        // Only adjusted closes moved for dates whose trade prices are unchanged: a dividend or split was applied
        // by the provider, so older stored bars are now stale.
        boolean adjustmentChanged = adjustedCloseChanges > 0 && adjustedCloseChanges == sameTradePrices;
        return new Merge(inserts.size(), updated, skipped, adjustmentChanged);
    }

    private static boolean sameValues(DailyBar stored, DailyBar fetched) {
        return sameTradePrices(stored, fetched) && stored.adjustedClose().compareTo(fetched.adjustedClose()) == 0;
    }

    private static boolean sameTradePrices(DailyBar stored, DailyBar fetched) {
        return stored.open().compareTo(fetched.open()) == 0 && stored.high().compareTo(fetched.high()) == 0
                && stored.low().compareTo(fetched.low()) == 0 && stored.close().compareTo(fetched.close()) == 0
                && stored.volume() == fetched.volume();
    }

    private static BigDecimal relativeDifference(BigDecimal stored, BigDecimal fetched) {
        return stored.subtract(fetched).abs().divide(stored, 10, RoundingMode.HALF_UP);
    }

    private record Merge(int inserted, int updated, int skipped, boolean adjustmentChanged) {
    }

    /**
     * @param adjustmentChanged the provider changed adjusted prices of already stored dates (new dividend or split):
     *                          a repopulation refreshes the older history
     * @param validationIssues  description of rejected bars, or {@code null}
     */
    public record BackfillResult(JobCounters counters, boolean adjustmentChanged, String validationIssues) {
    }
}
