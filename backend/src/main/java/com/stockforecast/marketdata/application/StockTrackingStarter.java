package com.stockforecast.marketdata.application;

import com.stockforecast.watchlist.StockAddedToWatchlist;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Tracking starts in the same transaction as watching: the moment a stock is on a watchlist its market data is
 * reported as pending (never "not tracked"), even before the asynchronous backfill has picked it up.
 */
@Component
class StockTrackingStarter {

    private final MarketDataStore store;
    private final Clock clock;

    StockTrackingStarter(MarketDataStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @EventListener
    void on(StockAddedToWatchlist event) {
        store.ensureTracked(event.stockId(), clock.instant());
    }
}
