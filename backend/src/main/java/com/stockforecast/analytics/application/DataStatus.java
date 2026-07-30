package com.stockforecast.analytics.application;

import com.stockforecast.marketdata.MarketDataStatus;

/**
 * Availability of market data behind a metric set, as presented to clients.
 */
public enum DataStatus {
    READY,
    /** Being collected (e.g. the stock was just added to a watchlist). */
    PENDING,
    /** Nobody watches the stock, so no data is collected. */
    NOT_TRACKED,
    /** Collection failed and no data is stored yet. */
    UNAVAILABLE;

    static DataStatus from(MarketDataStatus.State state) {
        return switch (state) {
            case READY -> READY;
            case PENDING -> PENDING;
            case NOT_TRACKED -> NOT_TRACKED;
            case FAILED -> UNAVAILABLE;
        };
    }
}
