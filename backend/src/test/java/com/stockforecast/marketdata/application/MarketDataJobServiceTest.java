package com.stockforecast.marketdata.application;

import com.stockforecast.marketdata.ExistingDataStrategy;
import com.stockforecast.marketdata.MarketDataInterval;
import com.stockforecast.marketdata.MarketDataJobStatus;
import com.stockforecast.marketdata.MarketDataJobTrigger;
import com.stockforecast.marketdata.MarketDataJobType;
import com.stockforecast.marketdata.MarketDataJobs.BackfillRequest;
import com.stockforecast.marketdata.MarketDataJobs.JobSubmission;
import com.stockforecast.marketdata.MarketDataJobs.SyncSummary;
import com.stockforecast.marketdata.MarketDataStatus;
import com.stockforecast.marketdata.PriceHistory.ProviderInfo;
import com.stockforecast.marketdata.ProviderCapabilities;
import com.stockforecast.marketdata.domain.MarketDataExceptions.InvalidDateRangeException;
import com.stockforecast.marketdata.domain.MarketDataExceptions.StockInactiveException;
import com.stockforecast.marketdata.domain.MarketDataJob;
import com.stockforecast.marketdata.domain.MarketDataJobRepository;
import com.stockforecast.marketdata.domain.MarketDataJobSearch;
import com.stockforecast.stock.AssetType;
import com.stockforecast.stock.StockCatalog;
import com.stockforecast.stock.StockRef;
import com.stockforecast.watchlist.WatchlistQueries;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketDataJobServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-11T22:00:00Z");
    private static final UUID ADMIN = UUID.randomUUID();
    private static final StockRef AAPL = new StockRef(UUID.randomUUID(), "AAPL", "Apple Inc.", "NASDAQ", "XNAS",
            "USD", "US", "Information Technology", "Hardware", AssetType.COMMON_STOCK);
    private static final MarketDataProperties PROPERTIES = new MarketDataProperties("synthetic", 5, 5,
            new MarketDataProperties.Sync("-", false),
            new MarketDataProperties.Jobs(Duration.ofSeconds(5), 5, Duration.ofMinutes(1), Duration.ofHours(1),
                    Duration.ofMinutes(10), Duration.ofMinutes(5), Duration.ofDays(90)),
            new MarketDataProperties.Health(Duration.ofMinutes(1), Duration.ofSeconds(15)));

    @Mock
    private MarketDataJobRepository jobs;
    @Mock
    private MarketDataJobSearch jobSearch;
    @Mock
    private StockCatalog stockCatalog;
    @Mock
    private WatchlistQueries watchlists;
    @Mock
    private MarketDataStore store;
    @Mock
    private MarketDataProvider provider;
    @Mock
    private ApplicationEventPublisher events;

    private MarketDataJobService service;

    @BeforeEach
    void setUp() {
        lenient().when(provider.info()).thenReturn(new ProviderInfo("fake", "Fake provider", false, "test"));
        lenient().when(provider.capabilities()).thenReturn(new ProviderCapabilities(
                Set.of(MarketDataInterval.ONE_DAY), true, true, true, 5000, "test"));
        lenient().when(stockCatalog.findById(AAPL.id())).thenReturn(Optional.of(AAPL));
        lenient().when(stockCatalog.findAllActiveByIds(List.of(AAPL.id()))).thenReturn(List.of(AAPL));
        lenient().when(stockCatalog.findAllByIds(any())).thenReturn(List.of(AAPL));
        lenient().when(jobs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new MarketDataJobService(jobs, jobSearch, new MarketDataJobViews(stockCatalog), stockCatalog,
                watchlists, store, provider, PROPERTIES, events, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void queuesABackfillTracksTheStockAndWakesTheWorker() {
        JobSubmission submission = service.requestBackfill(AAPL.id(), new BackfillRequest(LocalDate.of(2015, 1, 1),
                LocalDate.of(2026, 10, 9), null, null), ADMIN);

        assertThat(submission.created()).isTrue();
        assertThat(submission.job().status()).isEqualTo(MarketDataJobStatus.QUEUED);
        assertThat(submission.job().existingDataStrategy()).isEqualTo(ExistingDataStrategy.SKIP);
        assertThat(submission.job().interval()).isEqualTo(MarketDataInterval.ONE_DAY);
        assertThat(submission.job().symbol()).isEqualTo("AAPL");
        assertThat(submission.job().maxAttempts()).isEqualTo(5);
        verify(store).ensureTracked(AAPL.id(), NOW);
        verify(events).publishEvent(any(MarketDataJobQueued.class));
    }

    @Test
    void identicalActiveRequestsReturnTheExistingJob() {
        MarketDataJob active = MarketDataJob.queue(new MarketDataJob.Request(MarketDataJobType.AUTOMATIC_SYNC,
                MarketDataJobTrigger.SCHEDULE, AAPL.id(), MarketDataInterval.ONE_DAY, null, null, null, null, null),
                "fake", 5, NOW);
        when(jobs.findByStockIdInAndStatusIn(any(), anyCollection())).thenReturn(List.of(active));

        JobSubmission submission = service.requestLatestUpdate(AAPL.id(), ADMIN);

        assertThat(submission.created()).isFalse();
        assertThat(submission.job().id()).isEqualTo(active.getId());
        verify(jobs, never()).save(any());
    }

    @Test
    void validatesRequestedRanges() {
        assertThatThrownBy(() -> service.requestBackfill(AAPL.id(), new BackfillRequest(LocalDate.of(2026, 10, 9),
                LocalDate.of(2026, 10, 1), null, null), ADMIN)).isInstanceOf(InvalidDateRangeException.class);
        assertThatThrownBy(() -> service.requestBackfill(AAPL.id(), new BackfillRequest(LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 12), null, null), ADMIN)).isInstanceOf(InvalidDateRangeException.class)
                .hasMessageContaining("future");
        assertThatThrownBy(() -> service.requestBackfill(AAPL.id(), new BackfillRequest(LocalDate.of(1960, 1, 1),
                LocalDate.of(2026, 10, 1), null, null), ADMIN)).isInstanceOf(InvalidDateRangeException.class);
    }

    @Test
    void inactiveStocksCannotBeSynchronised() {
        when(stockCatalog.findAllActiveByIds(List.of(AAPL.id()))).thenReturn(List.of());

        assertThatThrownBy(() -> service.requestLatestUpdate(AAPL.id(), ADMIN))
                .isInstanceOf(StockInactiveException.class);
    }

    @Test
    void watchingAStockWithoutDataQueuesItsInitialHistory() {
        when(store.findStatuses(List.of(AAPL.id()))).thenReturn(Map.of());

        service.onStockWatched(AAPL.id());

        ArgumentCaptor<MarketDataJob> queued = ArgumentCaptor.forClass(MarketDataJob.class);
        verify(jobs).save(queued.capture());
        assertThat(queued.getValue().getType()).isEqualTo(MarketDataJobType.INITIAL_LOAD);
        assertThat(queued.getValue().getTrigger()).isEqualTo(MarketDataJobTrigger.WATCHLIST);
        assertThat(queued.getValue().getRequestedBy()).isNull();
    }

    @Test
    void watchingAStockWithStaleDataCatchesUpButCurrentDataNeedsNothing() {
        when(store.findStatuses(List.of(AAPL.id()))).thenReturn(Map.of(AAPL.id(), status(LocalDate.of(2026, 9, 30))));
        service.onStockWatched(AAPL.id());
        ArgumentCaptor<MarketDataJob> queued = ArgumentCaptor.forClass(MarketDataJob.class);
        verify(jobs).save(queued.capture());
        assertThat(queued.getValue().getType()).isEqualTo(MarketDataJobType.AUTOMATIC_SYNC);

        // Sunday: Friday's bar is the newest completed weekday.
        when(store.findStatuses(List.of(AAPL.id()))).thenReturn(Map.of(AAPL.id(), status(LocalDate.of(2026, 10, 9))));
        service.onStockWatched(AAPL.id());
        verify(jobs).save(any());
    }

    @Test
    void scheduledSynchronisationSkipsStocksThatAlreadyHaveAnActiveJob() {
        StockRef msft = new StockRef(UUID.randomUUID(), "MSFT", "Microsoft", "NASDAQ", "XNAS", "USD", "US", null,
                null, AssetType.COMMON_STOCK);
        when(watchlists.findAllWatchedStockIds()).thenReturn(Set.of(AAPL.id(), msft.id()));
        when(stockCatalog.findAllActiveByIds(Set.of(AAPL.id(), msft.id()))).thenReturn(List.of(AAPL, msft));
        when(stockCatalog.findAllActiveByIds(List.of(msft.id()))).thenReturn(List.of(msft));
        lenient().when(stockCatalog.findById(msft.id())).thenReturn(Optional.of(msft));
        MarketDataJob running = MarketDataJob.queue(new MarketDataJob.Request(MarketDataJobType.BACKFILL,
                MarketDataJobTrigger.ADMIN, AAPL.id(), MarketDataInterval.ONE_DAY, ExistingDataStrategy.SKIP,
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31), ADMIN, null), "fake", 5, NOW);
        when(jobs.findByStockIdInAndStatusIn(any(), anyCollection())).thenAnswer(invocation ->
                invocation.<Collection<UUID>>getArgument(0).contains(AAPL.id()) ? List.of(running)
                        : List.of());

        SyncSummary summary = service.queueScheduledSynchronisation();

        assertThat(summary).isEqualTo(new SyncSummary(2, 1));
        ArgumentCaptor<MarketDataJob> queued = ArgumentCaptor.forClass(MarketDataJob.class);
        verify(jobs).save(queued.capture());
        assertThat(queued.getValue().getStockId()).isEqualTo(msft.id());
        assertThat(queued.getValue().getTrigger()).isEqualTo(MarketDataJobTrigger.SCHEDULE);
    }

    private static MarketDataStatus status(LocalDate lastBar) {
        return new MarketDataStatus(AAPL.id(), MarketDataStatus.State.READY, "fake", lastBar, NOW, NOW, null);
    }
}
