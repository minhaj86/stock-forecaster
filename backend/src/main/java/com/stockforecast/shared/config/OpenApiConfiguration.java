package com.stockforecast.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document metadata. Every operation requires a bearer token unless it explicitly opts out with an
 * empty {@code @SecurityRequirements}.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    public static final String BEARER_SCHEME = "bearerAuth";

    static final String DESCRIPTION = """
            REST API of the Stock Forecast Platform: personalised watchlists, market data, analytics, \
            statistical forecasts and grounded AI comparisons.

            **Disclaimer:** Forecasts and AI-generated analysis are informational only and should not be \
            considered financial advice.""";

    @Bean
    OpenAPI stockForecastOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Stock Forecast Platform API")
                        .version("v1")
                        .description(DESCRIPTION)
                        .license(new License().name("Proprietary")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Short-lived access token from /api/v1/auth/login or /api/v1/auth/refresh")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
