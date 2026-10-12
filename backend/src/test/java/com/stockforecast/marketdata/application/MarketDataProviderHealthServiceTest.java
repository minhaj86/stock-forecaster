package com.stockforecast.marketdata.application;

import com.stockforecast.marketdata.MarketDataProviders.ProviderUsage;
import com.stockforecast.marketdata.PriceHistory.ProviderInfo;
import com.stockforecast.marketdata.ProviderHealth;
import com.stockforecast.marketdata.ProviderHealth.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketDataProviderHealthServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-11T22:00:00Z");
    private static final MarketDataProperties PROPERTIES = new MarketDataProperties("synthetic", 5, 5,
            new MarketDataProperties.Sync("-", false),
            new MarketDataProperties.Jobs(Duration.ofSeconds(5), 5, Duration.ofMinutes(1), Duration.ofHours(1),
                    Duration.ofMinutes(10), Duration.ofMinutes(5), Duration.ofDays(90)),
            new MarketDataProperties.Health(Duration.ofMinutes(1), Duration.ofSeconds(15)));

    @Mock
    private MarketDataProvider provider;
    @Mock
    private ProviderUsageRecorder usage;

    private MarketDataProviderHealthService service;

    @BeforeEach
    void setUp() {
        lenient().when(provider.info()).thenReturn(new ProviderInfo("fake", "Fake provider", false, "test"));
        lenient().when(provider.isConfigured()).thenReturn(true);
        lenient().when(usage.today(any(), any())).thenReturn(usage(null, null, null));
        service = new MarketDataProviderHealthService(provider, usage, ProviderThrottle.UNLIMITED, PROPERTIES,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void activeChecksAreCachedSoTheQuotaIsNotUsedUp() {
        when(provider.checkHealth()).thenReturn(healthy(NOW));

        assertThat(service.check().status()).isEqualTo(Status.HEALTHY);
        assertThat(service.check(false).status()).isEqualTo(Status.HEALTHY);
        assertThat(service.check(true).status()).isEqualTo(Status.HEALTHY);

        verify(provider, times(1)).checkHealth();
    }

    @Test
    void providerFailuresAreClassified() {
        when(provider.checkHealth()).thenThrow(new MarketDataProviderException(ProviderErrorCode.AUTHENTICATION_FAILED,
                "Twelve Data rejected the API key"));

        ProviderHealth health = service.check(true);

        assertThat(health.status()).isEqualTo(Status.MISCONFIGURED);
        assertThat(health.message()).isEqualTo("Twelve Data rejected the API key");
    }

    @Test
    void aMissingConfigurationIsReportedWithoutCallingTheProvider() {
        when(provider.isConfigured()).thenReturn(false);

        assertThat(service.check(true).status()).isEqualTo(Status.MISCONFIGURED);
        assertThat(service.lastKnown().status()).isEqualTo(Status.MISCONFIGURED);
        verify(provider, never()).checkHealth();
    }

    @Test
    void withoutAnActiveCheckTheHealthIsDerivedFromTheLatestRequest() {
        assertThat(service.lastKnown().status()).isEqualTo(Status.UNKNOWN);

        when(usage.today(any(), any())).thenReturn(usage(NOW.minusSeconds(60), null, null));
        assertThat(service.lastKnown().status()).isEqualTo(Status.HEALTHY);

        when(usage.today(any(), any())).thenReturn(usage(NOW.minusSeconds(60), NOW.minusSeconds(5), "HTTP 503"));
        assertThat(service.lastKnown()).satisfies(health -> {
            assertThat(health.status()).isEqualTo(Status.DEGRADED);
            assertThat(health.message()).contains("HTTP 503");
        });
        verify(provider, never()).checkHealth();
    }

    @Test
    void newerRequestOutcomesOverrideAnOlderActiveCheck() {
        when(provider.checkHealth()).thenReturn(healthy(NOW.minusSeconds(120)));
        service.check(true);
        when(usage.today(any(), any())).thenReturn(usage(NOW.minusSeconds(300), NOW.minusSeconds(10), "timeout"));

        assertThat(service.lastKnown().status()).isEqualTo(Status.DEGRADED);
    }

    private static ProviderHealth healthy(Instant checkedAt) {
        return new ProviderHealth("fake", Status.HEALTHY, checkedAt, 12L, "reachable", Map.of());
    }

    private static ProviderUsage usage(Instant lastSuccess, Instant lastFailure, String message) {
        return new ProviderUsage(0, 0, 0, lastSuccess, lastFailure, message, null);
    }
}
