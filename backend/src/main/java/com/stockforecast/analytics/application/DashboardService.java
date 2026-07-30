package com.stockforecast.analytics.application;

import com.stockforecast.analytics.application.AnalyticsViews.DashboardView;
import com.stockforecast.analytics.application.AnalyticsViews.MoverView;
import com.stockforecast.analytics.application.AnalyticsViews.MoversView;
import com.stockforecast.analytics.application.AnalyticsViews.PerformanceView;
import com.stockforecast.analytics.application.AnalyticsViews.SeriesView;
import com.stockforecast.shared.domain.MarketPeriod;
import com.stockforecast.stock.StockCatalog;
import com.stockforecast.stock.StockRef;
import com.stockforecast.watchlist.WatchlistQueries;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/**
 * The personalised dashboard: the user's watched stocks (all watchlists, or one), their metrics, the movers of the
 * selected period and the normalised performance chart. All figures come from {@link StockAnalyticsService}.
 */
@Service
public class DashboardService {

    /** Upper bound of stocks on one dashboard; keeps response size and chart readability in check. */
    public static final int MAX_STOCKS = 50;

    private final WatchlistQueries watchlists;
    private final StockCatalog stockCatalog;
    private final StockAnalyticsService analytics;
    private final Clock clock;

    public DashboardService(WatchlistQueries watchlists, StockCatalog stockCatalog, StockAnalyticsService analytics,
                            Clock clock) {
        this.watchlists = watchlists;
        this.stockCatalog = stockCatalog;
        this.analytics = analytics;
        this.clock = clock;
    }

    public DashboardView dashboard(UUID userId, MarketPeriod period, Optional<UUID> watchlistId) {
        List<UUID> stockIds = watchlistId
                .map(id -> watchlists.findStockIds(userId, id).orElseThrow(WatchlistNotFoundForDashboardException::new))
                .orElseGet(() -> watchlists.findWatchedStockIds(userId));
        List<StockRef> stocks = stockCatalog.findAllByIds(stockIds).stream()
                .sorted(Comparator.comparing(StockRef::symbol)).toList();
        boolean truncated = stocks.size() > MAX_STOCKS;
        List<StockRef> included = truncated ? stocks.subList(0, MAX_STOCKS) : stocks;

        // Every stock here is watched, so "not tracked" only means its first synchronisation has not started yet.
        List<StockSnapshot> snapshots = analytics.snapshots(included).stream()
                .map(snapshot -> snapshot.dataStatus() == DataStatus.NOT_TRACKED
                        ? snapshot.withStatus(DataStatus.PENDING) : snapshot)
                .toList();
        PerformanceView performance = analytics.performance(included, period);

        return new DashboardView(clock.instant(), period.code(), watchlists.countWatchlists(userId), stocks.size(),
                truncated,
                snapshots.stream().map(StockSnapshot::asOf).filter(Objects::nonNull)
                        .max(Comparator.naturalOrder()).orElse(null),
                snapshots.stream().map(StockSnapshot::lastSyncedAt).filter(Objects::nonNull)
                        .max(Comparator.naturalOrder()).orElse(null),
                snapshots.stream().map(StockSnapshot::dataProvider).filter(Objects::nonNull).distinct().sorted()
                        .toList(),
                snapshots, movers(snapshots, performance), performance);
    }

    private static MoversView movers(List<StockSnapshot> snapshots, PerformanceView performance) {
        Map<String, StockSnapshot> bySymbol = snapshots.stream()
                .collect(Collectors.toMap(StockSnapshot::symbol, Function.identity()));
        List<SeriesView> fullPeriod = performance.series().stream().filter(series -> !series.partial()).toList();
        return new MoversView(
                best(fullPeriod, SeriesView::periodReturn, bySymbol, true),
                best(fullPeriod, SeriesView::periodReturn, bySymbol, false),
                bestSnapshot(snapshots, StockSnapshot::volatility30d, true),
                bestSnapshot(snapshots, StockSnapshot::volatility30d, false),
                bestSnapshot(snapshots, StockSnapshot::momentum3M, true));
    }

    private static MoverView best(List<SeriesView> series, ToDoubleFunction<SeriesView> metric,
                                  Map<String, StockSnapshot> bySymbol, boolean highest) {
        Comparator<SeriesView> order = Comparator.comparingDouble(metric);
        return series.stream().max(highest ? order : order.reversed())
                .map(winner -> new MoverView(winner.symbol(), bySymbol.get(winner.symbol()).companyName(),
                        metric.applyAsDouble(winner)))
                .orElse(null);
    }

    private static MoverView bestSnapshot(List<StockSnapshot> snapshots, Function<StockSnapshot, Double> metric,
                                          boolean highest) {
        Comparator<StockSnapshot> order = Comparator.comparingDouble(snapshot -> metric.apply(snapshot));
        return snapshots.stream().filter(snapshot -> metric.apply(snapshot) != null)
                .max(highest ? order : order.reversed())
                .map(winner -> new MoverView(winner.symbol(), winner.companyName(), metric.apply(winner)))
                .orElse(null);
    }
}
