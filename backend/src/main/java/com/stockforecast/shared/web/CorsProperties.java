package com.stockforecast.shared.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Browser origins allowed to call the API cross-origin. Empty means same-origin only (the default when the
 * SPA is served behind the same reverse proxy as the API).
 */
@ConfigurationProperties("app.cors")
public record CorsProperties(
        @DefaultValue List<String> allowedOrigins,
        @DefaultValue("1h") Duration maxAge) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins.stream()
                .map(String::strip)
                .filter(origin -> !origin.isEmpty())
                .toList();
        if (allowedOrigins.contains("*")) {
            throw new IllegalArgumentException(
                    "app.cors.allowed-origins must list explicit origins; '*' cannot be combined with credentials");
        }
    }
}
