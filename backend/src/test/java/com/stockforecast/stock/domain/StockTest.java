package com.stockforecast.stock.domain;

import com.stockforecast.stock.AssetType;
import com.stockforecast.stock.domain.StockAdministrationExceptions.InvalidStockException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");

    @Test
    void registersWithNormalisedCodes() {
        Stock stock = Stock.register(new StockSymbol("VOLV-B"), " Volvo B ", "OMX", "xsto", "sek", "se", " ", null,
                AssetType.COMMON_STOCK, Stock.MANUAL_SOURCE, NOW);

        assertThat(stock.toManaged()).satisfies(managed -> {
            assertThat(managed.companyName()).isEqualTo("Volvo B");
            assertThat(managed.mic()).isEqualTo("XSTO");
            assertThat(managed.currency()).isEqualTo("SEK");
            assertThat(managed.country()).isEqualTo("SE");
            assertThat(managed.sector()).isNull();
            assertThat(managed.active()).isTrue();
            assertThat(managed.source()).isEqualTo("MANUAL");
        });
    }

    @Test
    void rejectsInvalidMetadata() {
        assertThatThrownBy(() -> register("Apple", "XNA", "USD", "US")).isInstanceOf(InvalidStockException.class)
                .hasMessageContaining("MIC");
        assertThatThrownBy(() -> register("Apple", "XNAS", "US$", "US")).isInstanceOf(InvalidStockException.class)
                .hasMessageContaining("Currency");
        assertThatThrownBy(() -> register("Apple", "XNAS", "USD", "USA")).isInstanceOf(InvalidStockException.class)
                .hasMessageContaining("Country");
        assertThatThrownBy(() -> register(" ", "XNAS", "USD", "US")).isInstanceOf(InvalidStockException.class)
                .hasMessageContaining("Company name");
    }

    @Test
    void canBeDeactivatedAndActivated() {
        Stock stock = register("Apple", "XNAS", "USD", "US");

        stock.deactivate(NOW.plusSeconds(1));
        assertThat(stock.isActive()).isFalse();
        stock.activate(NOW.plusSeconds(2));
        assertThat(stock.isActive()).isTrue();
        assertThat(stock.toManaged().updatedAt()).isEqualTo(NOW.plusSeconds(2));
    }

    private static Stock register(String name, String mic, String currency, String country) {
        return Stock.register(new StockSymbol("AAPL"), name, "NASDAQ", mic, currency, country, null, null,
                AssetType.COMMON_STOCK, "twelve-data", NOW);
    }
}
