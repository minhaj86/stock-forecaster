package com.stockforecast.identity.application;

import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Login-session (refresh token) policy.
 *
 * @param refreshTokenTtl  sliding lifetime of each refresh token
 * @param maxLifetime      absolute session lifetime; after it the user must sign in again
 * @param reuseGracePeriod window in which replaying a just-rotated token is treated as a benign race
 */
@Validated
@ConfigurationProperties("app.security.session")
public record SessionProperties(
        @NotNull @DurationMin(minutes = 1) @DefaultValue("7d") Duration refreshTokenTtl,
        @NotNull @DurationMin(minutes = 1) @DefaultValue("30d") Duration maxLifetime,
        @NotNull @DefaultValue("10s") Duration reuseGracePeriod) {
}
