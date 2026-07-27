package com.stockforecast.security;

import com.stockforecast.identity.Role;
import com.stockforecast.identity.application.RegistrationService;
import com.stockforecast.identity.domain.EmailAddress;
import com.stockforecast.identity.infrastructure.security.JwtProperties;
import com.stockforecast.support.AuthTestClient;
import com.stockforecast.support.AuthTestClient.Session;
import com.stockforecast.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class ApiSecurityIT {

    private static final String ME = "/api/v1/users/me";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthTestClient auth;
    @Autowired
    private JwtEncoder jwtEncoder;
    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private RegistrationService registrationService;

    @Test
    void protectedEndpointsRequireAuthenticationAndAnswerWithACorrelatedApiError() throws Exception {
        mockMvc.perform(get(ME).header("X-Correlation-Id", "it-correlation-0001"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
                .andExpect(header().string("X-Correlation-Id", "it-correlation-0001"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value(ME))
                .andExpect(jsonPath("$.correlationId").value("it-correlation-0001"));
    }

    @Test
    void expiredAccessTokensAreRejected() throws Exception {
        Session session = auth.registerNewUser();
        Instant issuedAt = Instant.now().minus(Duration.ofHours(2));
        String expired = accessToken(session.userId(), issuedAt, issuedAt.plus(Duration.ofMinutes(15)),
                "at+jwt", jwtProperties.audience());

        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")))
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
    }

    @Test
    void tamperedTokensAreRejectedIncludingPrivilegeEscalationAttempts() throws Exception {
        Session session = auth.registerNewUser();
        String[] parts = session.accessToken().split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String escalatedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.replace("\"USER\"", "\"ADMIN\"").getBytes(StandardCharsets.UTF_8));
        String escalated = parts[0] + "." + escalatedPayload + "." + parts[2];
        String badSignature = parts[0] + "." + parts[1] + "." + new StringBuilder(parts[2]).reverse();

        for (String token : List.of(escalated, badSignature, "not-a-jwt")) {
            mockMvc.perform(get("/api/v1/admin/users/statistics").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
        }
    }

    @Test
    void tokensForAnotherAudienceOrOfAnotherTypeAreRejected() throws Exception {
        Session session = auth.registerNewUser();
        Instant now = Instant.now();
        String otherAudience = accessToken(session.userId(), now, now.plusSeconds(600), "at+jwt", "another-api");
        String notAnAccessToken = accessToken(session.userId(), now, now.plusSeconds(600), "JWT",
                jwtProperties.audience());

        for (String token : List.of(otherAudience, notAnAccessToken)) {
            mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
        }
    }

    @Test
    void adminEndpointsAreForbiddenForRegularUsersAndAvailableToAdministrators() throws Exception {
        Session user = auth.registerNewUser();
        mockMvc.perform(get("/api/v1/admin/users/statistics").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        String adminEmail = AuthTestClient.uniqueEmail();
        registrationService.createAccount(EmailAddress.of(adminEmail), AuthTestClient.PASSWORD, "Admin", Role.ADMIN);
        Session admin = auth.login(adminEmail, AuthTestClient.PASSWORD);

        mockMvc.perform(get("/api/v1/admin/users/statistics").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.administrators", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.registeredLast30Days", greaterThanOrEqualTo(2)));
    }

    @Test
    void unknownApiPathsAreNotFoundForAuthenticatedUsers() throws Exception {
        Session session = auth.registerNewUser();

        mockMvc.perform(get("/api/v1/does-not-exist").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        // Other users' data is never addressable by id: there is no such route.
        mockMvc.perform(get("/api/v1/users/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void operationalEndpointsArePublicButOthersAreNot() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_memory_used_bytes")));
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/.well-known/oauth-protected-resource"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resource_name").value("Stock Forecast Platform API"))
                .andExpect(jsonPath("$.tls_client_certificate_bound_access_tokens").value(false));
    }

    @Test
    void openApiDocumentIsPublishedAndDoesNotExposeThePrincipalAsAParameter() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/users/me'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/me'].get.parameters").doesNotExist())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.info.description", containsString("not be considered financial advice")));
    }

    @Test
    void corsPreflightIsOnlyAllowedForConfiguredOrigins() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));

        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    private String accessToken(UUID userId, Instant issuedAt, Instant expiresAt, String type, String audience) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .audience(List.of(audience))
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("email", "someone@example.com")
                .claim("roles", List.of("USER"))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type(type).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
