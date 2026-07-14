package com.stockforecast.watchlist.api;

import com.jayway.jsonpath.JsonPath;
import com.stockforecast.support.AuthTestClient;
import com.stockforecast.support.AuthTestClient.Session;
import com.stockforecast.support.IntegrationTest;
import com.stockforecast.support.WatchlistTestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class WatchlistApiIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthTestClient auth;
    @Autowired
    private WatchlistTestClient watchlists;

    private Session alice;

    @BeforeEach
    void signIn() throws Exception {
        alice = auth.registerNewUser();
    }

    @Test
    void newUsersStartWithAnEmptyDefaultWatchlist() throws Exception {
        mockMvc.perform(get("/api/v1/watchlists").header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("My Watchlist"))
                .andExpect(jsonPath("$[0].stockCount").value(0));
    }

    @Test
    void createsRenamesAndDeletesWatchlists() throws Exception {
        String created = mockMvc.perform(post("/api/v1/watchlists").header(HttpHeaders.AUTHORIZATION, alice.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name": "  Technology "}"""))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.name").value("Technology"))
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        mockMvc.perform(patch("/api/v1/watchlists/{id}", id).header(HttpHeaders.AUTHORIZATION, alice.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name": "Tech leaders"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Tech leaders"));

        mockMvc.perform(get("/api/v1/watchlists").header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(jsonPath("$[*].name", contains("My Watchlist", "Tech leaders")));

        mockMvc.perform(delete("/api/v1/watchlists/{id}", id).header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/watchlists/{id}", id).header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WATCHLIST_NOT_FOUND"));
    }

    @Test
    void watchlistNamesAreUniquePerUserIgnoringCase() throws Exception {
        watchlists.createWatchlist(alice, "Dividends");

        mockMvc.perform(post("/api/v1/watchlists").header(HttpHeaders.AUTHORIZATION, alice.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name": "DIVIDENDS"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WATCHLIST_NAME_TAKEN"));

        // Another user may use the same name.
        watchlists.createWatchlist(auth.registerNewUser(), "Dividends");
    }

    @Test
    void addsListsAndRemovesStocks() throws Exception {
        UUID id = watchlists.defaultWatchlistId(alice);

        mockMvc.perform(post("/api/v1/watchlists/{id}/items", id).header(HttpHeaders.AUTHORIZATION, alice.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"symbol": "msft", "notes": "Cloud leader"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stock.symbol").value("MSFT"))
                .andExpect(jsonPath("$.stock.companyName").value("Microsoft Corporation"))
                .andExpect(jsonPath("$.notes").value("Cloud leader"));
        watchlists.addStock(alice, id, "AAPL");

        mockMvc.perform(get("/api/v1/watchlists/{id}", id).header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockCount").value(2))
                .andExpect(jsonPath("$.items[*].stock.symbol", contains("MSFT", "AAPL")));
        mockMvc.perform(get("/api/v1/watchlists").header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(jsonPath("$[0].symbols", contains("MSFT", "AAPL")));

        mockMvc.perform(patch("/api/v1/watchlists/{id}/items/{symbol}", id, "AAPL")
                        .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"notes": "Buy below 150"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("Buy below 150"));

        mockMvc.perform(delete("/api/v1/watchlists/{id}/items/{symbol}", id, "MSFT")
                        .header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/watchlists/{id}", id).header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(jsonPath("$.items[*].stock.symbol", contains("AAPL")));
    }

    @Test
    void rejectsDuplicateUnknownAndMissingStocks() throws Exception {
        UUID id = watchlists.defaultWatchlistId(alice);
        watchlists.addStock(alice, id, "NVDA");

        mockMvc.perform(post("/api/v1/watchlists/{id}/items", id).header(HttpHeaders.AUTHORIZATION, alice.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"symbol": "NVDA"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCK_ALREADY_IN_WATCHLIST"));
        mockMvc.perform(post("/api/v1/watchlists/{id}/items", id).header(HttpHeaders.AUTHORIZATION, alice.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"symbol": "ZZZZ"}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STOCK_NOT_FOUND"));
        mockMvc.perform(delete("/api/v1/watchlists/{id}/items/{symbol}", id, "AAPL")
                        .header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STOCK_NOT_IN_WATCHLIST"));
        mockMvc.perform(get("/api/v1/watchlists/{id}", "not-a-uuid").header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void validatesNames() throws Exception {
        mockMvc.perform(post("/api/v1/watchlists").header(HttpHeaders.AUTHORIZATION, alice.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name": "   "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void userACannotReadModifyOrDeleteUserBsWatchlist() throws Exception {
        Session bob = auth.registerNewUser();
        UUID bobsList = watchlists.defaultWatchlistId(bob);
        watchlists.addStock(bob, bobsList, "TSLA");

        mockMvc.perform(get("/api/v1/watchlists/{id}", bobsList).header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WATCHLIST_NOT_FOUND"));
        mockMvc.perform(patch("/api/v1/watchlists/{id}", bobsList).header(HttpHeaders.AUTHORIZATION, alice.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name": "Hijacked"}"""))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/watchlists/{id}", bobsList).header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/watchlists").header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(jsonPath("$[*].id", contains(watchlists.defaultWatchlistId(alice).toString())));

        mockMvc.perform(get("/api/v1/watchlists/{id}", bobsList).header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                .andExpect(jsonPath("$.name").value("My Watchlist"))
                .andExpect(jsonPath("$.items[*].stock.symbol", contains("TSLA")));
    }

    @Test
    void userACannotAddToOrDeleteItemsFromUserBsWatchlist() throws Exception {
        Session bob = auth.registerNewUser();
        UUID bobsList = watchlists.defaultWatchlistId(bob);
        watchlists.addStock(bob, bobsList, "AMZN");

        mockMvc.perform(post("/api/v1/watchlists/{id}/items", bobsList).header(HttpHeaders.AUTHORIZATION, alice.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"symbol": "AAPL"}"""))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/watchlists/{id}/items/{symbol}", bobsList, "AMZN")
                        .header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WATCHLIST_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/watchlists/{id}", bobsList).header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                .andExpect(jsonPath("$.items[*].stock.symbol", contains("AMZN")));
    }

    @Test
    void enforcesTheNumberOfWatchlistsPerUser() throws Exception {
        for (int i = 1; i < 20; i++) {
            watchlists.createWatchlist(alice, "List " + i);
        }

        mockMvc.perform(post("/api/v1/watchlists").header(HttpHeaders.AUTHORIZATION, alice.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name": "One too many"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WATCHLIST_LIMIT_REACHED"));
    }
}
