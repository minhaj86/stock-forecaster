package com.stockforecast.watchlist;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only queries other modules may use. User-scoped methods only ever return data owned by the given user.
 */
public interface WatchlistQueries {

    /** Every stock referenced by at least one watchlist (drives market-data synchronisation). */
    Set<UUID> findAllWatchedStockIds();

    /** Distinct stocks across all watchlists of the user. */
    List<UUID> findWatchedStockIds(UUID userId);

    /** Stocks of one watchlist, or empty if it does not exist or belongs to someone else. */
    Optional<List<UUID>> findStockIds(UUID userId, UUID watchlistId);

    int countWatchlists(UUID userId);

    /** Number of distinct users watching each stock; stocks nobody watches are absent. */
    Map<UUID, Long> countWatchers(Collection<UUID> stockIds);
}
