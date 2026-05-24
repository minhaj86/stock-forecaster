package com.stockforecast.support;

import com.jayway.jsonpath.JsonPath;
import com.stockforecast.support.AuthTestClient.Session;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Watchlist and market-data helpers driving the real API.
 */
@TestComponent
public class WatchlistTestClient {

    private final MockMvc mockMvc;

    public WatchlistTestClient(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    /** The starter watchlist every new user receives. */
    public UUID defaultWatchlistId(Session session) throws Exception {
        String body = mockMvc.perform(get("/api/v1/watchlists").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$[0].id"));
    }

    public UUID createWatchlist(Session session, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/watchlists").header(HttpHeaders.AUTHORIZATION, session.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name": "%s"}""".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    public void addStock(Session session, UUID watchlistId, String symbol) throws Exception {
        mockMvc.perform(post("/api/v1/watchlists/{id}/items", watchlistId)
                        .header(HttpHeaders.AUTHORIZATION, session.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"symbol": "%s"}""".formatted(symbol)))
                .andExpect(status().isCreated());
    }

    /** Waits for the asynchronous backfill that starts when a stock is watched. */
    public void awaitMarketData(Session session, String symbol) {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/stocks/{symbol}/prices", symbol).param("period", "5D")
                                .header(HttpHeaders.AUTHORIZATION, session.bearer()))
                        .andExpect(jsonPath("$.dataStatus").value("READY")));
    }
}
