/**
 * Market data: provider port and adapters, asynchronous synchronisation jobs, stored price history and provider
 * administration.
 *
 * <p>User requests never call a provider; they read what the jobs stored. Jobs are queued by the scheduler, by users
 * watching new stocks and by administrators, and executed by one worker within the provider's rate limits.
 *
 * <p>Published API: {@link com.stockforecast.marketdata.PriceHistory}, {@link com.stockforecast.marketdata.DailyBar},
 * {@link com.stockforecast.marketdata.MarketDataStatus}, {@link com.stockforecast.marketdata.MarketDataUpdated}
 * (event) and, for the admin console, {@link com.stockforecast.marketdata.MarketDataJobs},
 * {@link com.stockforecast.marketdata.MarketDataInventory} and {@link com.stockforecast.marketdata.MarketDataProviders}
 * with their value types.
 */
@ApplicationModule(displayName = "Market Data")
package com.stockforecast.marketdata;

import org.springframework.modulith.ApplicationModule;
