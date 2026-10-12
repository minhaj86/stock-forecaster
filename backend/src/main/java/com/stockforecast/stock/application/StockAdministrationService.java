package com.stockforecast.stock.application;

import com.stockforecast.stock.ManagedStock;
import com.stockforecast.stock.StockAdministration;
import com.stockforecast.stock.UnknownStockException;
import com.stockforecast.stock.domain.Stock;
import com.stockforecast.stock.domain.StockAdministrationExceptions.InvalidStockException;
import com.stockforecast.stock.domain.StockAdministrationExceptions.StockAlreadyExistsException;
import com.stockforecast.stock.domain.StockBrowser;
import com.stockforecast.stock.domain.StockRepository;
import com.stockforecast.stock.domain.StockSymbol;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Catalogue administration. Changes evict the type-ahead search cache so users see them immediately.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class StockAdministrationService implements StockAdministration {

    private final StockRepository stocks;
    private final StockBrowser browser;
    private final Clock clock;

    public StockAdministrationService(StockRepository stocks, StockBrowser browser, Clock clock) {
        this.stocks = stocks;
        this.browser = browser;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public StockPage search(String query, Boolean active, int page, int size) {
        String fragment = query == null || query.isBlank() ? null
                : StockCatalogService.normalize(query).toUpperCase(Locale.ROOT);
        StockBrowser.StockPage result = browser.searchAll(fragment, active, page, size);
        return new StockPage(result.content().stream().map(Stock::toManaged).toList(), page, size,
                result.totalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ManagedStock> findBySymbol(String symbol) {
        return StockSymbol.parse(symbol).flatMap(parsed -> stocks.findBySymbol(parsed.value())).map(Stock::toManaged);
    }

    @Override
    @Transactional(readOnly = true)
    public ManagedStock getBySymbol(String symbol) {
        return findBySymbol(symbol).orElseThrow(() -> new UnknownStockException(symbol));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = StockCatalogService.SEARCH_CACHE, allEntries = true)
    public ManagedStock register(NewStock stock) {
        StockSymbol symbol = StockSymbol.parse(stock.symbol()).orElseThrow(() -> new InvalidStockException(
                "Symbol must be 1-20 characters: letters, digits, '.' or '-' (e.g. AAPL, BRK.B, VOLV-B)"));
        if (stocks.existsBySymbol(symbol.value())) {
            throw new StockAlreadyExistsException(symbol.value());
        }
        Stock registered = Stock.register(symbol, stock.companyName(), stock.exchange(), stock.mic(),
                stock.currency(), stock.country(), stock.sector(), stock.industry(), stock.assetType(),
                stock.source() == null ? Stock.MANUAL_SOURCE : stock.source(), clock.instant());
        try {
            return stocks.save(registered).toManaged();
        } catch (DataIntegrityViolationException e) {
            // Two administrators registering the same symbol at once: the unique constraint decides.
            throw new StockAlreadyExistsException(symbol.value());
        }
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = StockCatalogService.SEARCH_CACHE, allEntries = true)
    public ManagedStock changeActive(String symbol, boolean active) {
        Stock stock = StockSymbol.parse(symbol).flatMap(parsed -> stocks.findBySymbol(parsed.value()))
                .orElseThrow(() -> new UnknownStockException(symbol));
        Instant now = clock.instant();
        if (active) {
            stock.activate(now);
        } else {
            stock.deactivate(now);
        }
        return stocks.save(stock).toManaged();
    }

    @Override
    @Transactional(readOnly = true)
    public CatalogueCounts counts() {
        return new CatalogueCounts(stocks.count(), stocks.countByActiveTrue());
    }
}
