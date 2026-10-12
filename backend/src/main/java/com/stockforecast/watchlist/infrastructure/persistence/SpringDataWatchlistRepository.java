package com.stockforecast.watchlist.infrastructure.persistence;

import com.stockforecast.watchlist.domain.Watchlist;
import com.stockforecast.watchlist.domain.WatchlistRepository;
import com.stockforecast.watchlist.domain.WatcherCount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataWatchlistRepository extends Repository<Watchlist, UUID>, WatchlistRepository {

    @Override
    @EntityGraph(attributePaths = "items")
    Optional<Watchlist> findByIdAndUserId(UUID id, UUID userId);

    @Override
    @EntityGraph(attributePaths = "items")
    List<Watchlist> findAllByUserIdOrderByCreatedAtAsc(UUID userId);

    @Override
    @Query("select distinct i.stockId from WatchlistItem i")
    List<UUID> findAllWatchedStockIds();

    @Override
    @Query("select distinct i.stockId from WatchlistItem i where i.watchlist.userId = :userId")
    List<UUID> findWatchedStockIdsByUserId(UUID userId);

    @Override
    @Query("""
            select new com.stockforecast.watchlist.domain.WatcherCount(
                       i.stockId, count(distinct i.watchlist.userId))
              from WatchlistItem i
             where i.stockId in :stockIds
             group by i.stockId""")
    List<WatcherCount> countWatchersByStockIds(Collection<UUID> stockIds);
}
