package com.stockforecast.marketdata.application;

import com.stockforecast.marketdata.MarketDataInterval;
import com.stockforecast.marketdata.PriceHistory.ProviderInfo;
import com.stockforecast.marketdata.application.MarketDataProvider.HistoricalPrice;
import com.stockforecast.marketdata.application.MarketDataProvider.HistoricalPrices;
import com.stockforecast.marketdata.application.MarketDataProvider.ProviderInstrument;
import com.stockforecast.marketdata.application.MarketDataStore.ReplaceResult;
import com.stockforecast.marketdata.application.MarketDataStore.StagedSummary;
import com.stockforecast.marketdata.domain.JobCounters;
import com.stockforecast.stock.AssetType;
import com.stockforecast.stock.StockRef;
import com.stockforecast.support.NoOpTransactions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static com.stockforecast.marketdata.application.MarketDataValidationServiceTest.bar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketDataRepopulationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-11T22:00:00Z");
    private static final StockRef AAPL = new StockRef(UUID.randomUUID(), "AAPL", "Apple Inc.", "NASDAQ", "XNAS",
            "USD", "US", "Information Technology", "Hardware", AssetType.COMMON_STOCK);
    private static final UUID JOB = UUID.randomUUID();
    private static final LocalDate FROM = LocalDate.of(2010, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 9);
    private static final ProviderInstrument INSTRUMENT = new ProviderInstrument("AAPL", "XNAS");

    @Mock
    private MarketDataProvider provider;
    @Mock
    private MarketDataStore store;

    private MarketDataRepopulationService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        lenient().when(provider.info()).thenReturn(new ProviderInfo("fake", "Fake provider", false, "test"));
        service = new MarketDataRepopulationService(provider, new MarketDataValidationService(clock), store,
                NoOpTransactions.template(), clock);
    }

    @Test
    void providerFailureLeavesTheStoredHistoryUntouched() {
        when(provider.getHistoricalPrices(INSTRUMENT, FROM, TO, MarketDataInterval.ONE_DAY))
                .thenThrow(new MarketDataProviderException(ProviderErrorCode.UNAVAILABLE, "HTTP 503"));

        assertThatThrownBy(() -> repopulate()).isInstanceOf(MarketDataProviderException.class);

        verify(store, never()).stageBars(any(), anyList());
        verify(store, never()).replaceWithStaged(any(), any(), any(), any(), any(), any());
        verify(store, never()).recordSuccess(any(), any(), any());
    }

    @Test
    void anyInvalidBarAbortsTheReplacement() {
        provides(bar("2026-10-01", "100", "101", "99", "100.5", "100.5", 1000L),
                bar("2026-10-02", "100", "98", "99", "100.5", "100.5", 1000L));

        assertThatThrownBy(this::repopulate).isInstanceOf(JobExecutionException.class)
                .hasMessageContaining("completely valid dataset")
                .satisfies(error -> assertThat(((JobExecutionException) error).counters().failed()).isEqualTo(1));
        verify(store, never()).stageBars(any(), anyList());
        verify(store, never()).replaceWithStaged(any(), any(), any(), any(), any(), any());
    }

    @Test
    void anEmptyAnswerNeverDeletesStoredData() {
        provides();
        when(store.countBars(AAPL.id(), FROM, TO)).thenReturn(4218L);

        assertThatThrownBy(this::repopulate).isInstanceOf(JobExecutionException.class)
                .satisfies(error -> assertThat(((JobExecutionException) error).errorCode())
                        .isEqualTo("REPOPULATION_EMPTY_RESULT"));
        verify(store, never()).replaceWithStaged(any(), any(), any(), any(), any(), any());
    }

    @Test
    void stagesVerifiesAndReplacesOnlyTheSpanTheProviderCovered() {
        provides(bar("2015-01-02", "100", "101", "99", "100.5", "90.5", 1000L),
                bar("2026-10-09", "200", "201", "199", "200.5", "200.5", 1000L));
        when(store.summarizeStaged(JOB)).thenReturn(new StagedSummary(2, LocalDate.of(2015, 1, 2),
                LocalDate.of(2026, 10, 9)));
        when(store.replaceWithStaged(AAPL.id(), JOB, LocalDate.of(2015, 1, 2), TO, "fake", NOW))
                .thenReturn(new ReplaceResult(1, 1, 0, 3));

        JobCounters counters = repopulate();

        assertThat(counters).isEqualTo(new JobCounters(2, 1, 1, 0, 0, 3));
        InOrder order = inOrder(store);
        order.verify(store).clearStaged(JOB);
        order.verify(store).stageBars(any(), anyList());
        order.verify(store).summarizeStaged(JOB);
        order.verify(store).replaceWithStaged(AAPL.id(), JOB, LocalDate.of(2015, 1, 2), TO, "fake", NOW);
        order.verify(store).recordSuccess(AAPL.id(), "fake", NOW);
    }

    @Test
    void aStagingMismatchAbortsBeforeTheSwap() {
        provides(bar("2026-10-08", "100", "101", "99", "100.5", "100.5", 1000L),
                bar("2026-10-09", "100", "101", "99", "100.5", "100.5", 1000L));
        when(store.summarizeStaged(JOB)).thenReturn(new StagedSummary(1, LocalDate.of(2026, 10, 8),
                LocalDate.of(2026, 10, 8)));

        assertThatThrownBy(this::repopulate).isInstanceOf(JobExecutionException.class);
        verify(store, never()).replaceWithStaged(any(), any(), any(), any(), any(), any());
    }

    private JobCounters repopulate() {
        return service.repopulate(JOB, AAPL, FROM, TO, MarketDataInterval.ONE_DAY, JobProgress.NONE);
    }

    private void provides(HistoricalPrice... prices) {
        when(provider.getHistoricalPrices(INSTRUMENT, FROM, TO, MarketDataInterval.ONE_DAY))
                .thenReturn(new HistoricalPrices("AAPL", "USD", MarketDataInterval.ONE_DAY, List.of(prices)));
    }
}
