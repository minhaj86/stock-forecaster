package com.stockforecast.watchlist.application;

import com.stockforecast.stock.StockRef;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read models returned by the watchlist use cases.
 */
public final class WatchlistViews {

    private WatchlistViews() {
    }

    public record WatchlistSummaryView(UUID id, String name, int stockCount, List<String> symbols, Instant createdAt,
                                       Instant updatedAt) {
    }

    public record WatchlistView(UUID id, String name, Instant createdAt, Instant updatedAt,
                                List<WatchlistItemView> items) {
    }

    public record WatchlistItemView(StockRef stock, Instant addedAt, String notes) {
    }
}
