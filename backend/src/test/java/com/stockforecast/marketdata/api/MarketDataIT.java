package com.stockforecast.marketdata.api;

import com.stockforecast.marketdata.application.MarketDataSyncService;
import com.stockforecast.marketdata.application.MarketDataSyncService.SyncOutcome;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
    private MarketDataSyncService syncService;
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

        // Immediately after the add returns (the backfill may not have started yet) the stock is never "not tracked".
        mockMvc.perform(get("/api/v1/stocks/PANW/prices").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(jsonPath("$.dataStatus", anyOf(is("PENDING"), is("READY"))));
    }

    @Test
    void synchronisationIsIdempotent() throws Exception {
        watchlists.addStock(session, watchlists.defaultWatchlistId(session), "INTU");
        watchlists.awaitMarketData(session, "INTU");
        UUID stockId = stockCatalog.getBySymbol("INTU").id();
        Integer before = countBars(stockId);

        assertThat(syncService.syncStock(stockId, true)).isEqualTo(SyncOutcome.UPDATED);
        assertThat(syncService.syncStock(stockId, true)).isEqualTo(SyncOutcome.UPDATED);

        assertThat(countBars(stockId)).isEqualTo(before).isGreaterThan(1200);
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
}
