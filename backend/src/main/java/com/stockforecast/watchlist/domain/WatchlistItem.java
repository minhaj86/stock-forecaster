package com.stockforecast.watchlist.domain;

import com.stockforecast.shared.domain.Identifiers;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A stock in a watchlist. Part of the {@link Watchlist} aggregate; the stock is referenced by id only.
 */
@Entity
@Table(name = "watchlist_items")
public class WatchlistItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "watchlist_id", nullable = false, updatable = false)
    private Watchlist watchlist;

    @Column(name = "stock_id", nullable = false, updatable = false)
    private UUID stockId;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    @Column(length = Watchlist.NOTES_MAX_LENGTH)
    private String notes;

    @Version
    private Long version;

    protected WatchlistItem() {
        // required by JPA
    }

    WatchlistItem(Watchlist watchlist, UUID stockId, String notes, Instant addedAt) {
        this.id = Identifiers.newId();
        this.watchlist = Objects.requireNonNull(watchlist, "watchlist");
        this.stockId = Objects.requireNonNull(stockId, "stockId");
        this.notes = notes;
        this.addedAt = Objects.requireNonNull(addedAt, "addedAt");
    }

    void changeNotes(String notes) {
        this.notes = notes;
    }

    public UUID getId() {
        return id;
    }

    public UUID getStockId() {
        return stockId;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    public String getNotes() {
        return notes;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof WatchlistItem item && id.equals(item.getId());
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
