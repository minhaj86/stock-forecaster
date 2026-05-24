package com.stockforecast.support;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Drives the real authentication endpoints to obtain tokens for integration tests.
 */
@TestComponent
public class AuthTestClient {

    public static final String REFRESH_COOKIE = "sf_refresh_token";
    public static final String PASSWORD = "correct-horse-battery-staple";

    private final MockMvc mockMvc;

    public AuthTestClient(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    public static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    public Session register(String email, String displayName) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s", "displayName": "%s"}
                                """.formatted(email, PASSWORD, displayName)))
                .andReturn().getResponse();
        return Session.from(response, 201);
    }

    public Session registerNewUser() throws Exception {
        return register(uniqueEmail(), "Test User");
    }

    public Session login(String email, String password) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, password)))
                .andReturn().getResponse();
        return Session.from(response, 200);
    }

    public record Session(String accessToken, String refreshToken, UUID userId) {

        static Session from(MockHttpServletResponse response, int expectedStatus) throws Exception {
            if (response.getStatus() != expectedStatus) {
                throw new AssertionError("Expected HTTP " + expectedStatus + " but was " + response.getStatus()
                        + ": " + response.getContentAsString());
            }
            String body = response.getContentAsString();
            Cookie cookie = response.getCookie(REFRESH_COOKIE);
            if (cookie == null) {
                throw new AssertionError("No refresh token cookie in response");
            }
            return new Session(JsonPath.read(body, "$.accessToken"), cookie.getValue(),
                    UUID.fromString(JsonPath.read(body, "$.user.id")));
        }

        public String bearer() {
            return "Bearer " + accessToken;
        }

        public Cookie refreshCookie() {
            return new Cookie(REFRESH_COOKIE, refreshToken);
        }
    }
}
