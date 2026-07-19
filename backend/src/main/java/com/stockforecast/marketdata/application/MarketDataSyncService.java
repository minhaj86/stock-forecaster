package com.stockforecast.marketdata.application;

import com.stockforecast.marketdata.DailyBar;
import com.stockforecast.marketdata.application.MarketDataStore.SyncClaim;
import com.stockforecast.stock.StockCatalog;
import com.stockforecast.stock.StockRef;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Synchronises stored bars with the provider.
 *
 * <p>Flow per stock: claim a lease (short transaction) → call the provider (no transaction, no DB connection
 * held) → upsert bars and record success (short transaction). Re-running is harmless: bars are upserted by
 * natural key and a trailing window is always re-fetched. Failures back off exponentially per stock.
 */
@Service
public class MarketDataSyncService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataSyncService.class);
    private static final Duration MAX_BACKOFF = Duration.ofHours(6);

    private final MarketDataStore store;
    private final MarketDataProvider provider;
    private final StockCatalog stockCatalog;
    private final MarketDataProperties properties;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    /** Epoch seconds of the last successful synchronisation, exported as a gauge. */
    private final AtomicLong lastSuccessEpochSeconds = new AtomicLong();

    public MarketDataSyncService(MarketDataStore store, ObjectProvider<MarketDataProvider> provider,
                                 StockCatalog stockCatalog, MarketDataProperties properties,
                                 TransactionTemplate transactions, Clock clock, MeterRegistry meterRegistry) {
        this.store = store;
        this.provider = provider.getIfAvailable(() -> {
            throw new IllegalStateException("No market-data provider available for app.market-data.provider='"
                    + properties.provider() + "' (MARKET_DATA_PROVIDER). Supported: synthetic");
        });
        this.stockCatalog = stockCatalog;
        this.properties = properties;
        this.transactions = transactions;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        meterRegistry.gauge("market_data_last_success", lastSuccessEpochSeconds);
    }

    public SyncOutcome syncStock(UUID stockId) {
        return syncStock(stockId, false);
    }

    /**
     * @param ignoreBackoff {@code true} for explicit user-triggered refreshes
     */
    public SyncOutcome syncStock(UUID stockId, boolean ignoreBackoff) {
        Optional<StockRef> stock = stockCatalog.findById(stockId);
        if (stock.isEmpty()) {
            return SyncOutcome.SKIPPED;
        }
        Instant now = clock.instant();
        Optional<SyncClaim> claim = store.tryClaim(stockId, now, now.minus(properties.leaseTimeout()), ignoreBackoff);
        if (claim.isEmpty()) {
            return SyncOutcome.SKIPPED;
        }
        return synchronise(stock.get(), claim.get());
    }

    public int syncAll(Collection<UUID> stockIds) {
        int updated = 0;
        for (UUID stockId : stockIds) {
            if (syncStock(stockId) == SyncOutcome.UPDATED) {
                updated++;
            }
        }
        return updated;
    }

    private SyncOutcome synchronise(StockRef stock, SyncClaim claim) {
        String providerId = provider.info().id();
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        LocalDate from = claim.lastBarDate() == null
                ? today.minusYears(properties.historyYears())
                : claim.lastBarDate().minusDays(properties.refreshOverlapDays());
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            List<DailyBar> bars = provider.getHistoricalPrices(stock.symbol(), from, today);
            Instant completedAt = clock.instant();
            LocalDate lastBarDate = bars.stream().map(DailyBar::date).max(Comparator.naturalOrder())
                    .orElse(claim.lastBarDate());
            transactions.executeWithoutResult(status -> {
                store.upsertBars(stock.id(), providerId, bars, completedAt);
                store.recordSuccess(stock.id(), providerId, lastBarDate, completedAt);
            });
            meterRegistry.counter("market_data_requests", "provider", providerId, "outcome", "success").increment();
            lastSuccessEpochSeconds.set(completedAt.getEpochSecond());
            log.debug("Synchronised {} bars for {} from {} ({})", bars.size(), stock.symbol(), from, providerId);
            return SyncOutcome.UPDATED;
        } catch (MarketDataProviderException e) {
            Instant failedAt = clock.instant();
            Duration backoff = backoff(claim.consecutiveFailures() + 1);
            store.recordFailure(stock.id(), "Market-data provider error: " + e.getMessage(), failedAt,
                    failedAt.plus(backoff));
            meterRegistry.counter("market_data_failures", "provider", providerId,
                    "retryable", Boolean.toString(e.isRetryable())).increment();
            log.warn("Market-data sync failed for {} via {} (retry in {}): {}", stock.symbol(), providerId, backoff,
                    e.getMessage());
            return SyncOutcome.FAILED;
        } finally {
            sample.stop(meterRegistry.timer("market_data_sync_duration", "provider", providerId));
        }
    }

    static Duration backoff(int consecutiveFailures) {
        long minutes = 1L << Math.min(consecutiveFailures, 10);
        Duration backoff = Duration.ofMinutes(minutes);
        return backoff.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff;
    }

    public enum SyncOutcome {
        UPDATED,
        SKIPPED,
        FAILED
    }
}
