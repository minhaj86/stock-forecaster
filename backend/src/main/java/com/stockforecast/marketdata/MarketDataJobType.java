package com.stockforecast.marketdata;

public enum MarketDataJobType {
    /** First history download for a newly tracked stock (configured number of years). */
    INITIAL_LOAD,
    /** Administrator-requested incremental update from the newest stored bar. */
    LATEST_UPDATE,
    /** Administrator-requested download of an explicit date range. */
    BACKFILL,
    /** Safe replacement of the stored history in a date range (fetch, validate, stage, then swap). */
    REPOPULATE,
    /** Scheduled incremental update of a watched stock. */
    AUTOMATIC_SYNC
}
