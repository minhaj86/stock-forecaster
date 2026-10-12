package com.stockforecast.marketdata.application;

import com.stockforecast.watchlist.StockAddedToWatchlist;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Tracking starts in the same transaction as watching: the moment a stock is on a watchlist its market data is
 * reported as pending (never "not tracked"), and the initial-history (or catch-up) job is queued atomically with the
 * watchlist change, so a crash cannot lose it. The job itself runs asynchronously; the user never waits for years of
 * history to download.
 */
@Component
class StockTrackingStarter {

    private final MarketDataStore store;
    private final MarketDataJobService jobs;
    private final Clock clock;

    StockTrackingStarter(MarketDataStore store, MarketDataJobService jobs, Clock clock) {
        this.store = store;
        this.jobs = jobs;
        this.clock = clock;
    }

    @EventListener
    void on(StockAddedToWatchlist event) {
        store.ensureTracked(event.stockId(), clock.instant());
        jobs.onStockWatched(event.stockId());
    }
}
