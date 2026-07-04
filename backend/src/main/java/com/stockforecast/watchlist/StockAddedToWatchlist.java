package com.stockforecast.watchlist;

import java.time.Instant;
import java.util.UUID;

/**
 * Published after a stock was added to a watchlist; market data uses it to start tracking the stock immediately.
 */
public record StockAddedToWatchlist(UUID watchlistId, UUID userId, UUID stockId, String symbol, Instant addedAt) {
}
