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
 * @param provider           active provider ({@code MARKET_DATA_PROVIDER}): {@code synthetic} or {@code TWELVE_DATA}
 * @param historyYears       history loaded when a stock is first tracked ({@code MARKET_DATA_INITIAL_HISTORY_YEARS})
 * @param refreshOverlapDays trailing days re-fetched by incremental updates to pick up late corrections
 */
@Validated
@ConfigurationProperties("app.market-data")
public record MarketDataProperties(
        @NotBlank String provider,
        @Min(1) @Max(30) @DefaultValue("5") int historyYears,
        @Min(0) @Max(30) @DefaultValue("5") int refreshOverlapDays,
        @NotNull @DefaultValue Sync sync,
        @NotNull @DefaultValue Jobs jobs,
        @NotNull @DefaultValue Health health) {

    public MarketDataProperties {
        if (provider != null && !provider.isBlank() && MarketDataProviderType.fromConfiguration(provider).isEmpty()) {
            throw new IllegalArgumentException("Unknown market-data provider '%s' (MARKET_DATA_PROVIDER). Supported: %s"
                    .formatted(provider, MarketDataProviderType.supportedValues()));
        }
    }

    public MarketDataProviderType providerType() {
        return MarketDataProviderType.fromConfiguration(provider).orElseThrow();
    }

    /**
     * @param cron      schedule of the automatic synchronisation of watched stocks ({@code -} disables it)
     * @param onStartup queue a catch-up synchronisation when the application starts
     */
    public record Sync(@NotBlank @DefaultValue("0 30 22 * * MON-FRI") String cron,
                       @DefaultValue("true") boolean onStartup) {
    }

    /**
     * @param pollInterval   how often the worker looks for due jobs (it is also woken up when a job is queued)
     * @param maxAttempts    executions per job before a transient failure becomes final
     * @param retryBaseDelay first automatic retry delay; doubles per attempt (with jitter) up to retryMaxDelay
     * @param leaseTimeout   a running job without heartbeat for this long is considered abandoned and re-queued
     * @param runBudget      a worker run stops picking new jobs after this long (bounds the cluster-wide lock)
     * @param retention      finished jobs older than this are deleted
     */
    public record Jobs(@NotNull @DefaultValue("PT5S") Duration pollInterval,
                       @Min(1) @Max(20) @DefaultValue("5") int maxAttempts,
                       @NotNull @DefaultValue("PT1M") Duration retryBaseDelay,
                       @NotNull @DefaultValue("PT1H") Duration retryMaxDelay,
                       @NotNull @DefaultValue("PT10M") Duration leaseTimeout,
                       @NotNull @DefaultValue("PT5M") Duration runBudget,
                       @NotNull @DefaultValue("P90D") Duration retention) {
    }

    /**
     * @param cacheTtl           how long an active health check result is reused
     * @param minRefreshInterval forced checks are throttled to one per interval
     */
    public record Health(@NotNull @DefaultValue("PT1M") Duration cacheTtl,
                         @NotNull @DefaultValue("PT15S") Duration minRefreshInterval) {
    }
}
