package com.stockforecast.stock.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for the catalogue. Paged browsing lives in the infrastructure adapter's query methods exposed
 * through {@link StockBrowser}.
 */
public interface StockRepository {

    Optional<Stock> findBySymbol(String symbol);

    Optional<Stock> findById(UUID id);

    List<Stock> findAllByIdIn(Collection<UUID> ids);

    /**
     * Ranked search: exact symbol, symbol prefix, name/alias prefix, then substring matches.
     *
     * @param escapedTerm upper-cased term with LIKE wildcards escaped
     */
    List<Stock> search(String escapedTerm, int limit);
}
