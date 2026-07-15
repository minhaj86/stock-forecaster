/**
 * Market data: provider port and adapters, background ingestion for watched stocks, stored price history.
 *
 * <p>User requests never call a provider; they read what ingestion stored. Published API:
 * {@link com.stockforecast.marketdata.PriceHistory}, {@link com.stockforecast.marketdata.DailyBar},
 * {@link com.stockforecast.marketdata.MarketDataStatus}.
 */
@ApplicationModule(displayName = "Market Data")
package com.stockforecast.marketdata;

import org.springframework.modulith.ApplicationModule;
