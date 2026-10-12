package com.stockforecast.stock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Published lookup API of the stock module.
 */
public interface StockCatalog {

    Optional<StockRef> findBySymbol(String symbol);

    /**
     * @throws UnknownStockException if the symbol is malformed or not in the catalogue
     */
    StockRef getBySymbol(String symbol);

    /** Includes inactive stocks. */
    Optional<StockRef> findById(UUID id);

    /** Includes inactive stocks (existing watchlist items and history keep referring to them). */
    List<StockRef> findAllByIds(Collection<UUID> ids);

    /** Only stocks that are currently active (searchable, watchable and synchronised). */
    List<StockRef> findAllActiveByIds(Collection<UUID> ids);
}
