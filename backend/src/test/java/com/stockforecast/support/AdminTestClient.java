package com.stockforecast.support;

import com.jayway.jsonpath.JsonPath;
import com.stockforecast.identity.Role;
import com.stockforecast.identity.application.RegistrationService;
import com.stockforecast.identity.domain.EmailAddress;
import com.stockforecast.support.AuthTestClient.Session;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Administrators, stocks and market-data jobs for admin console integration tests.
 */
@TestComponent
public class AdminTestClient {

    private final MockMvc mockMvc;
    private final AuthTestClient auth;
    private final RegistrationService registrationService;

    public AdminTestClient(MockMvc mockMvc, AuthTestClient auth, RegistrationService registrationService) {
        this.mockMvc = mockMvc;
        this.auth = auth;
        this.registrationService = registrationService;
    }

    /** A new, active administrator who is signed in. */
    public Session newAdmin() throws Exception {
        String email = AuthTestClient.uniqueEmail();
        registrationService.createAccount(EmailAddress.of(email), AuthTestClient.PASSWORD, "Test Admin", Role.ADMIN);
        return auth.login(email, AuthTestClient.PASSWORD);
    }

    /** A symbol no other test uses (synthetic prices exist for any symbol). */
    public static String uniqueSymbol() {
        StringBuilder symbol = new StringBuilder("Z");
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 5; i++) {
            symbol.append((char) ('A' + random.nextInt(26)));
        }
        return symbol.toString().toUpperCase(Locale.ROOT);
    }

    /** Registers a stock manually (without loading history). */
    public String registerStock(Session admin) throws Exception {
        String symbol = uniqueSymbol();
        mockMvc.perform(post("/api/v1/admin/stocks").header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"symbol": "%s", "loadHistory": false,
                                 "manual": {"companyName": "%s Test Corp", "exchange": "NASDAQ", "mic": "XNAS",
                                            "currency": "USD", "country": "US", "assetType": "COMMON_STOCK"}}"""
                                .formatted(symbol, symbol)))
                .andExpect(status().isCreated());
        return symbol;
    }

    /** Queues a backfill and returns the job id. */
    public UUID backfill(Session admin, String symbol, String from, String to, String strategy) throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/stocks/{symbol}/market-data/backfill", symbol)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"fromDate": "%s", "toDate": "%s", "interval": "ONE_DAY", "existingDataStrategy": "%s"}"""
                                .formatted(from, to, strategy)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.job.id"));
    }

    /** Waits until the job reached the given final status. */
    public void awaitJob(Session admin, UUID jobId, String expectedStatus) {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/admin/market-data/jobs/{id}", jobId)
                                .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                        .andExpect(jsonPath("$.status").value(expectedStatus)));
    }

    public String job(Session admin, UUID jobId) throws Exception {
        return mockMvc.perform(get("/api/v1/admin/market-data/jobs/{id}", jobId)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
