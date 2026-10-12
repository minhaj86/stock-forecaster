package com.stockforecast.marketdata;

public enum MarketDataJobStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    /** Valid bars were stored, but some bars were rejected by validation. */
    PARTIALLY_COMPLETED,
    FAILED,
    CANCELLED;

    public boolean isActive() {
        return this == QUEUED || this == RUNNING;
    }

    public boolean isFinished() {
        return !isActive();
    }
}
