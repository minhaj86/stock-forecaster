package com.stockforecast.identity.infrastructure.security;

import com.stockforecast.identity.AuthenticatedUser;
import com.stockforecast.identity.Role;
import com.stockforecast.identity.application.IssuedAccessToken;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtAccessTokenIssuerTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    private static final String SECRET = base64("an-hmac-secret-that-is-comfortably-longer-than-32-bytes");
    private static final AuthenticatedUser USER =
            new AuthenticatedUser(UUID.randomUUID(), "ada@example.com", Role.ADMIN);

    private final JwtConfiguration configuration = new JwtConfiguration();
    private final JwtProperties properties = properties("stock-forecast-api", SECRET);
    private final JwtSigningKey signingKey = configuration.jwtSigningKey(properties);
    private final JwtAccessTokenIssuer issuer =
            new JwtAccessTokenIssuer(configuration.jwtEncoder(signingKey), properties);

    @Test
    void issuedTokensCarryTheExpectedClaimsAndDecode() {
        IssuedAccessToken token = issuer.issue(USER, NOW);

        Jwt jwt = decoderAt(NOW.plusSeconds(60)).decode(token.value());

        assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(jwt.getSubject()).isEqualTo(USER.id().toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo("ada@example.com");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("stock-forecast");
        assertThat(jwt.getAudience()).containsExactly("stock-forecast-api");
        assertThat(jwt.getExpiresAt()).isEqualTo(token.expiresAt());
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getHeaders()).containsEntry("typ", "at+jwt").containsEntry("alg", "HS256");
    }

    @Test
    void expiredTokensAreRejectedOnceTheClockSkewToleranceHasPassed() {
        IssuedAccessToken token = issuer.issue(USER, NOW);
        Instant justAfterSkew = token.expiresAt().plusSeconds(31);

        assertThatThrownBy(() -> decoderAt(justAfterSkew).decode(token.value()))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void tokensSignedWithAnotherKeyAreRejected() {
        JwtProperties otherProperties = properties("stock-forecast-api", base64("a-completely-different-secret-of-sufficient-size"));
        JwtSigningKey otherKey = configuration.jwtSigningKey(otherProperties);
        String forged = new JwtAccessTokenIssuer(configuration.jwtEncoder(otherKey), otherProperties)
                .issue(USER, NOW).value();

        assertThatThrownBy(() -> decoderAt(NOW).decode(forged)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void tokensForAnotherAudienceAreRejected() {
        String token = new JwtAccessTokenIssuer(configuration.jwtEncoder(signingKey),
                properties("some-other-api", SECRET)).issue(USER, NOW).value();

        assertThatThrownBy(() -> decoderAt(NOW).decode(token)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void jwtsThatAreNotAccessTokensAreRejected() {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("stock-forecast").audience(List.of("stock-forecast-api")).subject(USER.id().toString())
                .issuedAt(NOW).expiresAt(NOW.plusSeconds(600)).claim("email", USER.email())
                .claim("roles", List.of("USER")).build();
        String idTokenLike = configuration.jwtEncoder(signingKey).encode(JwtEncoderParameters.from(
                JwsHeader.with(JwtConfiguration.ALGORITHM).type("JWT").build(), claims)).getTokenValue();

        assertThatThrownBy(() -> decoderAt(NOW).decode(idTokenLike)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void signingKeyMustBeConfiguredUnlessEphemeralKeysAreExplicitlyAllowed() {
        assertThatThrownBy(() -> JwtSigningKey.from(properties("aud", null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
        assertThat(JwtSigningKey.from(new JwtProperties("iss", "aud", Duration.ofMinutes(15), " ", true)).key()
                .getEncoded()).hasSizeGreaterThanOrEqualTo(JwtSigningKey.MINIMUM_KEY_BYTES);
    }

    @Test
    void signingKeyMustBeValidBase64OfAtLeast256Bits() {
        assertThatThrownBy(() -> JwtSigningKey.from(properties("aud", base64("too-short"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("256 bits");
        assertThatThrownBy(() -> JwtSigningKey.from(properties("aud", "%%% not base64 %%%")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Base64");
    }

    @Test
    void secretsAreNeverRenderedInToString() {
        assertThat(properties.toString()).doesNotContain(SECRET);
        assertThat(signingKey.toString()).doesNotContain(SECRET);
    }

    private JwtDecoder decoderAt(Instant instant) {
        return configuration.jwtDecoder(signingKey, properties, Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static JwtProperties properties(String audience, String secret) {
        return new JwtProperties("stock-forecast", audience, Duration.ofMinutes(15), secret, false);
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
