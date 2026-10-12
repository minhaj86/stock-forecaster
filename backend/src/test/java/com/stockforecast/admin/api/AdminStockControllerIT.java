package com.stockforecast.admin.api;

import com.jayway.jsonpath.JsonPath;
import com.stockforecast.support.AdminTestClient;
import com.stockforecast.support.AuthTestClient;
import com.stockforecast.support.AuthTestClient.Session;
import com.stockforecast.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AdminStockControllerIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthTestClient auth;
    @Autowired
    private AdminTestClient admins;

    private Session admin;

    @BeforeEach
    void signInAsAdministrator() throws Exception {
        admin = admins.newAdmin();
    }

    @Test
    void listsTheCatalogueWithMarketDataCoverage() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stocks").param("query", "apple")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.content[0].active").value(true))
                .andExpect(jsonPath("$.content[0].source").value("CATALOGUE"))
                .andExpect(jsonPath("$.content[0].marketData.dataStatus").isString())
                .andExpect(jsonPath("$.content[0].marketData.barCount").isNumber());
        mockMvc.perform(get("/api/v1/admin/stocks").param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$.page.totalElements", greaterThan(60)));
        mockMvc.perform(get("/api/v1/admin/stocks/NOPE").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STOCK_NOT_FOUND"));
    }

    @Test
    void theSyntheticProviderCannotSearchOrDescribeStocksSoTheyAreEnteredManually() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stocks/provider-search").param("query", "Microsoft")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider.id").value("synthetic"))
                .andExpect(jsonPath("$.supported").value(false))
                .andExpect(jsonPath("$.results").isEmpty());
        mockMvc.perform(post("/api/v1/admin/stocks").header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"symbol\": \"%s\"}"
                                .formatted(AdminTestClient.uniqueSymbol())))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("PROVIDER_METADATA_UNAVAILABLE"));
    }

    @Test
    void registersStocksManuallyAndLoadsTheirHistoryInTheBackground() throws Exception {
        String symbol = AdminTestClient.uniqueSymbol();

        String body = mockMvc.perform(post("/api/v1/admin/stocks").header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"symbol": "%s", "loadHistory": true,
                                 "manual": {"companyName": "Volvo B", "exchange": "OMX", "mic": "xsto",
                                            "currency": "sek", "country": "se", "assetType": "COMMON_STOCK"}}"""
                                .formatted(symbol.toLowerCase())))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/admin/stocks/" + symbol))
                .andExpect(jsonPath("$.stock.symbol").value(symbol))
                .andExpect(jsonPath("$.stock.mic").value("XSTO"))
                .andExpect(jsonPath("$.stock.currency").value("SEK"))
                .andExpect(jsonPath("$.stock.source").value("MANUAL"))
                .andExpect(jsonPath("$.job.type").value("INITIAL_LOAD"))
                .andExpect(jsonPath("$.job.trigger").value("ADMIN"))
                .andExpect(jsonPath("$.job.requestedBy.id").value(admin.userId().toString()))
                .andReturn().getResponse().getContentAsString();

        admins.awaitJob(admin, UUID.fromString(JsonPath.read(body, "$.job.id")), "COMPLETED");
        mockMvc.perform(get("/api/v1/admin/stocks/{symbol}", symbol).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$.marketData.dataStatus").value("READY"))
                .andExpect(jsonPath("$.marketData.barCount", greaterThan(1200)))
                .andExpect(jsonPath("$.marketData.firstBarDate").isString())
                .andExpect(jsonPath("$.marketData.activeJob").isEmpty());

        Session user = auth.registerNewUser();
        mockMvc.perform(get("/api/v1/stocks/search").param("q", symbol).header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(jsonPath("$[0].symbol").value(symbol));
        mockMvc.perform(get("/api/v1/admin/audit-log").param("action", "STOCK_CREATED").param("entityId", symbol)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$.content[0].metadata.currency").value("SEK"))
                .andExpect(jsonPath("$.content[0].metadata.initialLoadJobId").isString());
    }

    @Test
    void rejectsDuplicateAndInvalidStocks() throws Exception {
        mockMvc.perform(post("/api/v1/admin/stocks").header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"symbol": "aapl", "manual": {"companyName": "Apple", "exchange": "NASDAQ",
                                 "mic": "XNAS", "currency": "USD", "country": "US", "assetType": "COMMON_STOCK"}}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCK_ALREADY_EXISTS"));
        mockMvc.perform(post("/api/v1/admin/stocks").header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"symbol": "%s", "manual": {"companyName": "Bad", "exchange": "NASDAQ",
                                 "mic": "XN", "currency": "USD", "country": "US", "assetType": "COMMON_STOCK"}}"""
                                .formatted(AdminTestClient.uniqueSymbol())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("manual.mic")));
    }

    @Test
    void deactivatedStocksAreHiddenFromUsersAndNotSynchronised() throws Exception {
        String symbol = admins.registerStock(admin);
        Session user = auth.registerNewUser();

        mockMvc.perform(post("/api/v1/admin/stocks/{symbol}/deactivate", symbol)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/v1/stocks/{symbol}", symbol).header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/admin/stocks/{symbol}", symbol).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(post("/api/v1/admin/stocks/{symbol}/market-data/update", symbol)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCK_INACTIVE"));

        mockMvc.perform(post("/api/v1/admin/stocks/{symbol}/activate", symbol)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$.active").value(true));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/stocks/{symbol}", symbol).header(HttpHeaders.AUTHORIZATION, user.bearer()))
                        .andExpect(status().isOk()));
        mockMvc.perform(get("/api/v1/admin/audit-log").param("entityId", symbol)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$.content[0].action").value("STOCK_ENABLED"))
                .andExpect(jsonPath("$.content[1].action").value("STOCK_DISABLED"));
    }
}
