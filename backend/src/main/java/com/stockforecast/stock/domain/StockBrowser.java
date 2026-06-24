package com.stockforecast.stock.domain;

import java.util.List;

/**
 * Paged catalogue browsing (Explore page).
 */
public interface StockBrowser {

    StockPage browse(String sector, int page, int size);

    List<String> findSectors();

    record StockPage(List<Stock> content, int page, int size, long totalElements) {
    }
}
