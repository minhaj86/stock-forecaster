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

    Optional<StockRef> findById(UUID id);

    List<StockRef> findAllByIds(Collection<UUID> ids);
}
