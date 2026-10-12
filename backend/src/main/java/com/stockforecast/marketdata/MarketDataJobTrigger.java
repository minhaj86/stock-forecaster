package com.stockforecast.marketdata;

public enum MarketDataJobTrigger {
    /** Requested in the admin console. */
    ADMIN,
    /** A user watched a stock that had no (current) data. */
    WATCHLIST,
    /** The scheduled synchronisation or the start-up catch-up. */
    SCHEDULE
}
