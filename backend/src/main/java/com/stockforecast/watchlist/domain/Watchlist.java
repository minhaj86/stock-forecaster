package com.stockforecast.watchlist.domain;

import com.stockforecast.shared.domain.Identifiers;
import com.stockforecast.watchlist.domain.WatchlistExceptions.InvalidWatchlistException;
import com.stockforecast.watchlist.domain.WatchlistExceptions.StockAlreadyInWatchlistException;
import com.stockforecast.watchlist.domain.WatchlistExceptions.StockNotInWatchlistException;
import com.stockforecast.watchlist.domain.WatchlistExceptions.WatchlistFullException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A user-owned, named list of stocks (aggregate root). Invariants: a stock appears at most once and a list holds
 * at most {@value #MAX_ITEMS} stocks, which bounds the market data the platform must collect per user.
 */
@Entity
@Table(name = "watchlists")
public class Watchlist {

    public static final int MAX_ITEMS = 50;
    public static final int MAX_WATCHLISTS_PER_USER = 20;
    public static final int NAME_MAX_LENGTH = 60;
    public static final int NOTES_MAX_LENGTH = 500;
    public static final String DEFAULT_NAME = "My Watchlist";

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "watchlist", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("addedAt ASC")
    private List<WatchlistItem> items = new ArrayList<>();

    protected Watchlist() {
        // required by JPA
    }

    private Watchlist(UUID userId, String name, Instant now) {
        this.id = Identifiers.newId();
        this.userId = Objects.requireNonNull(userId, "userId");
        this.name = validName(name);
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public static Watchlist create(UUID userId, String name, Instant now) {
        return new Watchlist(userId, name, now);
    }

    public static String validName(String raw) {
        String stripped = raw == null ? "" : raw.strip();
        if (stripped.isEmpty() || stripped.length() > NAME_MAX_LENGTH
                || stripped.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidWatchlistException("INVALID_WATCHLIST_NAME",
                    "Watchlist name must be 1-%d printable characters".formatted(NAME_MAX_LENGTH));
        }
        return stripped;
    }

    public void rename(String newName, Instant now) {
        String valid = validName(newName);
        if (!valid.equals(name)) {
            this.name = valid;
            touch(now);
        }
    }

    public WatchlistItem addStock(UUID stockId, String notes, Instant now) {
        if (find(stockId).isPresent()) {
            throw new StockAlreadyInWatchlistException();
        }
        if (items.size() >= MAX_ITEMS) {
            throw new WatchlistFullException(MAX_ITEMS);
        }
        WatchlistItem item = new WatchlistItem(this, stockId, validNotes(notes), now);
        items.add(item);
        touch(now);
        return item;
    }

    public WatchlistItem removeStock(UUID stockId, Instant now) {
        WatchlistItem item = find(stockId).orElseThrow(StockNotInWatchlistException::new);
        items.remove(item);
        touch(now);
        return item;
    }

    public WatchlistItem changeNotes(UUID stockId, String notes, Instant now) {
        WatchlistItem item = find(stockId).orElseThrow(StockNotInWatchlistException::new);
        item.changeNotes(validNotes(notes));
        touch(now);
        return item;
    }

    public boolean contains(UUID stockId) {
        return find(stockId).isPresent();
    }

    private Optional<WatchlistItem> find(UUID stockId) {
        return items.stream().filter(item -> item.getStockId().equals(stockId)).findFirst();
    }

    private static String validNotes(String notes) {
        if (notes == null || notes.isBlank()) {
            return null;
        }
        String stripped = notes.strip();
        if (stripped.length() > NOTES_MAX_LENGTH) {
            throw new InvalidWatchlistException("INVALID_NOTES",
                    "Notes must be at most %d characters".formatted(NOTES_MAX_LENGTH));
        }
        return stripped;
    }

    private void touch(Instant now) {
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<WatchlistItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Watchlist watchlist && id.equals(watchlist.getId());
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Watchlist[id=" + id + ", items=" + items.size() + "]";
    }
}
