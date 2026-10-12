package com.stockforecast.admin.api;

import com.jayway.jsonpath.JsonPath;
import com.stockforecast.support.AdminTestClient;
import com.stockforecast.support.AuthTestClient;
import com.stockforecast.support.AuthTestClient.Session;
import com.stockforecast.support.IntegrationTest;
import com.stockforecast.support.WatchlistTestClient;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.stockforecast.support.AuthTestClient.PASSWORD;
import static com.stockforecast.support.AuthTestClient.REFRESH_COOKIE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AdminUserControllerIT {

    private static final String TEMPORARY_PASSWORD = "Temporary-Password-42";
    private static final String CHOSEN_PASSWORD = "my-own-secret-passphrase";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthTestClient auth;
    @Autowired
    private AdminTestClient admins;
    @Autowired
    private WatchlistTestClient watchlists;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Session admin;

    @BeforeEach
    void signInAsAdministrator() throws Exception {
        admin = admins.newAdmin();
    }

    @Test
    void searchesUsersByEmailOrNameAndFiltersThem() throws Exception {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        Session byName = auth.register(AuthTestClient.uniqueEmail(), "Grace " + marker);
        Session byEmail = auth.register("user-" + marker + "@example.com", "Somebody Else");

        mockMvc.perform(get("/api/v1/admin/users").param("query", marker.toUpperCase())
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id", hasItem(byName.userId().toString())))
                .andExpect(jsonPath("$.content[*].id", hasItem(byEmail.userId().toString())))
                .andExpect(jsonPath("$.content[0].role").value("USER"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
        mockMvc.perform(get("/api/v1/admin/users").param("query", marker).param("role", "ADMIN")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$.page.totalElements").value(0));
        mockMvc.perform(get("/api/v1/admin/users").param("role", "ADMIN").param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$.content[*].role", everyItem(is("ADMIN"))));
        mockMvc.perform(get("/api/v1/admin/users").param("size", "500")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createdUsersMustReplaceTheTemporaryPasswordBeforeUsingTheApplication() throws Exception {
        String email = AuthTestClient.uniqueEmail();
        String body = mockMvc.perform(post("/api/v1/admin/users").header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"displayName": "Alice Smith", "email": "%s", "role": "USER", "status": "ACTIVE",
                                 "temporaryPassword": "%s", "requirePasswordChange": true}"""
                                .formatted(email, TEMPORARY_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/v1/admin/users/")))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.displayName").value("Alice Smith"))
                .andExpect(jsonPath("$.passwordChangeRequired").value(true))
                .andExpect(jsonPath("$.watchlistCount").value(1))
                .andExpect(jsonPath("$.temporaryPassword").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(TEMPORARY_PASSWORD);

        MockHttpServletResponse login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"email": "%s", "password": "%s"}""".formatted(email, TEMPORARY_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.passwordChangeRequired").value(true))
                .andReturn().getResponse();
        String restricted = "Bearer " + JsonPath.read(login.getContentAsString(), "$.accessToken");
        Cookie session = login.getCookie(REFRESH_COOKIE);

        mockMvc.perform(get("/api/v1/watchlists").header(HttpHeaders.AUTHORIZATION, restricted))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, restricted))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/password").cookie(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "wrong-password-123", "newPassword": "%s"}"""
                                .formatted(CHOSEN_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
        MockHttpServletResponse changed = mockMvc.perform(post("/api/v1/auth/password").cookie(session)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"currentPassword": "%s", "newPassword": "%s"}"""
                                .formatted(TEMPORARY_PASSWORD, CHOSEN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.passwordChangeRequired").value(false))
                .andReturn().getResponse();

        mockMvc.perform(get("/api/v1/watchlists").header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + JsonPath.read(changed.getContentAsString(), "$.accessToken")))
                .andExpect(status().isOk());
        // The old session ended with the password change; the temporary password no longer works.
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(session)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "%s"}""".formatted(email, TEMPORARY_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidAndDuplicateUsers() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users").header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"displayName": "", "email": "not-an-email", "role": "USER", "status": "ACTIVE",
                                 "temporaryPassword": "short"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("temporaryPassword")));

        Session existing = auth.registerNewUser();
        String email = JsonPath.read(mockMvc.perform(get("/api/v1/admin/users/{id}", existing.userId())
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andReturn().getResponse().getContentAsString(), "$.email");
        mockMvc.perform(post("/api/v1/admin/users").header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"displayName": "Copy", "email": "%s", "role": "USER", "status": "ACTIVE",
                                 "temporaryPassword": "%s"}""".formatted(email.toUpperCase(), TEMPORARY_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void disabledUsersCannotSignInKeepTheirDataAndCanBeReEnabled() throws Exception {
        String email = AuthTestClient.uniqueEmail();
        Session user = auth.register(email, "Disabled Dan");

        mockMvc.perform(post("/api/v1/admin/users/{id}/disable", user.userId())
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.watchlistCount").value(1));

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(user.refreshCookie()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "%s"}""".formatted(email, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));

        mockMvc.perform(post("/api/v1/admin/users/{id}/enable", user.userId())
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        auth.login(email, PASSWORD);

        mockMvc.perform(get("/api/v1/admin/audit-log").param("entityType", "USER")
                        .param("entityId", user.userId().toString())
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("USER_ENABLED"))
                .andExpect(jsonPath("$.content[1].action").value("USER_DISABLED"))
                .andExpect(jsonPath("$.content[1].metadata.previousStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.content[1].actor.id").value(admin.userId().toString()));
    }

    @Test
    void lockedUsersAreToldToContactAnAdministrator() throws Exception {
        String email = AuthTestClient.uniqueEmail();
        Session user = auth.register(email, "Locked Lou");

        mockMvc.perform(patch("/api/v1/admin/users/{id}", user.userId())
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"LOCKED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "%s"}""".formatted(email, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void editsNameEmailAndRoleAndAuditsEachChange() throws Exception {
        Session user = auth.registerNewUser();
        String newEmail = AuthTestClient.uniqueEmail();

        mockMvc.perform(patch("/api/v1/admin/users/{id}", user.userId())
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"displayName": "Renamed User", "email": "%s", "role": "ADMIN"}""".formatted(newEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Renamed User"))
                .andExpect(jsonPath("$.email").value(newEmail))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mockMvc.perform(get("/api/v1/admin/audit-log").param("entityId", user.userId().toString())
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$.content[*].action", hasItem("USER_ROLE_CHANGED")))
                .andExpect(jsonPath("$.content[*].action", hasItem("USER_UPDATED")));
        mockMvc.perform(get("/api/v1/admin/audit-log").param("action", "USER_ROLE_CHANGED")
                        .param("entityId", user.userId().toString())
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$.content[0].metadata.previousRole").value("USER"))
                .andExpect(jsonPath("$.content[0].metadata.newRole").value("ADMIN"));
    }

    @Test
    void passwordResetsEndAllSessionsAndForceANewPassword() throws Exception {
        String email = AuthTestClient.uniqueEmail();
        Session user = auth.register(email, "Forgetful Fred");

        mockMvc.perform(post("/api/v1/admin/users/{id}/password-reset", user.userId())
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"temporaryPassword": "%s", "requirePasswordChange": true}"""
                                .formatted(TEMPORARY_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired").value(true));

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(user.refreshCookie()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "%s"}""".formatted(email, TEMPORARY_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.passwordChangeRequired").value(true));
        String audit = mockMvc.perform(get("/api/v1/admin/audit-log").param("action", "USER_PASSWORD_RESET")
                        .param("entityId", user.userId().toString())
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$.content[0].summary", containsString(email)))
                .andExpect(jsonPath("$.content[0].metadata.requirePasswordChange").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(audit).doesNotContain(TEMPORARY_PASSWORD);
    }

    @Test
    void deletingAUserRemovesTheirDataButNotSharedMarketData() throws Exception {
        String email = AuthTestClient.uniqueEmail();
        Session user = auth.register(email, "Leaving Lisa");
        watchlists.addStock(user, watchlists.defaultWatchlistId(user), "KO");

        mockMvc.perform(delete("/api/v1/admin/users/{id}", user.userId())
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/users/{id}", user.userId())
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        assertThat(count("select count(*) from users where id = ?", user.userId())).isZero();
        assertThat(count("select count(*) from user_profiles where user_id = ?", user.userId())).isZero();
        assertThat(count("select count(*) from watchlists where user_id = ?", user.userId())).isZero();
        assertThat(count("select count(*) from refresh_tokens where user_id = ?", user.userId())).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from stocks where symbol = 'KO'", Integer.class))
                .isEqualTo(1);
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(user.refreshCookie()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/audit-log").param("action", "USER_DELETED")
                        .param("entityId", user.userId().toString())
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$.content[0].metadata.email").value(email))
                .andExpect(jsonPath("$.content[0].metadata.watchlistsDeleted").value(1));
    }

    @Test
    void administratorsCannotDeleteDemoteOrDisableThemselves() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/users/{id}", admin.userId())
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_ADMINISTRATION_NOT_ALLOWED"));
        mockMvc.perform(patch("/api/v1/admin/users/{id}", admin.userId())
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\": \"USER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_ADMINISTRATION_NOT_ALLOWED"));
        mockMvc.perform(post("/api/v1/admin/users/{id}/disable", admin.userId())
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/admin/users/{id}", admin.userId())
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void statisticsCountUsersByStatus() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/statistics").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").isNumber())
                .andExpect(jsonPath("$.activeUsers").isNumber())
                .andExpect(jsonPath("$.disabledUsers").isNumber())
                .andExpect(jsonPath("$.registeredLast7Days").isNumber());
    }

    private int count(String sql, UUID id) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count == null ? 0 : count;
    }
}
