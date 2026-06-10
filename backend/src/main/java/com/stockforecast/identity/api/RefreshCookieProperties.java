package com.stockforecast.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Refresh-token cookie attributes. {@code secure} may only be disabled for plain-HTTP local development.
 */
@Validated
@ConfigurationProperties("app.security.refresh-cookie")
public record RefreshCookieProperties(
        @NotBlank @DefaultValue("sf_refresh_token") String name,
        @NotBlank @DefaultValue("/api/v1/auth") String path,
        @DefaultValue("true") boolean secure,
        @Pattern(regexp = "Strict|Lax") @DefaultValue("Strict") String sameSite) {
}
