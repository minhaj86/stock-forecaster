package com.stockforecast.marketdata.application;

import com.stockforecast.marketdata.DailyBar;
import com.stockforecast.marketdata.PriceHistory.ProviderInfo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Port to an external market-data vendor. Adapters translate vendor payloads into these types; vendor types never
 * leave the adapter. Implementations must be safe to call concurrently.
 */
public interface MarketDataProvider {

    ProviderInfo info();

    /**
     * Daily bars (oldest first) between both dates, inclusive; may be empty for non-trading periods.
     *
     * @throws MarketDataProviderException on vendor or transport failures
     */
    List<DailyBar> getHistoricalPrices(String symbol, LocalDate from, LocalDate to);

    Optional<StockQuote> getQuote(String symbol);

    List<ProviderSearchResult> searchStocks(String query);

    record StockQuote(String symbol, BigDecimal price, BigDecimal change, double changePercent, Instant timestamp) {
    }

    record ProviderSearchResult(String symbol, String name, String exchange, String currency) {
    }
}
