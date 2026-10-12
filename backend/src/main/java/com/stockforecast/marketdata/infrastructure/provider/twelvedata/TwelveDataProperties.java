package com.stockforecast.marketdata.infrastructure.provider.twelvedata;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Twelve Data adapter settings (only bound when {@code MARKET_DATA_PROVIDER=TWELVE_DATA}).
 *
 * @param apiKey            {@code TWELVE_DATA_API_KEY}; sent in the {@code Authorization} header, never logged
 * @param requestsPerMinute API credits per minute of the subscribed plan (Basic: 8); every vendor request costs one
 * @param maxWait           how long a caller waits for a free request before the call counts as rate limited
 * @param maxBarsPerRequest vendor limit per time-series request; longer ranges are split
 */
@Validated
@ConfigurationProperties("app.market-data.twelve-data")
public record TwelveDataProperties(
        @NotBlank @DefaultValue("https://api.twelvedata.com") String baseUrl,
        String apiKey,
        @NotNull @DefaultValue("5s") Duration connectTimeout,
        @NotNull @DefaultValue("20s") Duration readTimeout,
        @Min(1) @Max(100_000) @DefaultValue("8") int requestsPerMinute,
        @NotNull @DefaultValue("30s") Duration maxWait,
        @Min(100) @Max(5000) @DefaultValue("5000") int maxBarsPerRequest) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String toString() {
        return "TwelveDataProperties[baseUrl=" + baseUrl + ", apiKey=" + (hasApiKey() ? "***" : "<missing>")
                + ", requestsPerMinute=" + requestsPerMinute + "]";
    }
}
