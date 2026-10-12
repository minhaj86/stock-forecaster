package com.stockforecast.marketdata;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Published after stored history of a stock changed and the change committed. Caches keyed by the data version
 * ({@code dataVersion} = time of the successful synchronisation) are invalidated implicitly; listeners may
 * recalculate derived data.
 *
 * @param historyReplaced the stored range was rebuilt (repopulation): earlier forecasts, backtests and model
 *                        evaluations of this stock must not be reused
 */
public record MarketDataUpdated(UUID stockId, String symbol, MarketDataJobType jobType, LocalDate fromDate,
                                LocalDate toDate, int inserted, int updated, int deleted, boolean historyReplaced,
                                Instant dataVersion) {
}
