package com.stockforecast.marketdata.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param provider           active provider id ({@code MARKET_DATA_PROVIDER}), e.g. {@code synthetic}
 * @param historyYears       history fetched when a stock is first tracked
 * @param refreshOverlapDays trailing days re-fetched on every sync to pick up late corrections
 * @param leaseTimeout       a synchronisation lease older than this is considered abandoned
 */
@Validated
@ConfigurationProperties("app.market-data")
public record MarketDataProperties(
        @NotBlank String provider,
        @Min(1) @Max(20) @DefaultValue("5") int historyYears,
        @Min(0) @Max(30) @DefaultValue("5") int refreshOverlapDays,
        @NotNull @DefaultValue("10m") Duration leaseTimeout,
        @NotNull @DefaultValue Sync sync) {

    /**
     * @param cron      schedule of the full synchronisation ({@code -} disables it)
     * @param onStartup run a catch-up synchronisation when the application starts
     */
    public record Sync(@NotBlank @DefaultValue("0 30 22 * * MON-FRI") String cron,
                       @DefaultValue("true") boolean onStartup) {
    }
}
