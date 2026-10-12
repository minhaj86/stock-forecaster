package com.stockforecast.marketdata;

/**
 * What a backfill does with bars that are already stored for a date.
 */
public enum ExistingDataStrategy {
    /** Keep stored bars; only insert missing dates (the safe default). */
    SKIP,
    /** Overwrite stored bars whose values differ from the provider's. */
    UPDATE
}
