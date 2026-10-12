package com.stockforecast.marketdata.api;

import com.stockforecast.marketdata.application.MarketDataJobService;
import com.stockforecast.stock.StockCatalog;
import com.stockforecast.support.AuthTestClient;
import com.stockforecast.support.AuthTestClient.Session;
import com.stockforecast.support.IntegrationTest;
import com.stockforecast.support.WatchlistTestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class MarketDataIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthTestClient auth;
    @Autowired
    private WatchlistTestClient watchlists;
    @Autowired
    private MarketDataJobService jobService;
    @Autowired
    private StockCatalog stockCatalog;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Session session;

    @BeforeEach
    void signIn() throws Exception {
        session = auth.registerNewUser();
    }

    @Test
    void watchingAStockBackfillsItsHistoryInTheBackground() throws Exception {
        watchlists.addStock(session, watchlists.defaultWatchlistId(session), "ADBE");

        watchlists.awaitMarketData(session, "ADBE");

        mockMvc.perform(get("/api/v1/stocks/ADBE/prices").param("period", "1Y")
                        .header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("ADBE"))
                .andExpect(jsonPath("$.dataStatus").value("READY"))
                .andExpect(jsonPath("$.dataProvider").value("synthetic"))
                .andExpect(jsonPath("$.interval").value("1d"))
                .andExpect(jsonPath("$.bars.length()", greaterThan(240)))
                .andExpect(jsonPath("$.bars[0].open").isNumber())
                .andExpect(jsonPath("$.bars[0].volume").isNumber());
    }

    @Test
    void aStockIsTrackedTheMomentItIsWatched() throws Exception {
        watchlists.addStock(session, watchlists.defaultWatchlistId(session), "PANW");

        // Immediately after the add returns (the job may not have started yet) the stock is never "not tracked".
        mockMvc.perform(get("/api/v1/stocks/PANW/prices").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(jsonPath("$.dataStatus", anyOf(is("PENDING"), is("READY"))));
    }

    @Test
    void watchingQueuesAnInitialLoadAndScheduledSynchronisationIsIdempotent() throws Exception {
        watchlists.addStock(session, watchlists.defaultWatchlistId(session), "INTU");
        watchlists.awaitMarketData(session, "INTU");
        UUID stockId = stockCatalog.getBySymbol("INTU").id();
        assertThat(countJobs(stockId, "INITIAL_LOAD", "WATCHLIST")).isEqualTo(1);
        Integer before = countBars(stockId);

        for (int run = 1; run <= 2; run++) {
            jobService.queueScheduledSynchronisation();
            int expected = run;
            await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).untilAsserted(() ->
                    assertThat(countJobs(stockId, "AUTOMATIC_SYNC", "SCHEDULE")).isEqualTo(expected));
        }

        assertThat(countBars(stockId)).isEqualTo(before).isGreaterThan(1200);
    }

    @Test
    void watchingAStockWhoseDataIsCurrentQueuesNoFurtherJob() throws Exception {
        watchlists.addStock(session, watchlists.defaultWatchlistId(session), "CRWD");
        watchlists.awaitMarketData(session, "CRWD");
        UUID stockId = stockCatalog.getBySymbol("CRWD").id();
        Session other = auth.registerNewUser();

        watchlists.addStock(other, watchlists.defaultWatchlistId(other), "CRWD");

        assertThat(jdbcTemplate.queryForObject("select count(*) from market_data_sync_jobs where stock_id = ?",
                Integer.class, stockId)).isEqualTo(1);
    }

    @Test
    void unwatchedStocksHaveNoDataAndSayWhy() throws Exception {
        mockMvc.perform(get("/api/v1/stocks/IBM/prices").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataStatus").value("NOT_TRACKED"))
                .andExpect(jsonPath("$.bars", hasSize(0)));
    }

    @Test
    void rejectsUnknownPeriods() throws Exception {
        mockMvc.perform(get("/api/v1/stocks/AAPL/prices").param("period", "2Y")
                        .header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void exposesTheActiveProviderSoClientsCanLabelSimulatedData() throws Exception {
        mockMvc.perform(get("/api/v1/market-data/provider").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("synthetic"))
                .andExpect(jsonPath("$.synthetic").value(true));
    }

    private Integer countBars(UUID stockId) {
        return jdbcTemplate.queryForObject("select count(*) from market_prices where stock_id = ?", Integer.class,
                stockId);
    }

    private Integer countJobs(UUID stockId, String type, String trigger) {
        return jdbcTemplate.queryForObject("""
                select count(*) from market_data_sync_jobs
                 where stock_id = ? and job_type = ? and trigger_source = ? and status = 'COMPLETED'""",
                Integer.class, stockId, type, trigger);
    }
}
