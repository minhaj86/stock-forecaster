package com.stockforecast.watchlist.domain;

import java.util.UUID;

/**
 * Number of distinct users watching a stock.
 */
public record WatcherCount(UUID stockId, long watchers) {
}
