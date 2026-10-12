package com.stockforecast.analytics.application;

import com.stockforecast.marketdata.MarketDataUpdated;
import com.stockforecast.stock.StockCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Recalculates a stock's metrics as soon as its stored history changed. Cached snapshots are keyed by the data
 * version, so the old ones are already unreachable; this only spares the next reader the computation.
 */
@Component
class AnalyticsRecalculation {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsRecalculation.class);

    private final StockAnalyticsService analytics;
    private final StockCatalog stockCatalog;

    AnalyticsRecalculation(StockAnalyticsService analytics, StockCatalog stockCatalog) {
        this.analytics = analytics;
        this.stockCatalog = stockCatalog;
    }

    @Async
    @TransactionalEventListener
    public void on(MarketDataUpdated event) {
        stockCatalog.findById(event.stockId()).ifPresent(stock -> {
            analytics.snapshots(List.of(stock));
            log.debug("Recalculated analytics for {} after a {} job", event.symbol(), event.jobType());
        });
    }
}
