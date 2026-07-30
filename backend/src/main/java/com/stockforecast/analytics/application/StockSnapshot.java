package com.stockforecast.analytics.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Point-in-time metrics of one stock, computed from stored daily bars. Ratios are decimal fractions
 * ({@code 0.052} = 5.2 %); metrics are {@code null} when the history is too short.
 *
 * @param syncIssue client-safe description when the latest synchronisation attempt failed (stored data is shown)
 */
public record StockSnapshot(
        String symbol, String companyName, String exchange, String currency, String sector,
        DataStatus dataStatus, String dataProvider, LocalDate asOf, Instant lastSyncedAt, String syncIssue,
        BigDecimal price, BigDecimal previousClose, BigDecimal change, Double changePercent,
        Double return1W, Double return1M, Double return3M, Double return6M, Double returnYtd, Double return1Y,
        Double volatility30d, Double volatility90d, Double maxDrawdown1Y, Double rsi14, Double momentum3M) {

    StockSnapshot withStatus(DataStatus status) {
        return new StockSnapshot(symbol, companyName, exchange, currency, sector, status, dataProvider, asOf,
                lastSyncedAt, syncIssue, price, previousClose, change, changePercent, return1W, return1M, return3M,
                return6M, returnYtd, return1Y, volatility30d, volatility90d, maxDrawdown1Y, rsi14, momentum3M);
    }
}
