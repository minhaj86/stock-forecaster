package com.stockforecast.analytics.application;

import com.stockforecast.analytics.application.AnalyticsViews.PerformanceView;
import com.stockforecast.analytics.application.AnalyticsViews.PointView;
import com.stockforecast.analytics.application.AnalyticsViews.SeriesView;
import com.stockforecast.marketdata.DailyBar;
import com.stockforecast.marketdata.MarketDataStatus;
import com.stockforecast.marketdata.PriceHistory;
import com.stockforecast.quant.NormalizedSeries;
import com.stockforecast.quant.PriceSeries;
import com.stockforecast.shared.domain.MarketPeriod;
import com.stockforecast.stock.StockCatalog;
import com.stockforecast.stock.StockRef;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.UUID;

/**
 * Computes stock metrics from stored prices. Snapshots are cached per stock and data version (the time of the last
 * successful synchronisation), so new data invalidates them without explicit eviction.
 */
@Service
public class StockAnalyticsService {

    public static final String SNAPSHOT_CACHE = "stock-snapshot";

    static final int VOLATILITY_SHORT_DAYS = 30;
    static final int VOLATILITY_LONG_DAYS = 90;
    static final int RSI_PERIOD = 14;
    static final int MOMENTUM_DAYS = 63;
    /** Enough history for one-year metrics plus the RSI warm-up. */
    private static final int SNAPSHOT_LOOKBACK_DAYS = 400;

    private final PriceHistory priceHistory;
    private final StockCatalog stockCatalog;
    private final CacheManager cacheManager;
    private final Clock clock;

    public StockAnalyticsService(PriceHistory priceHistory, StockCatalog stockCatalog, CacheManager cacheManager,
                                 Clock clock) {
        this.priceHistory = priceHistory;
        this.stockCatalog = stockCatalog;
        this.cacheManager = cacheManager;
        this.clock = clock;
    }

    public StockSnapshot snapshot(String symbol) {
        return snapshots(List.of(stockCatalog.getBySymbol(symbol))).getFirst();
    }

    public List<StockSnapshot> snapshotsForSymbols(Collection<String> symbols) {
        return snapshots(symbols.stream().distinct().map(stockCatalog::getBySymbol).toList());
    }

    /** Snapshots in the order of {@code stocks}. */
    public List<StockSnapshot> snapshots(List<StockRef> stocks) {
        if (stocks.isEmpty()) {
            return List.of();
        }
        Map<UUID, MarketDataStatus> statuses = priceHistory.findStatuses(stocks.stream().map(StockRef::id).toList());
        Cache cache = cacheManager.getCache(SNAPSHOT_CACHE);
        Map<UUID, StockSnapshot> result = new HashMap<>();
        List<StockRef> toCompute = new ArrayList<>();
        for (StockRef stock : stocks) {
            MarketDataStatus status = statuses.get(stock.id());
            StockSnapshot cached = cache == null || status.lastSuccessAt() == null ? null
                    : cache.get(cacheKey(stock, status), StockSnapshot.class);
            if (cached != null) {
                result.put(stock.id(), cached);
            } else {
                toCompute.add(stock);
            }
        }
        if (!toCompute.isEmpty()) {
            LocalDate from = toCompute.stream().map(stock -> statuses.get(stock.id()).lastBarDate())
                    .filter(Objects::nonNull).min(Comparator.naturalOrder())
                    .orElseGet(() -> LocalDate.now(clock.withZone(ZoneOffset.UTC)))
                    .minusDays(SNAPSHOT_LOOKBACK_DAYS);
            Map<UUID, List<DailyBar>> bars = priceHistory.findDailyBars(toCompute.stream().map(StockRef::id).toList(),
                    from);
            for (StockRef stock : toCompute) {
                MarketDataStatus status = statuses.get(stock.id());
                StockSnapshot snapshot = compute(stock, status, bars.getOrDefault(stock.id(), List.of()));
                if (cache != null && snapshot.dataStatus() == DataStatus.READY) {
                    cache.put(cacheKey(stock, status), snapshot);
                }
                result.put(stock.id(), snapshot);
            }
        }
        return stocks.stream().map(stock -> result.get(stock.id())).toList();
    }

    /**
     * Performance rebased to 100 at the start of the period. All series share one as-of date (the most recent
     * bar of any stock) so the lines are comparable.
     */
    public PerformanceView performance(List<StockRef> stocks, MarketPeriod period) {
        Map<UUID, MarketDataStatus> statuses = priceHistory.findStatuses(stocks.stream().map(StockRef::id).toList());
        LocalDate asOf = statuses.values().stream().map(MarketDataStatus::lastBarDate).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        if (asOf == null) {
            return new PerformanceView(period.code(), null, List.of());
        }
        LocalDate start = period.startDate(asOf);
        // Load a little before the start so the base close (last close on or before the start) is included.
        Map<UUID, List<DailyBar>> bars = priceHistory.findDailyBars(stocks.stream().map(StockRef::id).toList(),
                start.minusDays(10));
        List<SeriesView> series = new ArrayList<>();
        for (StockRef stock : stocks) {
            List<DailyBar> stockBars = bars.getOrDefault(stock.id(), List.of());
            if (stockBars.isEmpty()) {
                continue;
            }
            NormalizedSeries normalized = toSeries(stockBars).normalizedSince(start, period.prefersWeeklyPoints());
            series.add(new SeriesView(stock.symbol(), stock.companyName(), normalized.baseDate(), normalized.partial(),
                    round(normalized.totalReturn(), 6),
                    normalized.points().stream().map(point -> new PointView(point.date(), round(point.value(), 4)))
                            .toList()));
        }
        return new PerformanceView(period.code(), asOf, series);
    }

    private StockSnapshot compute(StockRef stock, MarketDataStatus status, List<DailyBar> bars) {
        String syncIssue = syncIssue(status);
        if (bars.isEmpty()) {
            DataStatus dataStatus = status.state() == MarketDataStatus.State.READY ? DataStatus.PENDING
                    : DataStatus.from(status.state());
            return new StockSnapshot(stock.symbol(), stock.companyName(), stock.exchange(), stock.currency(),
                    stock.sector(), dataStatus, status.provider(), status.lastBarDate(), status.lastSuccessAt(),
                    syncIssue, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                    null);
        }
        PriceSeries series = toSeries(bars);
        LocalDate asOf = series.lastDate();
        DailyBar last = bars.getLast();
        DailyBar previous = bars.size() > 1 ? bars.get(bars.size() - 2) : null;
        return new StockSnapshot(stock.symbol(), stock.companyName(), stock.exchange(), stock.currency(),
                stock.sector(), DataStatus.READY, status.provider(), asOf, status.lastSuccessAt(), syncIssue,
                last.close(), previous == null ? null : previous.close(),
                previous == null ? null : last.close().subtract(previous.close()),
                ratio(series.dailyChange()),
                ratio(series.returnSince(MarketPeriod.ONE_WEEK.startDate(asOf))),
                ratio(series.returnSince(MarketPeriod.ONE_MONTH.startDate(asOf))),
                ratio(series.returnSince(MarketPeriod.THREE_MONTHS.startDate(asOf))),
                ratio(series.returnSince(MarketPeriod.SIX_MONTHS.startDate(asOf))),
                ratio(series.returnSince(MarketPeriod.YEAR_TO_DATE.startDate(asOf))),
                ratio(series.returnSince(MarketPeriod.ONE_YEAR.startDate(asOf))),
                ratio(series.annualizedVolatility(VOLATILITY_SHORT_DAYS)),
                ratio(series.annualizedVolatility(VOLATILITY_LONG_DAYS)),
                ratio(series.maxDrawdownSince(MarketPeriod.ONE_YEAR.startDate(asOf))),
                scaled(series.rsi(RSI_PERIOD), 2),
                ratio(series.rateOfChange(MOMENTUM_DAYS)));
    }

    /** Reported only when the latest attempt failed after the last success, i.e. the data may be stale. */
    private static String syncIssue(MarketDataStatus status) {
        if (status.lastError() == null) {
            return null;
        }
        boolean failedAfterSuccess = status.lastSuccessAt() == null || (status.lastAttemptAt() != null
                && status.lastAttemptAt().isAfter(status.lastSuccessAt()));
        return failedAfterSuccess ? status.lastError() : null;
    }

    private static PriceSeries toSeries(List<DailyBar> bars) {
        double[] closes = new double[bars.size()];
        for (int i = 0; i < closes.length; i++) {
            closes[i] = bars.get(i).adjustedClose().doubleValue();
        }
        return PriceSeries.of(bars.stream().map(DailyBar::date).toList(), closes);
    }

    private static String cacheKey(StockRef stock, MarketDataStatus status) {
        Instant version = status.lastSuccessAt();
        return stock.id() + ":" + version.toEpochMilli();
    }

    private static Double ratio(OptionalDouble value) {
        return scaled(value, 6);
    }

    private static Double scaled(OptionalDouble value, int scale) {
        return value.isPresent() ? round(value.getAsDouble(), scale) : null;
    }

    static double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
