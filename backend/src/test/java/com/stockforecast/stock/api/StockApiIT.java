package com.stockforecast.stock.api;

import com.stockforecast.stock.StockRef;
import com.stockforecast.stock.application.StockCatalogService;
import com.stockforecast.support.AuthTestClient;
import com.stockforecast.support.AuthTestClient.Session;
import com.stockforecast.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class StockApiIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthTestClient auth;
    @Autowired
    private CacheManager cacheManager;

    private Session session;

    @BeforeEach
    void signIn() throws Exception {
        session = auth.registerNewUser();
    }

    @Test
    void exactSymbolMatchesRankFirst() throws Exception {
        mockMvc.perform(get("/api/v1/stocks/search").param("q", "aapl").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].companyName").value("Apple Inc."))
                .andExpect(jsonPath("$[0].exchange").value("NASDAQ"));
    }

    @Test
    void searchMatchesCompanyNamesAndFormerNames() throws Exception {
        mockMvc.perform(get("/api/v1/stocks/search").param("q", "micro").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].symbol", hasItems("MSFT", "MU", "MSTR", "AMD")))
                // Name/alias prefixes ("Microsoft", "MicroStrategy") rank above substring matches ("Advanced Micro").
                .andExpect(jsonPath("$[3].symbol").value("AMD"));
        mockMvc.perform(get("/api/v1/stocks/search").param("q", "facebook").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(jsonPath("$[0].symbol").value("META"));
    }

    @Test
    void searchTreatsWildcardsLiterallyAndValidatesInput() throws Exception {
        mockMvc.perform(get("/api/v1/stocks/search").param("q", "%").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/v1/stocks/search").param("q", " ").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/v1/stocks/search").param("q", "a").param("limit", "500")
                        .header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchResultsAreCachedInRedisAndReadBack() throws Exception {
        mockMvc.perform(get("/api/v1/stocks/search").param("q", "Nvidia").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        List<StockRef> cached = cacheManager.getCache(StockCatalogService.SEARCH_CACHE).get("NVIDIA|8", List.class);
        assertThat(cached).extracting(StockRef::symbol).containsExactly("NVDA");
    }

    @Test
    void browsesTheCatalogueBySectorWithPagination() throws Exception {
        mockMvc.perform(get("/api/v1/stocks").param("sector", "Energy").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].symbol", contains("CVX", "XOM")))
                .andExpect(jsonPath("$.page.totalElements").value(2));
        mockMvc.perform(get("/api/v1/stocks").param("size", "5").param("page", "1")
                        .header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.size").value(5));
        mockMvc.perform(get("/api/v1/stocks/sectors").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(jsonPath("$", hasItems("Energy", "Information Technology", "Health Care")));
    }

    @Test
    void returnsStockDetailsOrNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/stocks/brk.b").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("BRK.B"))
                .andExpect(jsonPath("$.assetType").value("COMMON_STOCK"))
                .andExpect(jsonPath("$.currency").value("USD"));
        mockMvc.perform(get("/api/v1/stocks/NOPE").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STOCK_NOT_FOUND"));
    }

    @Test
    void catalogueRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/stocks/search").param("q", "apple"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void everyCatalogueEntryHasCompleteReferenceData() throws Exception {
        mockMvc.perform(get("/api/v1/stocks").param("size", "100").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(jsonPath("$.page.totalElements", is(68)))
                .andExpect(jsonPath("$.content[*].currency", everyItem(is("USD"))));
    }
}
