package com.stockforecast.marketdata.application;

import com.stockforecast.watchlist.StockAddedToWatchlist;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Starts collecting history as soon as a stock is watched, so the dashboard fills within seconds instead of at
 * the next scheduled run. Runs after the watchlist transaction committed, on a background thread; if the
 * process dies first, the next scheduled synchronisation catches up.
 */
@Component
class WatchedStockBackfill {

    private final MarketDataSyncService syncService;

    WatchedStockBackfill(MarketDataSyncService syncService) {
        this.syncService = syncService;
    }

    @Async
    @TransactionalEventListener
    public void on(StockAddedToWatchlist event) {
        syncService.syncStock(event.stockId());
    }
}
