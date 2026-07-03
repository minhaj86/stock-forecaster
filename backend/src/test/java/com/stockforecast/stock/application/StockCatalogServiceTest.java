package com.stockforecast.stock.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StockCatalogServiceTest {

    @Test
    void escapesLikeWildcardsSoUserInputIsMatchedLiterally() {
        assertThat(StockCatalogService.escapeLikeWildcards("100%_\\")).isEqualTo("100\\%\\_\\\\");
        assertThat(StockCatalogService.escapeLikeWildcards("MICRO")).isEqualTo("MICRO");
    }
}
