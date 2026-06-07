package com.stockforecast.identity.infrastructure.security;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * @param bcryptStrength BCrypt work factor (log2 rounds). 12 is a sensible production default; tests lower it.
 */
@Validated
@ConfigurationProperties("app.security.password")
public record PasswordProperties(@Min(4) @Max(16) @DefaultValue("12") int bcryptStrength) {
}
