package com.stockforecast.admin.api;

import com.stockforecast.support.AdminTestClient;
import com.stockforecast.support.AuthTestClient;
import com.stockforecast.support.AuthTestClient.Session;
import com.stockforecast.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Backend authorization of the whole admin API: hiding links in the UI is not a security control.
 */
@IntegrationTest
class AdminAuthorizationIT {

    private static final String SOME_ID = "0199d1c4-6d6f-7a51-9f43-2b9c7a0c1b11";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthTestClient auth;
    @Autowired
    private AdminTestClient admins;

    static Stream<Object[]> adminEndpoints() {
        return Stream.of(
                new Object[]{HttpMethod.GET, "/api/v1/admin/dashboard"},
                new Object[]{HttpMethod.GET, "/api/v1/admin/users"},
                new Object[]{HttpMethod.GET, "/api/v1/admin/users/statistics"},
                new Object[]{HttpMethod.GET, "/api/v1/admin/users/" + SOME_ID},
                new Object[]{HttpMethod.POST, "/api/v1/admin/users"},
                new Object[]{HttpMethod.PATCH, "/api/v1/admin/users/" + SOME_ID},
                new Object[]{HttpMethod.POST, "/api/v1/admin/users/" + SOME_ID + "/disable"},
                new Object[]{HttpMethod.POST, "/api/v1/admin/users/" + SOME_ID + "/enable"},
                new Object[]{HttpMethod.POST, "/api/v1/admin/users/" + SOME_ID + "/password-reset"},
                new Object[]{HttpMethod.DELETE, "/api/v1/admin/users/" + SOME_ID},
                new Object[]{HttpMethod.GET, "/api/v1/admin/stocks"},
                new Object[]{HttpMethod.GET, "/api/v1/admin/stocks/provider-search?query=apple"},
                new Object[]{HttpMethod.GET, "/api/v1/admin/stocks/AAPL"},
                new Object[]{HttpMethod.POST, "/api/v1/admin/stocks"},
                new Object[]{HttpMethod.POST, "/api/v1/admin/stocks/AAPL/deactivate"},
                new Object[]{HttpMethod.POST, "/api/v1/admin/stocks/AAPL/activate"},
                new Object[]{HttpMethod.POST, "/api/v1/admin/stocks/AAPL/market-data/update"},
                new Object[]{HttpMethod.POST, "/api/v1/admin/stocks/AAPL/market-data/backfill"},
                new Object[]{HttpMethod.POST, "/api/v1/admin/stocks/AAPL/market-data/repopulate"},
                new Object[]{HttpMethod.POST, "/api/v1/admin/market-data/synchronize"},
                new Object[]{HttpMethod.POST, "/api/v1/admin/market-data/bulk-load"},
                new Object[]{HttpMethod.GET, "/api/v1/admin/market-data/jobs"},
                new Object[]{HttpMethod.GET, "/api/v1/admin/market-data/jobs/" + SOME_ID},
                new Object[]{HttpMethod.POST, "/api/v1/admin/market-data/jobs/" + SOME_ID + "/retry"},
                new Object[]{HttpMethod.POST, "/api/v1/admin/market-data/jobs/" + SOME_ID + "/cancel"},
                new Object[]{HttpMethod.GET, "/api/v1/admin/market-data/providers"},
                new Object[]{HttpMethod.GET, "/api/v1/admin/market-data/providers/health"},
                new Object[]{HttpMethod.GET, "/api/v1/admin/audit-log"});
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("adminEndpoints")
    void unauthenticatedRequestsAreRejectedWith401(HttpMethod method, String path) throws Exception {
        mockMvc.perform(call(method, path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("adminEndpoints")
    void regularUsersAreForbiddenWith403(HttpMethod method, String path) throws Exception {
        Session user = auth.registerNewUser();

        mockMvc.perform(call(method, path).header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void administratorsCanUseTheAdminApi() throws Exception {
        Session admin = admins.newAdmin();

        mockMvc.perform(get("/api/v1/admin/dashboard").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.total").isNumber());
        mockMvc.perform(get("/api/v1/admin/users/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void aDemotedAdministratorsStillValidTokenNoLongerGrantsAccess() throws Exception {
        Session demoted = admins.newAdmin();
        Session other = admins.newAdmin();
        mockMvc.perform(get("/api/v1/admin/dashboard").header(HttpHeaders.AUTHORIZATION, demoted.bearer()))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/admin/users/{id}", demoted.userId())
                        .header(HttpHeaders.AUTHORIZATION, other.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\": \"USER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));

        // The access token still says ADMIN, but the account no longer is one.
        mockMvc.perform(get("/api/v1/admin/dashboard").header(HttpHeaders.AUTHORIZATION, demoted.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private static MockHttpServletRequestBuilder call(HttpMethod method, String path) {
        return request(method, path).contentType(MediaType.APPLICATION_JSON).content("{}");
    }
}
