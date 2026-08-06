package com.stockforecast.analytics.application;

import com.stockforecast.analytics.application.AnalyticsViews.DashboardView;
import com.stockforecast.analytics.application.AnalyticsViews.PerformanceView;
import com.stockforecast.analytics.application.AnalyticsViews.PointView;
import com.stockforecast.analytics.application.AnalyticsViews.SeriesView;
import com.stockforecast.shared.domain.MarketPeriod;
import com.stockforecast.stock.AssetType;
import com.stockforecast.stock.StockCatalog;
import com.stockforecast.stock.StockRef;
import com.stockforecast.watchlist.WatchlistQueries;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    private static final LocalDate AS_OF = LocalDate.of(2026, 10, 9);
    private static final UUID USER = UUID.randomUUID();

    @Mock
    private WatchlistQueries watchlists;
    @Mock
    private StockCatalog stockCatalog;
    @Mock
    private StockAnalyticsService analytics;

    private final StockRef aapl = stock("AAPL");
    private final StockRef msft = stock("MSFT");
    private final StockRef nvda = stock("NVDA");
    private final StockRef pltr = stock("PLTR");

    @Test
    void picksMoversFromPeriodPerformanceAndRiskMetrics() {
        when(watchlists.findWatchedStockIds(USER)).thenReturn(List.of(nvda.id(), aapl.id(), msft.id(), pltr.id()));
        when(watchlists.countWatchlists(USER)).thenReturn(2);
        when(stockCatalog.findAllByIds(anyList())).thenReturn(List.of(nvda, aapl, msft, pltr));
        when(analytics.snapshots(List.of(aapl, msft, nvda, pltr))).thenReturn(List.of(
                snapshot("AAPL", 0.21, 0.04), snapshot("MSFT", 0.18, 0.09), snapshot("NVDA", 0.42, 0.15),
                snapshot("PLTR", null, null).withStatus(DataStatus.NOT_TRACKED)));
        when(analytics.performance(List.of(aapl, msft, nvda, pltr), MarketPeriod.ONE_MONTH))
                .thenReturn(new PerformanceView("1M", AS_OF, List.of(
                        series("AAPL", 0.05, false), series("MSFT", 0.12, false), series("NVDA", -0.07, false),
                        series("PLTR", 0.50, true))));

        DashboardView dashboard = service().dashboard(USER, MarketPeriod.ONE_MONTH, Optional.empty());

        assertThat(dashboard.stocks()).extracting(StockSnapshot::symbol).containsExactly("AAPL", "MSFT", "NVDA", "PLTR");
        assertThat(dashboard.watchlistCount()).isEqualTo(2);
        assertThat(dashboard.stockCount()).isEqualTo(4);
        assertThat(dashboard.dataAsOf()).isEqualTo(AS_OF);
        // A stock whose history is shorter than the period (PLTR, partial) is not ranked on period performance.
        assertThat(dashboard.movers().topPerformer().symbol()).isEqualTo("MSFT");
        assertThat(dashboard.movers().weakestPerformer().symbol()).isEqualTo("NVDA");
        assertThat(dashboard.movers().weakestPerformer().value()).isEqualTo(-0.07);
        assertThat(dashboard.movers().highestVolatility().symbol()).isEqualTo("NVDA");
        assertThat(dashboard.movers().lowestVolatility().symbol()).isEqualTo("MSFT");
        assertThat(dashboard.movers().strongestMomentum().symbol()).isEqualTo("NVDA");
        // Watched stocks without data are reported as pending, never as "not tracked".
        assertThat(dashboard.stocks().getLast().dataStatus()).isEqualTo(DataStatus.PENDING);
    }

    @Test
    void anEmptyWatchlistYieldsAnEmptyDashboard() {
        when(watchlists.findWatchedStockIds(USER)).thenReturn(List.of());
        when(stockCatalog.findAllByIds(List.of())).thenReturn(List.of());
        when(analytics.snapshots(List.of())).thenReturn(List.of());
        when(analytics.performance(List.of(), MarketPeriod.ONE_YEAR))
                .thenReturn(new PerformanceView("1Y", null, List.of()));

        DashboardView dashboard = service().dashboard(USER, MarketPeriod.ONE_YEAR, Optional.empty());

        assertThat(dashboard.stockCount()).isZero();
        assertThat(dashboard.movers().topPerformer()).isNull();
        assertThat(dashboard.movers().highestVolatility()).isNull();
        assertThat(dashboard.dataAsOf()).isNull();
    }

    @Test
    void aWatchlistOfAnotherUserIsNotFound() {
        UUID foreign = UUID.randomUUID();
        when(watchlists.findStockIds(eq(USER), eq(foreign))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().dashboard(USER, MarketPeriod.ONE_MONTH, Optional.of(foreign)))
                .isInstanceOf(WatchlistNotFoundForDashboardException.class)
                .extracting("code").isEqualTo("WATCHLIST_NOT_FOUND");
    }

    private DashboardService service() {
        return new DashboardService(watchlists, stockCatalog, analytics, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static StockRef stock(String symbol) {
        return new StockRef(UUID.randomUUID(), symbol, symbol + " Inc.", "NASDAQ", "XNAS", "USD", "US",
                "Information Technology", "Software", AssetType.COMMON_STOCK);
    }

    private static StockSnapshot snapshot(String symbol, Double volatility, Double momentum) {
        boolean ready = volatility != null;
        return new StockSnapshot(symbol, symbol + " Inc.", "NASDAQ", "USD", "Information Technology",
                ready ? DataStatus.READY : DataStatus.PENDING, "synthetic", ready ? AS_OF : null,
                ready ? NOW : null, null, ready ? new BigDecimal("100.00") : null, null, null, null, null, null, null,
                null, null, null, volatility, null, null, null, momentum);
    }

    private static SeriesView series(String symbol, double periodReturn, boolean partial) {
        return new SeriesView(symbol, symbol + " Inc.", AS_OF.minusMonths(1), partial, periodReturn,
                List.of(new PointView(AS_OF.minusMonths(1), 100), new PointView(AS_OF, 100 * (1 + periodReturn))));
    }
}
