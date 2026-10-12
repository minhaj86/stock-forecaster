package com.stockforecast.marketdata;

/**
 * Bar interval. Stored history is end-of-day only: {@code market_prices} holds one bar per stock and trading date.
 * Intraday intervals would need their own retention-limited table keyed by timestamp, so they are not offered.
 */
public enum MarketDataInterval {
    ONE_DAY
}
