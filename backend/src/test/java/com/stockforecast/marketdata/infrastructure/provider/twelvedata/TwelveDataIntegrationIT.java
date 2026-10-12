package com.stockforecast.marketdata.infrastructure.provider.twelvedata;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.jayway.jsonpath.JsonPath;
import com.stockforecast.support.AdminTestClient;
import com.stockforecast.support.AuthTestClient.Session;
import com.stockforecast.support.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole stack (admin API → jobs → worker → Twelve Data adapter → PostgreSQL) against a stubbed Twelve Data API, in
 * its own application context with {@code MARKET_DATA_PROVIDER=TWELVE_DATA}. Never calls the real vendor.
 */
@IntegrationTest
@TestPropertySource(properties = {
        "app.market-data.provider=TWELVE_DATA",
        "app.market-data.twelve-data.api-key=" + TwelveDataIntegrationIT.API_KEY,
        "app.market-data.twelve-data.requests-per-minute=10000",
        "app.market-data.twelve-data.max-wait=PT2S",
        "app.market-data.twelve-data.read-timeout=PT2S"})
class TwelveDataIntegrationIT {

    static final String API_KEY = "it-secret-key-0123456789";
    private static final WireMockServer WIREMOCK = startWireMock();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AdminTestClient admins;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Session admin;

    @DynamicPropertySource
    static void twelveDataBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("app.market-data.twelve-data.base-url", WIREMOCK::baseUrl);
    }

    @AfterAll
    static void stopWireMock() {
        WIREMOCK.stop();
    }

    @BeforeEach
    void signInAsAdministrator() throws Exception {
        admin = admins.newAdmin();
    }

    @Test
    void searchesTheProviderAndRegistersStocksFromItsMetadata() throws Exception {
        WIREMOCK.stubFor(get(urlPathEqualTo("/symbol_search")).willReturn(okJson("""
                {"data": [
                  {"symbol": "VOLV-B", "instrument_name": "Volvo AB Class B", "exchange": "OMX", "mic_code": "XSTO",
                   "instrument_type": "Common Stock", "country": "Sweden", "currency": "SEK"},
                  {"symbol": "AAPL", "instrument_name": "Apple Inc", "exchange": "NASDAQ", "mic_code": "XNGS",
                   "instrument_type": "Common Stock", "country": "United States", "currency": "USD"}],
                 "status": "ok"}""")));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/admin/stocks/provider-search").param("query", "volvo")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider.id").value("twelve-data"))
                .andExpect(jsonPath("$.supported").value(true))
                .andExpect(jsonPath("$.results[0].symbol").value("VOLV-B"))
                .andExpect(jsonPath("$.results[0].country").value("SE"))
                .andExpect(jsonPath("$.results[0].assetType").value("COMMON_STOCK"))
                .andExpect(jsonPath("$.results[0].alreadyListed").value(false))
                .andExpect(jsonPath("$.results[1].alreadyListed").value(true));

        mockMvc.perform(post("/api/v1/admin/stocks").header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\": \"VOLV-B\", \"mic\": \"XSTO\", \"loadHistory\": false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stock.companyName").value("Volvo AB Class B"))
                .andExpect(jsonPath("$.stock.currency").value("SEK"))
                .andExpect(jsonPath("$.stock.country").value("SE"))
                .andExpect(jsonPath("$.stock.source").value("twelve-data"))
                .andExpect(jsonPath("$.job").isEmpty());
        WIREMOCK.verify(getRequestedFor(urlPathEqualTo("/symbol_search"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("apikey " + API_KEY))
                .withQueryParam("apikey", absent()));
    }

    @Test
    void backfillsStoreValidatedBarsAndRejectInconsistentOnes() throws Exception {
        String symbol = admins.registerStock(admin);
        stubTimeSeries(symbol, "splits", okJson(series(symbol, """
                {"datetime": "2025-03-03", "open": "10.0", "high": "10.5", "low": "9.8", "close": "10.2", "volume": "1000"},
                {"datetime": "2025-03-04", "open": "10.2", "high": "9.0", "low": "9.9", "close": "10.1", "volume": "1000"},
                {"datetime": "2025-03-05", "open": "10.1", "high": "10.4", "low": "10.0", "close": "10.3", "volume": "1200"}
                """)));
        stubTimeSeries(symbol, "all", okJson(series(symbol, """
                {"datetime": "2025-03-03", "open": "9.9", "high": "10.4", "low": "9.7", "close": "10.05", "volume": "1000"},
                {"datetime": "2025-03-04", "open": "10.1", "high": "9.0", "low": "9.8", "close": "9.95", "volume": "1000"},
                {"datetime": "2025-03-05", "open": "10.0", "high": "10.3", "low": "9.9", "close": "10.15", "volume": "1200"}
                """)));

        UUID job = admins.backfill(admin, symbol, "2025-03-01", "2025-03-07", "SKIP");
        admins.awaitJob(admin, job, "PARTIALLY_COMPLETED");

        String details = admins.job(admin, job);
        assertThat(JsonPath.<Integer>read(details, "$.recordsFetched")).isEqualTo(3);
        assertThat(JsonPath.<Integer>read(details, "$.recordsInserted")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(details, "$.recordsFailed")).isEqualTo(1);
        assertThat(JsonPath.<String>read(details, "$.statusMessage")).contains("2025-03-04: high is below low");
        assertThat(bars(symbol)).containsExactly(
                Map.of("bar_date", "2025-03-03", "close", new BigDecimal("10.200000"),
                        "adjusted_close", new BigDecimal("10.050000")),
                Map.of("bar_date", "2025-03-05", "close", new BigDecimal("10.300000"),
                        "adjusted_close", new BigDecimal("10.150000")));
    }

    @Test
    void aFailingProviderNeverDestroysStoredHistoryAndTheJobCanBeRetried() throws Exception {
        String symbol = admins.registerStock(admin);
        String good = """
                {"datetime": "2025-03-03", "open": "10", "high": "11", "low": "9", "close": "10.5", "volume": "1000"},
                {"datetime": "2025-03-04", "open": "10", "high": "11", "low": "9", "close": "10.6", "volume": "1000"}
                """;
        stubTimeSeries(symbol, null, okJson(series(symbol, good)));
        admins.awaitJob(admin, admins.backfill(admin, symbol, "2025-03-01", "2025-03-07", "SKIP"), "COMPLETED");
        List<Map<String, Object>> stored = bars(symbol);
        assertThat(stored).hasSize(2);

        stubTimeSeries(symbol, null, aResponse().withStatus(500));
        String body = mockMvc.perform(post("/api/v1/admin/stocks/{symbol}/market-data/repopulate", symbol)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromDate\": \"2025-03-01\", \"toDate\": \"2025-03-07\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        UUID repopulation = UUID.fromString(JsonPath.read(body, "$.job.id"));
        admins.awaitJob(admin, repopulation, "FAILED");

        String failed = admins.job(admin, repopulation);
        assertThat(JsonPath.<String>read(failed, "$.errorCode")).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(JsonPath.<Boolean>read(failed, "$.retryable")).isTrue();
        assertThat(JsonPath.<Integer>read(failed, "$.attempts")).isEqualTo(2);
        assertThat(bars(symbol)).isEqualTo(stored);

        stubTimeSeries(symbol, null, okJson(series(symbol, """
                {"datetime": "2025-03-04", "open": "10", "high": "11", "low": "9", "close": "10.7", "volume": "1000"}
                """)));
        String retried = mockMvc.perform(post("/api/v1/admin/market-data/jobs/{id}/retry", repopulation)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.job.retryOfJobId").value(repopulation.toString()))
                .andReturn().getResponse().getContentAsString();
        admins.awaitJob(admin, UUID.fromString(JsonPath.read(retried, "$.job.id")), "COMPLETED");

        // Only the span the provider covered (2025-03-04) is replaced; the earlier stored bar is kept.
        assertThat(bars(symbol)).extracting(row -> row.get("bar_date")).containsExactly("2025-03-03", "2025-03-04");
        assertThat(bars(symbol).getLast().get("close")).isEqualTo(new BigDecimal("10.700000"));
    }

    @Test
    void malformedAndDuplicateResponsesAreRejectedWithoutWritingAnything() throws Exception {
        String malformed = admins.registerStock(admin);
        stubTimeSeries(malformed, null, aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"meta\": {\"symbol\": \"" + malformed + "\""));
        UUID first = admins.backfill(admin, malformed, "2025-03-01", "2025-03-07", "SKIP");
        admins.awaitJob(admin, first, "FAILED");
        assertThat(JsonPath.<String>read(admins.job(admin, first), "$.errorCode")).isEqualTo("INVALID_PROVIDER_RESPONSE");
        assertThat(bars(malformed)).isEmpty();
        mockMvc.perform(post("/api/v1/admin/market-data/jobs/{id}/retry", first)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MARKET_DATA_JOB_NOT_RETRYABLE"));

        String duplicated = admins.registerStock(admin);
        String duplicate = """
                {"datetime": "2025-03-03", "open": "10", "high": "11", "low": "9", "close": "10.5", "volume": "1000"},
                {"datetime": "2025-03-03", "open": "10", "high": "11", "low": "9", "close": "10.4", "volume": "1000"}
                """;
        stubTimeSeries(duplicated, null, okJson(series(duplicated, duplicate)));
        UUID second = admins.backfill(admin, duplicated, "2025-03-01", "2025-03-07", "SKIP");
        admins.awaitJob(admin, second, "FAILED");
        assertThat(JsonPath.<String>read(admins.job(admin, second), "$.errorCode")).isEqualTo("INVALID_PROVIDER_DATA");
        assertThat(JsonPath.<String>read(admins.job(admin, second), "$.errorMessage")).contains("duplicate bars");
        assertThat(bars(duplicated)).isEmpty();
    }

    @Test
    void rateLimitedJobsWaitAndResumeWithoutUsingAnAttempt() throws Exception {
        String symbol = admins.registerStock(admin);
        WIREMOCK.stubFor(timeSeries(symbol, null).inScenario(symbol).whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "1")
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\": 429, \"message\": \"You have run out of API credits for the current "
                                + "minute.\", \"status\": \"error\"}"))
                .willSetStateTo("recovered"));
        WIREMOCK.stubFor(timeSeries(symbol, null).inScenario(symbol).whenScenarioStateIs("recovered")
                .willReturn(okJson(series(symbol, """
                        {"datetime": "2025-03-03", "open": "10", "high": "11", "low": "9", "close": "10.5",
                         "volume": "1000"}"""))));

        UUID job = admins.backfill(admin, symbol, "2025-03-01", "2025-03-07", "SKIP");
        admins.awaitJob(admin, job, "COMPLETED");

        assertThat(JsonPath.<Integer>read(admins.job(admin, job), "$.attempts")).isEqualTo(1);
        assertThat(bars(symbol)).hasSize(1);
    }

    @Test
    void healthChecksAndTheProviderPageNeverExposeTheApiKey() throws Exception {
        WIREMOCK.stubFor(get(urlPathEqualTo("/api_usage")).willReturn(okJson("""
                {"current_usage": 2, "plan_limit": 8, "daily_usage": 40, "plan_daily_limit": 800,
                 "plan_category": "basic"}""")));
        String healthy = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/admin/market-data/providers/health").param("refresh", "true")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("twelve-data"))
                .andExpect(jsonPath("$.status").value("HEALTHY"))
                .andExpect(jsonPath("$.details.minuteLimit").value(8))
                .andReturn().getResponse().getContentAsString();

        WIREMOCK.stubFor(get(urlPathEqualTo("/api_usage")).willReturn(aResponse().withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\": 401, \"message\": \"**apikey** parameter is incorrect\", \"status\": \"error\"}")));
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/admin/market-data/providers/health").param("refresh", "true")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$.status").value("MISCONFIGURED"));

        String providers = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/admin/market-data/providers").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active.id").value("twelve-data"))
                .andExpect(jsonPath("$.active.synthetic").value(false))
                .andExpect(jsonPath("$.active.apiKeyRequired").value(true))
                .andExpect(jsonPath("$.active.apiKeyConfigured").value(true))
                .andExpect(jsonPath("$.active.capabilities.symbolSearch").value(true))
                .andExpect(jsonPath("$.active.usage.rateLimit.requestsPerMinute").value(10000))
                .andReturn().getResponse().getContentAsString();
        assertThat(healthy + providers).doesNotContain(API_KEY);
    }

    private static WireMockServer startWireMock() {
        WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        return server;
    }

    private static MappingBuilder timeSeries(String symbol, String adjust) {
        MappingBuilder mapping = get(urlPathEqualTo("/time_series")).withQueryParam("symbol", equalTo(symbol));
        return adjust == null ? mapping : mapping.withQueryParam("adjust", equalTo(adjust));
    }

    private static void stubTimeSeries(String symbol, String adjust,
                                       ResponseDefinitionBuilder response) {
        WIREMOCK.stubFor(timeSeries(symbol, adjust).willReturn(response));
    }

    private static String series(String symbol, String values) {
        return """
                {"meta": {"symbol": "%s", "interval": "1day", "currency": "USD", "exchange_timezone": "America/New_York",
                          "exchange": "NASDAQ", "mic_code": "XNAS", "type": "Common Stock"},
                 "values": [%s], "status": "ok"}""".formatted(symbol, values);
    }

    private List<Map<String, Object>> bars(String symbol) {
        return jdbcTemplate.queryForList("""
                select p.bar_date::text as bar_date, p.close, p.adjusted_close from market_prices p
                  join stocks s on s.id = p.stock_id where s.symbol = ? order by p.bar_date""", symbol);
    }
}
