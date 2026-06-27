package com.stockforecast.stock.application;

import com.stockforecast.stock.StockCatalog;
import com.stockforecast.stock.StockRef;
import com.stockforecast.stock.UnknownStockException;
import com.stockforecast.stock.domain.Stock;
import com.stockforecast.stock.domain.StockBrowser;
import com.stockforecast.stock.domain.StockRepository;
import com.stockforecast.stock.domain.StockSymbol;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class StockCatalogService implements StockCatalog {

    public static final String SEARCH_CACHE = "stock-search";
    public static final int MAX_SEARCH_RESULTS = 20;

    private final StockRepository stocks;
    private final StockBrowser browser;

    public StockCatalogService(StockRepository stocks, StockBrowser browser) {
        this.stocks = stocks;
        this.browser = browser;
    }

    @Override
    public Optional<StockRef> findBySymbol(String symbol) {
        return StockSymbol.parse(symbol)
                .flatMap(parsed -> stocks.findBySymbol(parsed.value()))
                .filter(Stock::isActive)
                .map(Stock::toRef);
    }

    @Override
    public StockRef getBySymbol(String symbol) {
        return findBySymbol(symbol).orElseThrow(() -> new UnknownStockException(symbol));
    }

    @Override
    public Optional<StockRef> findById(UUID id) {
        return stocks.findById(id).map(Stock::toRef);
    }

    @Override
    public List<StockRef> findAllByIds(Collection<UUID> ids) {
        return ids.isEmpty() ? List.of() : stocks.findAllByIdIn(ids).stream().map(Stock::toRef).toList();
    }

    /**
     * Ranked type-ahead search over symbols, company names and aliases (cached: the catalogue changes rarely and
     * the same prefixes are requested constantly).
     */
    @Cacheable(cacheNames = SEARCH_CACHE, key = "T(com.stockforecast.stock.application.StockCatalogService).normalize(#query) + '|' + #limit")
    public List<StockRef> search(String query, int limit) {
        String term = normalize(query);
        if (term.isEmpty()) {
            return List.of();
        }
        int boundedLimit = Math.clamp(limit, 1, MAX_SEARCH_RESULTS);
        return stocks.search(escapeLikeWildcards(term), boundedLimit).stream().map(Stock::toRef).toList();
    }

    public CataloguePage browse(String sector, int page, int size) {
        StockBrowser.StockPage result = browser.browse(blankToNull(sector), page, size);
        return new CataloguePage(result.content().stream().map(Stock::toRef).toList(), result.page(), result.size(),
                result.totalElements());
    }

    public List<String> sectors() {
        return browser.findSectors();
    }

    public static String normalize(String query) {
        return query == null ? "" : query.strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    static String escapeLikeWildcards(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public record CataloguePage(List<StockRef> content, int page, int size, long totalElements) {
    }
}
