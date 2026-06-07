package com.stockforecast.identity.infrastructure.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Access-token settings.
 *
 * @param secret               Base64-encoded HMAC key of at least 256 bits ({@code JWT_SECRET})
 * @param allowEphemeralSecret when no secret is configured, generate a random one at startup. Only for local
 *                             development and tests: tokens do not survive restarts and are not shared between
 *                             instances.
 */
@Validated
@ConfigurationProperties("app.security.jwt")
public record JwtProperties(
        @NotBlank @DefaultValue("stock-forecast") String issuer,
        @NotBlank @DefaultValue("stock-forecast-api") String audience,
        @NotNull @DurationMin(minutes = 1) @DurationMax(hours = 1) @DefaultValue("15m") Duration accessTokenTtl,
        String secret,
        @DefaultValue("false") boolean allowEphemeralSecret) {

    @Override
    public String toString() {
        return "JwtProperties[issuer=" + issuer + ", audience=" + audience + ", accessTokenTtl=" + accessTokenTtl
                + ", secret=***, allowEphemeralSecret=" + allowEphemeralSecret + "]";
    }
}
