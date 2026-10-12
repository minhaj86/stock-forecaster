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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AdminMarketDataControllerIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthTestClient auth;
    @Autowired
    private AdminTestClient admins;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Session admin;

    @BeforeEach
    void signInAsAdministrator() throws Exception {
        admin = admins.newAdmin();
    }

    @Test
    void backfillsARangeAsynchronouslyAndSkipsExistingRecordsByDefault() throws Exception {
        String symbol = admins.registerStock(admin);

        UUID first = admins.backfill(admin, symbol, "2025-01-01", "2025-06-30", "SKIP");
        admins.awaitJob(admin, first, "COMPLETED");
        int stored = countBars(symbol);
        assertThat(stored).isGreaterThan(120);
        mockMvc.perform(get("/api/v1/admin/market-data/jobs/{id}", first).header(HttpHeaders.AUTHORIZATION,
                        admin.bearer()))
                .andExpect(jsonPath("$.type").value("BACKFILL"))
                .andExpect(jsonPath("$.trigger").value("ADMIN"))
                .andExpect(jsonPath("$.provider").value("synthetic"))
                .andExpect(jsonPath("$.fromDate").value("2025-01-01"))
                .andExpect(jsonPath("$.recordsInserted").value(stored))
                .andExpect(jsonPath("$.recordsSkipped").value(0))
                .andExpect(jsonPath("$.progress").value(100))
                .andExpect(jsonPath("$.durationMs").isNumber())
                .andExpect(jsonPath("$.requestedBy.email").isString());

        UUID overlapping = admins.backfill(admin, symbol, "2025-06-01", "2025-07-31", "SKIP");
        admins.awaitJob(admin, overlapping, "COMPLETED");
        String job = admins.job(admin, overlapping);
        int inserted = JsonPath.read(job, "$.recordsInserted");
        int skipped = JsonPath.read(job, "$.recordsSkipped");
        assertThat(skipped).isEqualTo(countBars(symbol, "2025-06-01", "2025-06-30"));
        assertThat(inserted).isEqualTo(countBars(symbol, "2025-07-01", "2025-07-31"));
        assertThat(countBars(symbol)).isEqualTo(stored + inserted);

        mockMvc.perform(get("/api/v1/admin/audit-log").param("action", "MARKET_DATA_BACKFILL_STARTED")
                        .param("entityId", symbol).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].metadata.existingDataStrategy").value("SKIP"));
    }

    @Test
    void validatesBackfillRequests() throws Exception {
        String symbol = admins.registerStock(admin);
        String tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1).toString();

        backfillRequest(symbol, "{\"fromDate\": \"2025-06-01\", \"toDate\": \"2025-01-01\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
        backfillRequest(symbol, "{\"fromDate\": \"2025-06-01\", \"toDate\": \"" + tomorrow + "\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
        backfillRequest(symbol, "{\"toDate\": \"2025-01-01\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        backfillRequest(symbol, "{\"fromDate\": \"2025-01-01\", \"toDate\": \"2025-02-01\", \"interval\": \"ONE_MINUTE\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mockMvc.perform(post("/api/v1/admin/stocks/NOPE/market-data/update")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STOCK_NOT_FOUND"));
    }

    @Test
    void latestDataUpdatesOnlyFetchWhatIsMissing() throws Exception {
        String symbol = admins.registerStock(admin);
        UUID initial = admins.backfill(admin, symbol, "2025-01-01", "2025-03-31", "SKIP");
        admins.awaitJob(admin, initial, "COMPLETED");

        String body = mockMvc.perform(post("/api/v1/admin/stocks/{symbol}/market-data/update", symbol)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/v1/admin/market-data/jobs/")))
                .andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.job.type").value("LATEST_UPDATE"))
                .andReturn().getResponse().getContentAsString();
        UUID update = UUID.fromString(JsonPath.read(body, "$.job.id"));
        admins.awaitJob(admin, update, "COMPLETED");

        // From the newest stored bar (minus a short correction window) to today, not the whole history again.
        String job = admins.job(admin, update);
        assertThat(LocalDate.parse(JsonPath.read(job, "$.fromDate"))).isAfter(LocalDate.of(2025, 3, 20));
        assertThat(JsonPath.<Integer>read(job, "$.recordsInserted")).isGreaterThan(100);
        mockMvc.perform(get("/api/v1/admin/stocks/{symbol}", symbol).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$.marketData.firstBarDate").value("2025-01-01"))
                .andExpect(jsonPath("$.marketData.barCount").value(countBars(symbol)));
    }

    @Test
    void repopulationReplacesTheHistoryAndInvalidatesCachedForecastsAndAnalytics() throws Exception {
        String symbol = admins.registerStock(admin);
        UUID initial = admins.backfill(admin, symbol, LocalDate.now(ZoneOffset.UTC).minusYears(2).toString(),
                LocalDate.now(ZoneOffset.UTC).toString(), "SKIP");
        admins.awaitJob(admin, initial, "COMPLETED");
        Session user = auth.registerNewUser();
        String before = forecast(user, symbol);
        assertThat(forecast(user, symbol)).as("served from cache").isEqualTo(before);
        // A stored bar the provider does not report (a bogus weekend bar) is removed by the repopulation.
        jdbcTemplate.update("""
                insert into market_prices (stock_id, bar_date, open, high, low, close, adjusted_close, volume,
                                           data_provider, ingested_at)
                select id, date '2025-12-27', 1, 1, 1, 1, 1, 1, 'synthetic', now() from stocks where symbol = ?""",
                symbol);

        String body = mockMvc.perform(post("/api/v1/admin/stocks/{symbol}/market-data/repopulate", symbol)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"fromDate": "%s", "toDate": "%s", "interval": "ONE_DAY"}"""
                                .formatted(LocalDate.now(ZoneOffset.UTC).minusYears(2),
                                        LocalDate.now(ZoneOffset.UTC))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.job.type").value("REPOPULATE"))
                .andReturn().getResponse().getContentAsString();
        UUID repopulation = UUID.fromString(JsonPath.read(body, "$.job.id"));
        admins.awaitJob(admin, repopulation, "COMPLETED");

        String job = admins.job(admin, repopulation);
        assertThat(JsonPath.<Integer>read(job, "$.recordsDeleted")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(job, "$.recordsUpdated")).isZero();
        assertThat(JsonPath.<Integer>read(job, "$.recordsSkipped")).isGreaterThan(400);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from market_prices p join stocks s on s.id = p.stock_id
                 where s.symbol = ? and p.bar_date = date '2025-12-27'""", Integer.class, symbol)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from market_price_staging", Integer.class)).isZero();

        String after = forecast(user, symbol);
        assertThat(JsonPath.<String>read(after, "$.lastSyncedAt")).isNotEqualTo(JsonPath.read(before, "$.lastSyncedAt"));
        assertThat(JsonPath.<String>read(after, "$.generatedAt")).isNotEqualTo(JsonPath.read(before, "$.generatedAt"));
        mockMvc.perform(get("/api/v1/admin/audit-log").param("action", "MARKET_DATA_REPOPULATION_STARTED")
                        .param("entityId", symbol).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$.content[0].metadata.jobId").value(repopulation.toString()));
    }

    @Test
    void jobsCanBeFilteredAndOnlyEligibleJobsRetriedOrCancelled() throws Exception {
        String symbol = admins.registerStock(admin);
        UUID job = admins.backfill(admin, symbol, "2025-01-01", "2025-01-31", "UPDATE");
        admins.awaitJob(admin, job, "COMPLETED");

        mockMvc.perform(get("/api/v1/admin/market-data/jobs").param("symbol", symbol)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(job.toString()))
                .andExpect(jsonPath("$.content[0].symbol").value(symbol))
                .andExpect(jsonPath("$.content[0].existingDataStrategy").value("UPDATE"));
        mockMvc.perform(get("/api/v1/admin/market-data/jobs").param("status", "COMPLETED").param("type", "BACKFILL")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$.content[*].status", everyItem(is("COMPLETED"))))
                .andExpect(jsonPath("$.content[*].type", everyItem(is("BACKFILL"))));
        mockMvc.perform(get("/api/v1/admin/market-data/jobs").param("symbol", "NOPE")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$.page.totalElements").value(0));
        mockMvc.perform(get("/api/v1/admin/market-data/jobs").param("status", "SOMETHING")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));

        mockMvc.perform(post("/api/v1/admin/market-data/jobs/{id}/retry", job)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MARKET_DATA_JOB_NOT_RETRYABLE"));
        mockMvc.perform(post("/api/v1/admin/market-data/jobs/{id}/cancel", job)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MARKET_DATA_JOB_NOT_CANCELLABLE"));
        mockMvc.perform(get("/api/v1/admin/market-data/jobs/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MARKET_DATA_JOB_NOT_FOUND"));
    }

    @Test
    void bulkLoadsQueueOneJobPerStockAndSynchronisationCoversWatchedStocks() throws Exception {
        String first = admins.registerStock(admin);
        String second = admins.registerStock(admin);

        String body = mockMvc.perform(post("/api/v1/admin/market-data/bulk-load")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"symbols": ["%s", "%s"], "fromDate": "2025-01-01", "toDate": "2025-02-28"}"""
                                .formatted(first, second)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobs.length()").value(2))
                .andExpect(jsonPath("$.jobs[0].existingDataStrategy").value("SKIP"))
                .andReturn().getResponse().getContentAsString();
        String batchId = JsonPath.read(body, "$.batchId");
        List<String> jobIds = JsonPath.read(body, "$.jobs[*].id");
        for (String id : jobIds) {
            admins.awaitJob(admin, UUID.fromString(id), "COMPLETED");
        }
        assertThat(JsonPath.<String>read(admins.job(admin, UUID.fromString(jobIds.getFirst())), "$.batchId"))
                .isEqualTo(batchId);
        mockMvc.perform(post("/api/v1/admin/market-data/bulk-load").header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"symbols": ["NOPE"], "fromDate": "2025-01-01", "toDate": "2025-02-28"}"""))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/admin/market-data/synchronize").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.stocksConsidered").isNumber())
                .andExpect(jsonPath("$.jobsQueued").isNumber());
        mockMvc.perform(get("/api/v1/admin/audit-log").param("action", "MARKET_DATA_SYNC_TRIGGERED")
                        .param("actor", "user-").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$.page.totalElements", greaterThanOrEqualTo(1)));
    }

    @Test
    void theDashboardAndProviderPagesSummariseOperationsWithoutSecrets() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.total", greaterThan(0)))
                .andExpect(jsonPath("$.users.administrators", greaterThan(0)))
                .andExpect(jsonPath("$.stocks.total", greaterThan(60)))
                .andExpect(jsonPath("$.stocks.missingData").isNumber())
                .andExpect(jsonPath("$.marketData.provider.id").value("synthetic"))
                .andExpect(jsonPath("$.marketData.provider.synthetic").value(true))
                .andExpect(jsonPath("$.jobs.completedToday").isNumber());

        String providers = mockMvc.perform(get("/api/v1/admin/market-data/providers")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active.id").value("synthetic"))
                .andExpect(jsonPath("$.active.apiKeyRequired").value(false))
                .andExpect(jsonPath("$.active.apiKeyConfigured").value(false))
                .andExpect(jsonPath("$.active.capabilities.supportedIntervals", hasItem("ONE_DAY")))
                .andExpect(jsonPath("$.active.capabilities.symbolSearch").value(false))
                .andExpect(jsonPath("$.active.usage.requestsToday").isNumber())
                .andExpect(jsonPath("$.available[*].id", hasItem("twelve-data")))
                .andReturn().getResponse().getContentAsString();
        assertThat(providers).doesNotContainIgnoringCase("apiKey\":\"").doesNotContain("TWELVE_DATA_API_KEY=");

        mockMvc.perform(get("/api/v1/admin/market-data/providers/health").param("refresh", "true")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HEALTHY"))
                .andExpect(jsonPath("$.checkedAt").isString());
    }

    private ResultActions backfillRequest(String symbol, String json)
            throws Exception {
        return mockMvc.perform(post("/api/v1/admin/stocks/{symbol}/market-data/backfill", symbol)
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String forecast(Session user, String symbol) throws Exception {
        return mockMvc.perform(get("/api/v1/stocks/{symbol}/forecast", symbol).param("model", "NAIVE")
                        .header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private int countBars(String symbol) {
        return countBars(symbol, "1970-01-01", "9999-12-31");
    }

    private int countBars(String symbol, String from, String to) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from market_prices p join stocks s on s.id = p.stock_id
                 where s.symbol = ? and p.bar_date between cast(? as date) and cast(? as date)""",
                Integer.class, symbol, from, to);
        return count == null ? 0 : count;
    }

}
