package com.stockforecast.stock;

import java.util.List;
import java.util.Optional;

/**
 * Catalogue administration for the admin console. Stocks are registered once (shared by all users) and deactivated
 * rather than deleted. Callers must be administrators (enforced with method security).
 */
public interface StockAdministration {

    /**
     * @param query  case-insensitive "contains" match on symbol or company name, or {@code null}
     * @param active filter by status, or {@code null} for all
     */
    StockPage search(String query, Boolean active, int page, int size);

    /** Also finds inactive stocks. */
    Optional<ManagedStock> findBySymbol(String symbol);

    /**
     * @throws UnknownStockException if the symbol is not in the catalogue
     */
    ManagedStock getBySymbol(String symbol);

    ManagedStock register(NewStock stock);

    /** Inactive stocks disappear from search, cannot be added to watchlists and are no longer synchronised. */
    ManagedStock changeActive(String symbol, boolean active);

    CatalogueCounts counts();

    /**
     * @param source {@code MANUAL} or the id of the market-data provider that supplied the metadata
     */
    record NewStock(String symbol, String companyName, String exchange, String mic, String currency, String country,
                    String sector, String industry, AssetType assetType, String source) {
    }

    record StockPage(List<ManagedStock> content, int page, int size, long totalElements) {
    }

    record CatalogueCounts(long total, long active) {
    }
}
