package com.stockforecast.stock.domain;

import java.util.List;

/**
 * Paged catalogue browsing (Explore page).
 */
public interface StockBrowser {

    StockPage browse(String sector, int page, int size);

    List<String> findSectors();

    /**
     * Admin listing over all stocks (including inactive ones), ordered by symbol.
     *
     * @param upperCaseFragment upper-cased "contains" match on symbol or company name, or {@code null}
     * @param active            status filter, or {@code null} for all
     */
    StockPage searchAll(String upperCaseFragment, Boolean active, int page, int size);

    record StockPage(List<Stock> content, int page, int size, long totalElements) {
    }
}
