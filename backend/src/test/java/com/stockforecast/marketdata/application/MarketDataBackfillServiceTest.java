package com.stockforecast.marketdata.application;

import com.stockforecast.marketdata.DailyBar;
import com.stockforecast.marketdata.ExistingDataStrategy;
import com.stockforecast.marketdata.MarketDataInterval;
import com.stockforecast.marketdata.PriceHistory.ProviderInfo;
import com.stockforecast.marketdata.application.MarketDataBackfillService.BackfillResult;
import com.stockforecast.marketdata.application.MarketDataProvider.HistoricalPrice;
import com.stockforecast.marketdata.application.MarketDataProvider.HistoricalPrices;
import com.stockforecast.marketdata.application.MarketDataProvider.ProviderInstrument;
import com.stockforecast.stock.AssetType;
import com.stockforecast.stock.StockRef;
import com.stockforecast.support.NoOpTransactions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.stockforecast.marketdata.application.MarketDataValidationServiceTest.bar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketDataBackfillServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-11T22:00:00Z");
    private static final StockRef AAPL = new StockRef(UUID.randomUUID(), "AAPL", "Apple Inc.", "NASDAQ", "XNAS",
            "USD", "US", "Information Technology", "Hardware", AssetType.COMMON_STOCK);
    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 9);

    @Mock
    private MarketDataProvider provider;
    @Mock
    private MarketDataStore store;

    private MarketDataBackfillService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        lenient().when(provider.info()).thenReturn(new ProviderInfo("fake", "Fake provider", false, "test"));
        service = new MarketDataBackfillService(provider, new MarketDataValidationService(clock), store,
                NoOpTransactions.template(), clock);
    }

    @Test
    void skipStrategyOnlyInsertsMissingDatesAndCountsTheRestAsSkipped() {
        stored(storedBar("2026-10-01", "100.5"), storedBar("2026-10-02", "105"));
        provides(bar("2026-10-01", "100", "101", "99", "100.5", "100.5", 1000L),
                bar("2026-10-02", "100", "101", "99", "100.5", "100.5", 1000L),
                bar("2026-10-05", "100", "101", "99", "100.5", "100.5", 1000L));

        BackfillResult result = service.backfill(AAPL, FROM, TO, MarketDataInterval.ONE_DAY,
                ExistingDataStrategy.SKIP, JobProgress.NONE);

        assertThat(result.counters()).satisfies(counters -> {
            assertThat(counters.fetched()).isEqualTo(3);
            assertThat(counters.inserted()).isEqualTo(1);
            assertThat(counters.updated()).isZero();
            assertThat(counters.skipped()).isEqualTo(2);
            assertThat(counters.failed()).isZero();
        });
        assertThat(insertedDates()).containsExactly(LocalDate.parse("2026-10-05"));
        verify(store, never()).upsertBars(any(), any(), anyList(), any());
        verify(store).recordSuccess(AAPL.id(), "fake", NOW);
    }

    @Test
    void updateStrategyOverwritesChangedBarsOnly() {
        stored(storedBar("2026-10-01", "100.5"), storedBar("2026-10-02", "105"));
        provides(bar("2026-10-01", "100", "101", "99", "100.5", "100.5", 1000L),
                bar("2026-10-02", "100", "101", "99", "100.5", "100.5", 1000L));

        BackfillResult result = service.backfill(AAPL, FROM, TO, MarketDataInterval.ONE_DAY,
                ExistingDataStrategy.UPDATE, JobProgress.NONE);

        assertThat(result.counters().updated()).isEqualTo(1);
        assertThat(result.counters().skipped()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DailyBar>> upserted = ArgumentCaptor.forClass(List.class);
        verify(store).upsertBars(eq(AAPL.id()), eq("fake"), upserted.capture(), eq(NOW));
        assertThat(upserted.getValue()).extracting(DailyBar::date).containsExactly(LocalDate.parse("2026-10-02"));
    }

    @Test
    void invalidBarsAreSkippedAndCountedAsFailed() {
        stored();
        provides(bar("2026-10-01", "100", "101", "99", "100.5", "100.5", 1000L),
                bar("2026-10-02", "100", "98", "99", "100.5", "100.5", 1000L));

        BackfillResult result = service.backfill(AAPL, FROM, TO, MarketDataInterval.ONE_DAY,
                ExistingDataStrategy.SKIP, JobProgress.NONE);

        assertThat(result.counters().inserted()).isEqualTo(1);
        assertThat(result.counters().failed()).isEqualTo(1);
        assertThat(result.validationIssues()).contains("high is below low");
    }

    @Test
    void malformedResponsesAreRejectedWithoutWritingAnything() {
        when(provider.getHistoricalPrices(new ProviderInstrument("AAPL", "XNAS"), FROM, TO,
                MarketDataInterval.ONE_DAY)).thenReturn(new HistoricalPrices("AAPL", "USD",
                MarketDataInterval.ONE_DAY, List.of(bar("2026-10-01", "1", "1", "1", "1", "1", 1L),
                bar("2026-10-01", "1", "1", "1", "1", "1", 1L))));

        assertThatThrownBy(() -> service.backfill(AAPL, FROM, TO, MarketDataInterval.ONE_DAY,
                ExistingDataStrategy.UPDATE, JobProgress.NONE))
                .isInstanceOf(JobExecutionException.class)
                .satisfies(error -> assertThat(((JobExecutionException) error).errorCode())
                        .isEqualTo("INVALID_PROVIDER_DATA"));
        verify(store, never()).insertMissingBars(any(), any(), anyList(), any());
        verify(store, never()).recordSuccess(any(), any(), any());
    }

    @Test
    void responsesWithOnlyInvalidBarsFailTheJob() {
        provides(bar("2026-10-02", "100", "98", "99", "100.5", "100.5", 1000L));

        assertThatThrownBy(() -> service.backfill(AAPL, FROM, TO, MarketDataInterval.ONE_DAY,
                ExistingDataStrategy.SKIP, JobProgress.NONE)).isInstanceOf(JobExecutionException.class)
                .hasMessageContaining("1 of 1 bars failed validation");
        verify(store, never()).recordSuccess(any(), any(), any());
    }

    @Test
    void detectsNewCorporateActionAdjustments() {
        stored(new DailyBar(LocalDate.parse("2026-10-01"), money("100"), money("101"), money("99"), money("100.5"),
                money("100.5"), 1000));
        provides(bar("2026-10-01", "100", "101", "99", "100.5", "99.9", 1000L));

        BackfillResult result = service.backfill(AAPL, FROM, TO, MarketDataInterval.ONE_DAY,
                ExistingDataStrategy.UPDATE, JobProgress.NONE);

        assertThat(result.adjustmentChanged()).isTrue();
    }

    private void provides(HistoricalPrice... prices) {
        when(provider.getHistoricalPrices(new ProviderInstrument("AAPL", "XNAS"), FROM, TO,
                MarketDataInterval.ONE_DAY)).thenReturn(new HistoricalPrices("AAPL", "USD",
                MarketDataInterval.ONE_DAY, List.of(prices)));
    }

    private void stored(DailyBar... bars) {
        lenient().when(store.findBars(eq(List.of(AAPL.id())), any(), any()))
                .thenReturn(Map.of(AAPL.id(), List.of(bars)));
    }

    private List<LocalDate> insertedDates() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DailyBar>> inserted = ArgumentCaptor.forClass(List.class);
        verify(store).insertMissingBars(eq(AAPL.id()), eq("fake"), inserted.capture(), eq(NOW));
        return inserted.getValue().stream().map(DailyBar::date).toList();
    }

    private static DailyBar storedBar(String date, String close) {
        return new DailyBar(LocalDate.parse(date), money("100"), money("101").max(money(close)), money("99"),
                money(close), money(close), 1000);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(6);
    }
}
