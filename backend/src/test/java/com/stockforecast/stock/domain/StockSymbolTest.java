package com.stockforecast.stock.domain;

import com.stockforecast.stock.application.StockCatalogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class StockSymbolTest {

    @Test
    void normalisesUserInput() {
        assertThat(StockSymbol.parse(" brk.b ")).contains(new StockSymbol("BRK.B"));
        assertThat(StockSymbol.parse("msft")).contains(new StockSymbol("MSFT"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "AA PL", "%", "A_B", ".AAPL", "ABCDEFGHIJKLMNOPQRSTU"})
    void rejectsImpossibleSymbols(String raw) {
        assertThat(StockSymbol.parse(raw)).isEmpty();
    }

    @Test
    void searchTermsAreNormalisedForStableCacheKeys() {
        assertThat(StockCatalogService.normalize("  apple   inc ")).isEqualTo("APPLE INC");
        assertThat(StockCatalogService.normalize(null)).isEmpty();
    }
}
