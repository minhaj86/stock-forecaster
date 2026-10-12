package com.stockforecast.watchlist.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port. Every user-facing lookup is scoped by owner, so a foreign id behaves like a missing one.
 */
public interface WatchlistRepository {

    Watchlist save(Watchlist watchlist);

    void delete(Watchlist watchlist);

    /** Loads the aggregate including its items. */
    Optional<Watchlist> findByIdAndUserId(UUID id, UUID userId);

    /** Loads all of a user's watchlists including their items in one round trip. */
    List<Watchlist> findAllByUserIdOrderByCreatedAtAsc(UUID userId);

    long countByUserId(UUID userId);

    boolean existsByUserIdAndNameIgnoreCase(UUID userId, String name);

    List<UUID> findAllWatchedStockIds();

    List<UUID> findWatchedStockIdsByUserId(UUID userId);

    /** Distinct users watching each of the given stocks (stocks nobody watches are absent). */
    List<WatcherCount> countWatchersByStockIds(Collection<UUID> stockIds);
}
