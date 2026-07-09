package com.stockforecast.watchlist.application;

import com.stockforecast.stock.StockCatalog;
import com.stockforecast.stock.StockRef;
import com.stockforecast.watchlist.StockAddedToWatchlist;
import com.stockforecast.watchlist.application.WatchlistViews.WatchlistItemView;
import com.stockforecast.watchlist.application.WatchlistViews.WatchlistSummaryView;
import com.stockforecast.watchlist.application.WatchlistViews.WatchlistView;
import com.stockforecast.watchlist.domain.Watchlist;
import com.stockforecast.watchlist.domain.WatchlistExceptions.WatchlistLimitReachedException;
import com.stockforecast.watchlist.domain.WatchlistExceptions.WatchlistNameTakenException;
import com.stockforecast.watchlist.domain.WatchlistExceptions.WatchlistNotFoundException;
import com.stockforecast.watchlist.domain.WatchlistItem;
import com.stockforecast.watchlist.domain.WatchlistRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Watchlist use cases. Every method takes the authenticated user's id; watchlists of other users are reported as
 * not found so their existence is never disclosed.
 */
@Service
public class WatchlistService {

    private final WatchlistRepository watchlists;
    private final StockCatalog stockCatalog;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public WatchlistService(WatchlistRepository watchlists, StockCatalog stockCatalog,
                            ApplicationEventPublisher events, Clock clock) {
        this.watchlists = watchlists;
        this.stockCatalog = stockCatalog;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WatchlistSummaryView> list(UUID userId) {
        List<Watchlist> owned = watchlists.findAllByUserIdOrderByCreatedAtAsc(userId);
        Map<UUID, StockRef> stocks = stocksById(owned.stream()
                .flatMap(watchlist -> watchlist.getItems().stream().map(WatchlistItem::getStockId))
                .collect(Collectors.toSet()));
        return owned.stream().map(watchlist -> new WatchlistSummaryView(watchlist.getId(), watchlist.getName(),
                watchlist.getItems().size(),
                watchlist.getItems().stream()
                        .map(item -> stocks.get(item.getStockId())).filter(Objects::nonNull)
                        .map(StockRef::symbol).toList(),
                watchlist.getCreatedAt(), watchlist.getUpdatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public WatchlistView get(UUID userId, UUID watchlistId) {
        return toView(load(userId, watchlistId));
    }

    @Transactional
    public WatchlistView create(UUID userId, String name) {
        String validName = Watchlist.validName(name);
        if (watchlists.countByUserId(userId) >= Watchlist.MAX_WATCHLISTS_PER_USER) {
            throw new WatchlistLimitReachedException(Watchlist.MAX_WATCHLISTS_PER_USER);
        }
        ensureNameAvailable(userId, validName);
        return toView(watchlists.save(Watchlist.create(userId, validName, clock.instant())));
    }

    @Transactional
    public WatchlistView rename(UUID userId, UUID watchlistId, String name) {
        Watchlist watchlist = load(userId, watchlistId);
        String validName = Watchlist.validName(name);
        if (!validName.equalsIgnoreCase(watchlist.getName())) {
            ensureNameAvailable(userId, validName);
        }
        watchlist.rename(validName, clock.instant());
        return toView(watchlists.save(watchlist));
    }

    @Transactional
    public void delete(UUID userId, UUID watchlistId) {
        watchlists.delete(load(userId, watchlistId));
    }

    @Transactional
    public WatchlistItemView addStock(UUID userId, UUID watchlistId, String symbol, String notes) {
        Watchlist watchlist = load(userId, watchlistId);
        StockRef stock = stockCatalog.getBySymbol(symbol);
        Instant now = clock.instant();
        WatchlistItem item = watchlist.addStock(stock.id(), notes, now);
        watchlists.save(watchlist);
        events.publishEvent(new StockAddedToWatchlist(watchlist.getId(), userId, stock.id(), stock.symbol(), now));
        return new WatchlistItemView(stock, item.getAddedAt(), item.getNotes());
    }

    @Transactional
    public void removeStock(UUID userId, UUID watchlistId, String symbol) {
        Watchlist watchlist = load(userId, watchlistId);
        watchlist.removeStock(stockCatalog.getBySymbol(symbol).id(), clock.instant());
        watchlists.save(watchlist);
    }

    @Transactional
    public WatchlistItemView changeNotes(UUID userId, UUID watchlistId, String symbol, String notes) {
        Watchlist watchlist = load(userId, watchlistId);
        StockRef stock = stockCatalog.getBySymbol(symbol);
        WatchlistItem item = watchlist.changeNotes(stock.id(), notes, clock.instant());
        watchlists.save(watchlist);
        return new WatchlistItemView(stock, item.getAddedAt(), item.getNotes());
    }

    /**
     * Gives every new user a starting list so "add to watchlist" works right after sign-up.
     */
    @Transactional
    public void createDefaultWatchlist(UUID userId) {
        if (watchlists.countByUserId(userId) == 0) {
            watchlists.save(Watchlist.create(userId, Watchlist.DEFAULT_NAME, clock.instant()));
        }
    }

    private Watchlist load(UUID userId, UUID watchlistId) {
        return watchlists.findByIdAndUserId(watchlistId, userId).orElseThrow(WatchlistNotFoundException::new);
    }

    private void ensureNameAvailable(UUID userId, String name) {
        if (watchlists.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new WatchlistNameTakenException(name);
        }
    }

    private WatchlistView toView(Watchlist watchlist) {
        Map<UUID, StockRef> stocks = stocksById(watchlist.getItems().stream().map(WatchlistItem::getStockId).toList());
        List<WatchlistItemView> items = watchlist.getItems().stream()
                .filter(item -> stocks.containsKey(item.getStockId()))
                .map(item -> new WatchlistItemView(stocks.get(item.getStockId()), item.getAddedAt(), item.getNotes()))
                .sorted(Comparator.comparing(WatchlistItemView::addedAt))
                .toList();
        return new WatchlistView(watchlist.getId(), watchlist.getName(), watchlist.getCreatedAt(),
                watchlist.getUpdatedAt(), items);
    }

    private Map<UUID, StockRef> stocksById(Collection<UUID> ids) {
        return stockCatalog.findAllByIds(ids).stream().collect(Collectors.toMap(StockRef::id, Function.identity()));
    }
}
