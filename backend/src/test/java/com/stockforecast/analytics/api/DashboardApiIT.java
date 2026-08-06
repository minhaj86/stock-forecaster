package com.stockforecast.analytics.api;

import com.stockforecast.analytics.application.StockAnalyticsService;
import com.stockforecast.analytics.application.StockSnapshot;
import com.stockforecast.marketdata.PriceHistory;
import com.stockforecast.stock.StockCatalog;
import com.stockforecast.stock.StockRef;
import com.stockforecast.support.AuthTestClient;
import com.stockforecast.support.AuthTestClient.Session;
import com.stockforecast.support.IntegrationTest;
import com.stockforecast.support.WatchlistTestClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class DashboardApiIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthTestClient auth;
    @Autowired
    private WatchlistTestClient watchlists;
    @Autowired
    private StockCatalog stockCatalog;
    @Autowired
    private PriceHistory priceHistory;
    @Autowired
    private CacheManager cacheManager;

    @Test
    void newUsersSeeAnEmptyDashboard() throws Exception {
        Session session = auth.registerNewUser();

        mockMvc.perform(get("/api/v1/dashboard").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.watchlistCount").value(1))
                .andExpect(jsonPath("$.stockCount").value(0))
                .andExpect(jsonPath("$.stocks", hasSize(0)))
                .andExpect(jsonPath("$.movers.topPerformer").doesNotExist())
                .andExpect(jsonPath("$.performance.series", hasSize(0)));
    }

    @Test
    void summarisesTheWatchedStocksWithMetricsMoversAndNormalisedPerformance() throws Exception {
        Session session = auth.registerNewUser();
        UUID main = watchlists.defaultWatchlistId(session);
        for (String symbol : List.of("NVDA", "AAPL", "KO")) {
            watchlists.addStock(session, main, symbol);
        }
        for (String symbol : List.of("NVDA", "AAPL", "KO")) {
            watchlists.awaitMarketData(session, symbol);
        }

        mockMvc.perform(get("/api/v1/dashboard").param("period", "6M")
                        .header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("6M"))
                .andExpect(jsonPath("$.stockCount").value(3))
                .andExpect(jsonPath("$.dataProviders", contains("synthetic")))
                .andExpect(jsonPath("$.dataAsOf", notNullValue()))
                .andExpect(jsonPath("$.stocks[*].symbol", contains("AAPL", "KO", "NVDA")))
                .andExpect(jsonPath("$.stocks[*].dataStatus", everyItem(is("READY"))))
                .andExpect(jsonPath("$.stocks[0].price", greaterThan(0.0)))
                .andExpect(jsonPath("$.stocks[0].changePercent").isNumber())
                .andExpect(jsonPath("$.stocks[0].return1M").isNumber())
                .andExpect(jsonPath("$.stocks[0].volatility30d", greaterThan(0.0)))
                .andExpect(jsonPath("$.stocks[0].maxDrawdown1Y", lessThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.movers.topPerformer.symbol").isString())
                .andExpect(jsonPath("$.movers.weakestPerformer.symbol").isString())
                .andExpect(jsonPath("$.movers.highestVolatility.value", greaterThan(0.0)))
                .andExpect(jsonPath("$.movers.strongestMomentum.symbol").isString())
                .andExpect(jsonPath("$.performance.series[*].symbol", containsInAnyOrder("AAPL", "KO", "NVDA")))
                .andExpect(jsonPath("$.performance.series[0].points[0].value").value(100.0))
                .andExpect(jsonPath("$.performance.series[0].points.length()", greaterThan(100)));
    }

    @Test
    void canBeScopedToOneOwnWatchlistOnly() throws Exception {
        Session alice = auth.registerNewUser();
        UUID tech = watchlists.createWatchlist(alice, "Tech");
        watchlists.addStock(alice, tech, "MSFT");
        watchlists.addStock(alice, watchlists.defaultWatchlistId(alice), "PEP");

        mockMvc.perform(get("/api/v1/dashboard").param("watchlistId", tech.toString())
                        .header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stocks[*].symbol", contains("MSFT")));

        Session bob = auth.registerNewUser();
        mockMvc.perform(get("/api/v1/dashboard").param("watchlistId", tech.toString())
                        .header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WATCHLIST_NOT_FOUND"));
    }

    @Test
    void stockAnalyticsAndPerformanceEndpointsServeAnyTrackedStock() throws Exception {
        Session session = auth.registerNewUser();
        watchlists.addStock(session, watchlists.defaultWatchlistId(session), "QCOM");
        watchlists.awaitMarketData(session, "QCOM");

        mockMvc.perform(get("/api/v1/stocks/QCOM/analytics").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("QCOM"))
                .andExpect(jsonPath("$.rsi14").isNumber())
                .andExpect(jsonPath("$.returnYtd").isNumber());
        mockMvc.perform(get("/api/v1/analytics/snapshots").param("symbols", "QCOM,IBM")
                        .header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(jsonPath("$[*].symbol", contains("QCOM", "IBM")))
                .andExpect(jsonPath("$[1].dataStatus").value("NOT_TRACKED"))
                .andExpect(jsonPath("$[1].price").doesNotExist());
        mockMvc.perform(get("/api/v1/analytics/performance").param("symbols", "QCOM").param("period", "5Y")
                        .header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(jsonPath("$.period").value("5Y"))
                .andExpect(jsonPath("$.series[0].points[0].value").value(100.0))
                // Five years are plotted with weekly points.
                .andExpect(jsonPath("$.series[0].points.length()", lessThanOrEqualTo(270)));
    }

    @Test
    void snapshotsAreCachedInRedisPerDataVersion() throws Exception {
        Session session = auth.registerNewUser();
        watchlists.addStock(session, watchlists.defaultWatchlistId(session), "CSCO");
        watchlists.awaitMarketData(session, "CSCO");
        mockMvc.perform(get("/api/v1/stocks/CSCO/analytics").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isOk());

        StockRef csco = stockCatalog.getBySymbol("CSCO");
        Instant version = priceHistory.findStatuses(List.of(csco.id())).get(csco.id()).lastSuccessAt();
        StockSnapshot cached = cacheManager.getCache(StockAnalyticsService.SNAPSHOT_CACHE)
                .get(csco.id() + ":" + version.toEpochMilli(), StockSnapshot.class);

        assertThat(cached).isNotNull();
        assertThat(cached.symbol()).isEqualTo("CSCO");
        assertThat(cached.price()).isPositive();
    }

    @Test
    void validatesParameters() throws Exception {
        Session session = auth.registerNewUser();

        mockMvc.perform(get("/api/v1/dashboard").param("period", "3W").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/analytics/snapshots").param("symbols", "")
                        .header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isUnauthorized());
    }
}
