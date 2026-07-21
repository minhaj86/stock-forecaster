package com.stockforecast.marketdata.infrastructure.scheduling;

import com.stockforecast.marketdata.application.MarketDataSyncService;
import com.stockforecast.watchlist.WatchlistQueries;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Daily synchronisation of every watched stock (only watched stocks are ever downloaded).
 */
@Component
class MarketDataSyncJob {

    private static final Logger log = LoggerFactory.getLogger(MarketDataSyncJob.class);

    private final WatchlistQueries watchlists;
    private final MarketDataSyncService syncService;

    MarketDataSyncJob(WatchlistQueries watchlists, MarketDataSyncService syncService) {
        this.watchlists = watchlists;
        this.syncService = syncService;
    }

    @Scheduled(cron = "${app.market-data.sync.cron}", zone = "UTC")
    @SchedulerLock(name = "market-data-sync", lockAtLeastFor = "PT1M")
    public void syncWatchedStocks() {
        Set<UUID> watched = watchlists.findAllWatchedStockIds();
        long started = System.nanoTime();
        int updated = syncService.syncAll(watched);
        log.info("Market-data sync finished: {} of {} watched stocks updated in {} ms", updated, watched.size(),
                (System.nanoTime() - started) / 1_000_000);
    }
}
