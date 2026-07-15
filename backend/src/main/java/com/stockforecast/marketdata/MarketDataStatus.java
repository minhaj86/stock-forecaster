package com.stockforecast.marketdata;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Synchronisation state of one stock.
 *
 * @param lastError client-safe description of the last failure, or {@code null}
 */
public record MarketDataStatus(UUID stockId, State state, String provider, LocalDate lastBarDate,
                               Instant lastSuccessAt, Instant lastAttemptAt, String lastError) {

    public enum State {
        /** Never requested: the stock is not on anyone's watchlist. */
        NOT_TRACKED,
        /** Tracked, first synchronisation still running. */
        PENDING,
        /** Data available (possibly with a newer failed attempt, see {@code lastError}). */
        READY,
        /** Tracked, but no data could be fetched yet. */
        FAILED
    }

    public static MarketDataStatus notTracked(UUID stockId) {
        return new MarketDataStatus(stockId, State.NOT_TRACKED, null, null, null, null, null);
    }
}
