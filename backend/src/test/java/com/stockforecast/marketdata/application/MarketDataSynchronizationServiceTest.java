package com.stockforecast.marketdata.application;

import com.stockforecast.marketdata.ExistingDataStrategy;
import com.stockforecast.marketdata.MarketDataInterval;
import com.stockforecast.marketdata.MarketDataJobStatus;
import com.stockforecast.marketdata.MarketDataJobTrigger;
import com.stockforecast.marketdata.MarketDataJobType;
import com.stockforecast.marketdata.MarketDataUpdated;
import com.stockforecast.marketdata.PriceHistory.ProviderInfo;
import com.stockforecast.marketdata.ProviderCapabilities;
import com.stockforecast.marketdata.application.MarketDataBackfillService.BackfillResult;
import com.stockforecast.marketdata.application.MarketDataStore.Coverage;
import com.stockforecast.marketdata.domain.JobCounters;
import com.stockforecast.marketdata.domain.MarketDataJob;
import com.stockforecast.marketdata.domain.MarketDataJobRepository;
import com.stockforecast.stock.AssetType;
import com.stockforecast.stock.StockCatalog;
import com.stockforecast.stock.StockRef;
import com.stockforecast.support.NoOpTransactions;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketDataSynchronizationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-11T22:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 11);
    private static final StockRef AAPL = new StockRef(UUID.randomUUID(), "AAPL", "Apple Inc.", "NASDAQ", "XNAS",
            "USD", "US", "Information Technology", "Hardware", AssetType.COMMON_STOCK);
    private static final MarketDataProperties PROPERTIES = new MarketDataProperties("synthetic", 5, 5,
            new MarketDataProperties.Sync("-", false),
            new MarketDataProperties.Jobs(Duration.ofSeconds(5), 3, Duration.ofMinutes(1), Duration.ofHours(1),
                    Duration.ofMinutes(10), Duration.ofMinutes(5), Duration.ofDays(90)),
            new MarketDataProperties.Health(Duration.ofMinutes(1), Duration.ofSeconds(15)));

    @Mock
    private MarketDataJobRepository jobs;
    @Mock
    private StockCatalog stockCatalog;
    @Mock
    private MarketDataStore store;
    @Mock
    private MarketDataBackfillService backfill;
    @Mock
    private MarketDataRepopulationService repopulation;
    @Mock
    private MarketDataProvider provider;
    @Mock
    private ApplicationEventPublisher events;

    private MarketDataSynchronizationService service;

    @BeforeEach
    void setUp() {
        lenient().when(provider.info()).thenReturn(new ProviderInfo("fake", "Fake provider", false, "test"));
        lenient().when(provider.capabilities()).thenReturn(new ProviderCapabilities(
                Set.of(MarketDataInterval.ONE_DAY), true, true, true, 5000, "test"));
        lenient().when(provider.isConfigured()).thenReturn(true);
        lenient().when(stockCatalog.findById(AAPL.id())).thenReturn(Optional.of(AAPL));
        lenient().when(stockCatalog.findAllActiveByIds(List.of(AAPL.id()))).thenReturn(List.of(AAPL));
        lenient().when(jobs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new MarketDataSynchronizationService(jobs, stockCatalog, store, backfill, repopulation, provider,
                PROPERTIES, NoOpTransactions.template(), events, Clock.fixed(NOW, ZoneOffset.UTC),
                new SimpleMeterRegistry());
    }

    @Test
    void incrementalJobsFetchFromTheNewestStoredBarWithACorrectionWindow() {
        MarketDataJob job = queued(MarketDataJobType.LATEST_UPDATE, null, null);
        when(store.findCoverage(List.of(AAPL.id()))).thenReturn(Map.of(AAPL.id(),
                new Coverage(LocalDate.of(2021, 10, 11), LocalDate.of(2026, 10, 5), 1258)));
        when(backfill.backfill(eq(AAPL), eq(LocalDate.of(2026, 10, 1)), eq(TODAY), eq(MarketDataInterval.ONE_DAY),
                eq(ExistingDataStrategy.UPDATE), any())).thenReturn(new BackfillResult(
                new JobCounters(7, 4, 0, 3, 0, 0), false, null));

        assertThat(service.processQueue()).isEqualTo(1);

        assertThat(job.getStatus()).isEqualTo(MarketDataJobStatus.COMPLETED);
        assertThat(job.getFromDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(job.getToDate()).isEqualTo(TODAY);
        ArgumentCaptor<MarketDataUpdated> updated = ArgumentCaptor.forClass(MarketDataUpdated.class);
        verify(events).publishEvent(updated.capture());
        assertThat(updated.getValue().inserted()).isEqualTo(4);
        assertThat(updated.getValue().historyReplaced()).isFalse();
    }

    @Test
    void stocksWithoutDataLoadTheConfiguredHistory() {
        queued(MarketDataJobType.INITIAL_LOAD, null, null);
        when(store.findCoverage(List.of(AAPL.id()))).thenReturn(Map.of());
        when(backfill.backfill(eq(AAPL), eq(TODAY.minusYears(5)), eq(TODAY), any(), any(), any()))
                .thenReturn(new BackfillResult(JobCounters.NONE, false, null));

        service.processQueue();

        verify(backfill).backfill(eq(AAPL), eq(TODAY.minusYears(5)), eq(TODAY), any(), any(), any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void backfillsUseTheRequestedRangeAndStrategy() {
        queued(MarketDataJobType.BACKFILL, LocalDate.of(2015, 1, 1), LocalDate.of(2026, 10, 9));
        when(backfill.backfill(eq(AAPL), eq(LocalDate.of(2015, 1, 1)), eq(LocalDate.of(2026, 10, 9)), any(),
                eq(ExistingDataStrategy.SKIP), any())).thenReturn(new BackfillResult(
                new JobCounters(2711, 1930, 0, 781, 0, 0), false, null));

        service.processQueue();

        verify(store).recordAttempt(AAPL.id(), NOW);
    }

    @Test
    void rateLimitsDeferTheJobWithoutUsingAnAttemptAndEndTheRun() {
        MarketDataJob job = queued(MarketDataJobType.BACKFILL, LocalDate.of(2015, 1, 1), LocalDate.of(2026, 10, 9));
        when(backfill.backfill(any(), any(), any(), any(), any(), any())).thenThrow(new MarketDataProviderException(
                ProviderErrorCode.RATE_LIMITED, "rate limited", Duration.ofSeconds(30), null));

        service.processQueue();

        assertThat(job.getStatus()).isEqualTo(MarketDataJobStatus.QUEUED);
        assertThat(job.getAttempts()).isZero();
        assertThat(job.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(job.getErrorCode()).isEqualTo("PROVIDER_RATE_LIMITED");
        verify(jobs, times(1)).claimNextRunnable(any());
        verify(store, never()).recordFailure(any(), any(), any());
    }

    @Test
    void transientFailuresAreRetriedWithBackoff() {
        MarketDataJob job = queued(MarketDataJobType.BACKFILL, LocalDate.of(2015, 1, 1), LocalDate.of(2026, 10, 9));
        when(backfill.backfill(any(), any(), any(), any(), any(), any())).thenThrow(new MarketDataProviderException(
                ProviderErrorCode.TIMEOUT, "Twelve Data did not respond in time"));

        service.processQueue();

        assertThat(job.getStatus()).isEqualTo(MarketDataJobStatus.QUEUED);
        assertThat(job.getErrorCode()).isEqualTo("PROVIDER_TIMEOUT");
        assertThat(job.getNextAttemptAt()).isBetween(NOW.plusSeconds(48), NOW.plusSeconds(72));
        verify(store).recordFailure(eq(AAPL.id()), any(), eq(NOW));
    }

    @Test
    void permanentFailuresAreNotRetried() {
        MarketDataJob job = queued(MarketDataJobType.BACKFILL, LocalDate.of(2015, 1, 1), LocalDate.of(2026, 10, 9));
        when(backfill.backfill(any(), any(), any(), any(), any(), any())).thenThrow(new MarketDataProviderException(
                ProviderErrorCode.AUTHENTICATION_FAILED, "Twelve Data rejected the API key"));

        service.processQueue();

        assertThat(job.getStatus()).isEqualTo(MarketDataJobStatus.FAILED);
        assertThat(job.getErrorCode()).isEqualTo("PROVIDER_AUTHENTICATION_FAILED");
        assertThat(job.canBeRetried()).isFalse();
    }

    @Test
    void invalidProviderDataFailsTheJobWithItsCounters() {
        MarketDataJob job = queued(MarketDataJobType.BACKFILL, LocalDate.of(2015, 1, 1), LocalDate.of(2026, 10, 9));
        when(backfill.backfill(any(), any(), any(), any(), any(), any())).thenThrow(new JobExecutionException(
                "INVALID_PROVIDER_DATA", "duplicate bars", new JobCounters(10, 0, 0, 0, 10, 0)));

        service.processQueue();

        assertThat(job.getStatus()).isEqualTo(MarketDataJobStatus.FAILED);
        assertThat(job.getErrorCode()).isEqualTo("INVALID_PROVIDER_DATA");
        assertThat(job.getCounters().failed()).isEqualTo(10);
    }

    @Test
    void repopulationsPublishThatHistoryWasReplaced() {
        MarketDataJob job = queued(MarketDataJobType.REPOPULATE, LocalDate.of(2010, 1, 1), LocalDate.of(2026, 10, 9));
        when(repopulation.repopulate(eq(job.getId()), eq(AAPL), any(), any(), any(), any()))
                .thenReturn(new JobCounters(4218, 0, 12, 4206, 0, 1));

        service.processQueue();

        ArgumentCaptor<MarketDataUpdated> updated = ArgumentCaptor.forClass(MarketDataUpdated.class);
        verify(events).publishEvent(updated.capture());
        assertThat(updated.getValue().historyReplaced()).isTrue();
        assertThat(job.getCounters().deleted()).isEqualTo(1);
    }

    @Test
    void inactiveStocksAreNotSynchronised() {
        MarketDataJob job = queued(MarketDataJobType.LATEST_UPDATE, null, null);
        when(stockCatalog.findAllActiveByIds(List.of(AAPL.id()))).thenReturn(List.of());

        service.processQueue();

        assertThat(job.getStatus()).isEqualTo(MarketDataJobStatus.FAILED);
        assertThat(job.getErrorCode()).isEqualTo("STOCK_INACTIVE");
        verify(backfill, never()).backfill(any(), any(), any(), any(), any(), any());
    }

    @Test
    void backoffDoublesWithJitterAndIsCapped() {
        Duration base = Duration.ofMinutes(1);
        Duration max = Duration.ofHours(1);

        assertThat(MarketDataSynchronizationService.backoff(1, base, max, 0.5)).isEqualTo(Duration.ofMinutes(1));
        assertThat(MarketDataSynchronizationService.backoff(3, base, max, 0.5)).isEqualTo(Duration.ofMinutes(4));
        assertThat(MarketDataSynchronizationService.backoff(3, base, max, 0.0)).isEqualTo(Duration.ofSeconds(192));
        assertThat(MarketDataSynchronizationService.backoff(30, base, max, 0.5)).isEqualTo(Duration.ofHours(1));
    }

    private MarketDataJob queued(MarketDataJobType type, LocalDate from, LocalDate to) {
        MarketDataJob job = MarketDataJob.queue(new MarketDataJob.Request(type, MarketDataJobTrigger.ADMIN, AAPL.id(),
                MarketDataInterval.ONE_DAY, type == MarketDataJobType.BACKFILL ? ExistingDataStrategy.SKIP : null,
                from, to, UUID.randomUUID(), null), "fake", 3, NOW.minusSeconds(5));
        when(jobs.claimNextRunnable(any())).thenReturn(Optional.of(job), Optional.empty());
        lenient().when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        return job;
    }
}
