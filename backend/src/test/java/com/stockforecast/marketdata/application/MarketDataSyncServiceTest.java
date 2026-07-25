package com.stockforecast.marketdata.application;

import com.stockforecast.marketdata.DailyBar;
import com.stockforecast.marketdata.PriceHistory.ProviderInfo;
import com.stockforecast.marketdata.application.MarketDataStore.SyncClaim;
import com.stockforecast.marketdata.application.MarketDataSyncService.SyncOutcome;
import com.stockforecast.stock.AssetType;
import com.stockforecast.stock.StockCatalog;
import com.stockforecast.stock.StockRef;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketDataSyncServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 11);
    private static final StockRef STOCK = new StockRef(UUID.randomUUID(), "AAPL", "Apple Inc.", "NASDAQ", "XNAS", "USD",
            "US", "Information Technology", "Technology Hardware", AssetType.COMMON_STOCK);
    private static final MarketDataProperties PROPERTIES = new MarketDataProperties("fake", 5, 5,
            Duration.ofMinutes(10), new MarketDataProperties.Sync("-", false));

    @Mock
    private MarketDataStore store;
    @Mock
    private MarketDataProvider provider;
    @Mock
    private StockCatalog stockCatalog;
    @Mock
    private ObjectProvider<MarketDataProvider> providerLookup;
    @Mock
    private TransactionTemplate transactions;

    private MarketDataSyncService service;

    @BeforeEach
    void setUp() {
        when(providerLookup.getIfAvailable(any())).thenReturn(provider);
        lenient().when(provider.info()).thenReturn(new ProviderInfo("fake", "Fake", false, "test double"));
        lenient().when(stockCatalog.findById(STOCK.id())).thenReturn(Optional.of(STOCK));
        lenient().doAnswer(invocation -> {
            invocation.<Consumer<Object>>getArgument(0).accept(null);
            return null;
        }).when(transactions).executeWithoutResult(any());
        service = new MarketDataSyncService(store, providerLookup, stockCatalog, PROPERTIES, transactions,
                Clock.fixed(NOW, ZoneOffset.UTC), new SimpleMeterRegistry());
    }

    @Test
    void firstSynchronisationBackfillsTheConfiguredHistory() {
        when(store.tryClaim(eq(STOCK.id()), eq(NOW), any(), eq(false)))
                .thenReturn(Optional.of(new SyncClaim(STOCK.id(), null, 0)));
        List<DailyBar> bars = List.of(bar(TODAY.minusDays(1)), bar(TODAY));
        when(provider.getHistoricalPrices("AAPL", TODAY.minusYears(5), TODAY)).thenReturn(bars);

        assertThat(service.syncStock(STOCK.id())).isEqualTo(SyncOutcome.UPDATED);

        verify(store).upsertBars(STOCK.id(), "fake", bars, NOW);
        verify(store).recordSuccess(STOCK.id(), "fake", TODAY, NOW);
    }

    @Test
    void laterSynchronisationsRefetchATrailingWindowOnly() {
        LocalDate lastBar = TODAY.minusDays(3);
        when(store.tryClaim(eq(STOCK.id()), any(), any(), anyBoolean()))
                .thenReturn(Optional.of(new SyncClaim(STOCK.id(), lastBar, 0)));
        when(provider.getHistoricalPrices("AAPL", lastBar.minusDays(5), TODAY)).thenReturn(List.of());

        service.syncStock(STOCK.id());

        verify(store).recordSuccess(STOCK.id(), "fake", lastBar, NOW);
    }

    @Test
    void stocksLeasedByAnotherWorkerOrBackingOffAreSkipped() {
        when(store.tryClaim(any(), any(), any(), anyBoolean())).thenReturn(Optional.empty());

        assertThat(service.syncStock(STOCK.id())).isEqualTo(SyncOutcome.SKIPPED);

        verifyNoInteractions(transactions);
        verify(provider, never()).getHistoricalPrices(any(), any(), any());
    }

    @Test
    void providerFailuresAreRecordedWithExponentialBackoff() {
        when(store.tryClaim(any(), any(), any(), anyBoolean()))
                .thenReturn(Optional.of(new SyncClaim(STOCK.id(), TODAY.minusDays(1), 2)));
        when(provider.getHistoricalPrices(any(), any(), any()))
                .thenThrow(new MarketDataProviderException("rate limited", true, null));

        assertThat(service.syncStock(STOCK.id())).isEqualTo(SyncOutcome.FAILED);

        verify(store).recordFailure(STOCK.id(), "Market-data provider error: rate limited", NOW,
                NOW.plus(Duration.ofMinutes(8)));
        verify(store, never()).upsertBars(any(), any(), any(), any());
    }

    @Test
    void backoffDoublesAndIsCapped() {
        assertThat(MarketDataSyncService.backoff(1)).isEqualTo(Duration.ofMinutes(2));
        assertThat(MarketDataSyncService.backoff(3)).isEqualTo(Duration.ofMinutes(8));
        assertThat(MarketDataSyncService.backoff(30)).isEqualTo(Duration.ofHours(6));
    }

    private static DailyBar bar(LocalDate date) {
        BigDecimal price = new BigDecimal("100.00");
        return new DailyBar(date, price, price, price, price, price, 1_000);
    }
}
