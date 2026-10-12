package com.stockforecast.marketdata.application;

import java.util.UUID;

/**
 * Internal: a job was queued. After the commit the worker is woken up so the job starts without waiting for the next
 * poll.
 */
public record MarketDataJobQueued(UUID jobId) {
}
