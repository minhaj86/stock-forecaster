package com.stockforecast.identity.api;

import com.jayway.jsonpath.JsonPath;
import com.stockforecast.support.AuthTestClient;
import com.stockforecast.support.AuthTestClient.Session;
import com.stockforecast.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.stockforecast.support.AuthTestClient.PASSWORD;
import static com.stockforecast.support.AuthTestClient.REFRESH_COOKIE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AuthApiIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthTestClient auth;

    @Test
    void registrationCreatesAnAccountAndSignsTheUserIn() throws Exception {
        String suffix = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": " New.User-%s@Example.COM ", "password": "%s", "displayName": "  New User "}
                                """.formatted(suffix, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/users/me"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString(REFRESH_COOKIE + "="), containsString("HttpOnly"), containsString("Secure"),
                        containsString("SameSite=Strict"), containsString("Path=/api/v1/auth"),
                        matchesPattern(".*Max-Age=60(4799|4800);.*"))))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn", anyOf(is(899), is(900))))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.user.email").value("new.user-" + suffix + "@example.com"))
                .andExpect(jsonPath("$.user.role").value("USER"));
    }

    @Test
    void registrationRejectsAnAlreadyRegisteredEmailIgnoringCase() throws Exception {
        String email = AuthTestClient.uniqueEmail();
        auth.register(email, "First");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s", "displayName": "Second"}
                                """.formatted(email.toUpperCase(), PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void registrationValidatesThePayload() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "not-an-email", "password": "short", "displayName": " "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("email", "password", "displayName")));
    }

    @Test
    void malformedAndUnsupportedBodiesAreClientErrors() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void loginIssuesTokensForValidCredentials() throws Exception {
        String email = AuthTestClient.uniqueEmail();
        Session registered = auth.register(email, "Login User");

        Session session = auth.login(email.toUpperCase(), PASSWORD);

        assertThat(session.userId()).isEqualTo(registered.userId());
        assertThat(session.refreshToken()).isNotEqualTo(registered.refreshToken());
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void withoutRememberMeTheRefreshCookieOnlyLivesForTheBrowserSessionAcrossRotations() throws Exception {
        String email = AuthTestClient.uniqueEmail();
        auth.register(email, "Session Only");

        MockHttpServletResponse login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s", "rememberMe": false}""".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(containsString("HttpOnly"),
                        not(containsString("Max-Age")), not(containsString("Expires")))))
                .andReturn().getResponse();

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(login.getCookie(REFRESH_COOKIE)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, not(containsString("Max-Age"))));
    }

    @Test
    void loginFailuresDoNotRevealWhetherTheAccountExists() throws Exception {
        String email = AuthTestClient.uniqueEmail();
        auth.register(email, "Existing");

        String wrongPassword = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "wrong-password-123"}""".formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();
        String unknownAccount = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "wrong-password-123"}""".formatted(AuthTestClient.uniqueEmail())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(wrongPassword, "$.message"))
                .isEqualTo(JsonPath.<String>read(unknownAccount, "$.message"));
    }

    @Test
    void refreshRotatesTheRefreshTokenAndIssuesAWorkingAccessToken() throws Exception {
        Session session = auth.registerNewUser();

        MockHttpServletResponse response = mockMvc.perform(post("/api/v1/auth/refresh").cookie(session.refreshCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(session.userId().toString()))
                .andReturn().getResponse();

        Cookie rotated = response.getCookie(REFRESH_COOKIE);
        assertThat(rotated).isNotNull();
        assertThat(rotated.getValue()).isNotBlank().isNotEqualTo(session.refreshToken());
        String accessToken = JsonPath.read(response.getContentAsString(), "$.accessToken");
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void authenticationEndpointsIgnoreAStaleBearerToken() throws Exception {
        Session session = auth.registerNewUser();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(session.refreshCookie())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired-or-garbage-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void missingOrUnknownRefreshTokensAreRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(REFRESH_COOKIE, "forged-token-value")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void replayingARotatedRefreshTokenRevokesTheWholeSession() throws Exception {
        Session session = auth.registerNewUser();
        String successor = mockMvc.perform(post("/api/v1/auth/refresh").cookie(session.refreshCookie()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(REFRESH_COOKIE).getValue();

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(session.refreshCookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // The legitimate successor is revoked too: a replay means the token chain may be in an attacker's hands.
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(REFRESH_COOKIE, successor)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logoutRevokesTheSessionClearsTheCookieAndIsIdempotent() throws Exception {
        Session session = auth.registerNewUser();

        mockMvc.perform(post("/api/v1/auth/logout").cookie(session.refreshCookie()))
                .andExpect(status().isNoContent())
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE,
                        hasItem(allOf(containsString(REFRESH_COOKIE + "=;"), containsString("Max-Age=0")))));

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(session.refreshCookie()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/logout").cookie(session.refreshCookie()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent());
    }
}
