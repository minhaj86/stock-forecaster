package com.stockforecast.watchlist.application;

import com.stockforecast.watchlist.WatchlistQueries;
import com.stockforecast.watchlist.domain.WatchlistItem;
import com.stockforecast.watchlist.domain.WatchlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
class WatchlistQueryService implements WatchlistQueries {

    private final WatchlistRepository watchlists;

    WatchlistQueryService(WatchlistRepository watchlists) {
        this.watchlists = watchlists;
    }

    @Override
    public Set<UUID> findAllWatchedStockIds() {
        return new LinkedHashSet<>(watchlists.findAllWatchedStockIds());
    }

    @Override
    public List<UUID> findWatchedStockIds(UUID userId) {
        return watchlists.findWatchedStockIdsByUserId(userId);
    }

    @Override
    public Optional<List<UUID>> findStockIds(UUID userId, UUID watchlistId) {
        return watchlists.findByIdAndUserId(watchlistId, userId)
                .map(watchlist -> watchlist.getItems().stream().map(WatchlistItem::getStockId).toList());
    }

    @Override
    public int countWatchlists(UUID userId) {
        return (int) watchlists.countByUserId(userId);
    }
}
