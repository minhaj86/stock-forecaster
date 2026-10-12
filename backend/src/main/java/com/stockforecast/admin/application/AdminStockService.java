package com.stockforecast.admin.application;

import com.stockforecast.admin.domain.AdminExceptions.ProviderMetadataIncompleteException;
import com.stockforecast.admin.domain.AuditAction;
import com.stockforecast.admin.domain.AuditEntityType;
import com.stockforecast.identity.AuthenticatedUser;
import com.stockforecast.marketdata.MarketDataInventory;
import com.stockforecast.marketdata.MarketDataInventory.StockDataSummary;
import com.stockforecast.marketdata.MarketDataJobView;
import com.stockforecast.marketdata.MarketDataJobs;
import com.stockforecast.marketdata.MarketDataProviders;
import com.stockforecast.marketdata.MarketDataProviders.ProviderSearch;
import com.stockforecast.marketdata.PriceHistory;
import com.stockforecast.marketdata.PriceHistory.ProviderInfo;
import com.stockforecast.marketdata.ProviderInstrumentMatch;
import com.stockforecast.marketdata.ProviderStockMetadata;
import com.stockforecast.stock.AssetType;
import com.stockforecast.stock.ManagedStock;
import com.stockforecast.stock.StockAdministration;
import com.stockforecast.stock.StockAdministration.NewStock;
import com.stockforecast.stock.StockAdministration.StockPage;
import com.stockforecast.watchlist.WatchlistQueries;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stock administration: the catalogue (stock module) enriched with market-data coverage and watcher counts.
 * New stocks are registered from provider metadata whenever the provider can supply it; provider calls run outside
 * any database transaction so no connection is held during the network call.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminStockService {

    private final StockAdministration stocks;
    private final MarketDataInventory inventory;
    private final MarketDataProviders providers;
    private final MarketDataJobs jobs;
    private final PriceHistory priceHistory;
    private final WatchlistQueries watchlists;
    private final AuditLog audit;
    private final TransactionTemplate transactions;

    public AdminStockService(StockAdministration stocks, MarketDataInventory inventory, MarketDataProviders providers,
                             MarketDataJobs jobs, PriceHistory priceHistory, WatchlistQueries watchlists,
                             AuditLog audit, PlatformTransactionManager transactionManager) {
        this.stocks = stocks;
        this.inventory = inventory;
        this.providers = providers;
        this.jobs = jobs;
        this.priceHistory = priceHistory;
        this.watchlists = watchlists;
        this.audit = audit;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public AdminStockPage search(String query, Boolean active, int page, int size) {
        StockPage result = stocks.search(query, active, page, size);
        return new AdminStockPage(enrich(result.content()), page, size, result.totalElements());
    }

    @Transactional(readOnly = true)
    public AdminStock get(String symbol) {
        return enrich(List.of(stocks.getBySymbol(symbol))).getFirst();
    }

    /**
     * Searches the provider (a synchronous, rate-limited call) and marks results already in the catalogue.
     */
    public ProviderLookup searchProvider(String query) {
        ProviderSearch search = providers.search(query);
        List<ProviderLookupResult> results = search.results().stream()
                .map(match -> new ProviderLookupResult(match, stocks.findBySymbol(match.symbol()).isPresent()))
                .toList();
        ProviderInfo info = priceHistory.activeProvider();
        return new ProviderLookup(info.id(), info.name(), search.supported(), results);
    }

    public Registration register(AuthenticatedUser actor, RegisterStock command) {
        NewStock newStock = command.manual() == null ? fromProvider(command.symbol(), command.mic())
                : command.manual().toNewStock(command.symbol());
        return transactions.execute(status -> registerInTransaction(actor, newStock, command.loadHistory()));
    }

    private Registration registerInTransaction(AuthenticatedUser actor, NewStock newStock, boolean loadHistory) {
        ManagedStock created = stocks.register(newStock);
        MarketDataJobView job = loadHistory ? jobs.requestInitialLoad(created.id(), actor.id()).job() : null;

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("symbol", created.symbol());
        metadata.put("companyName", created.companyName());
        metadata.put("exchange", created.exchange());
        metadata.put("mic", created.mic());
        metadata.put("currency", created.currency());
        metadata.put("source", created.source());
        if (job != null) {
            metadata.put("initialLoadJobId", job.id().toString());
        }
        audit.record(actor, AuditAction.STOCK_CREATED, AuditEntityType.STOCK, created.symbol(),
                "Added %s (%s, %s)".formatted(created.symbol(), created.companyName(), created.exchange()), metadata);
        return new Registration(enrich(List.of(created)).getFirst(), job);
    }

    @Transactional
    public AdminStock changeActive(AuthenticatedUser actor, String symbol, boolean active) {
        ManagedStock before = stocks.getBySymbol(symbol);
        ManagedStock after = stocks.changeActive(symbol, active);
        if (before.active() != after.active()) {
            audit.record(actor, active ? AuditAction.STOCK_ENABLED : AuditAction.STOCK_DISABLED,
                    AuditEntityType.STOCK, after.symbol(), (active ? "Activated " : "Deactivated ") + after.symbol(),
                    Map.of("symbol", after.symbol(), "active", active));
        }
        return enrich(List.of(after)).getFirst();
    }

    private NewStock fromProvider(String symbol, String mic) {
        ProviderStockMetadata metadata = providers.metadata(symbol, mic);
        String provider = priceHistory.activeProvider().id();
        if (metadata.assetType() == null) {
            throw new ProviderMetadataIncompleteException("the instrument type '%s' is not supported"
                    .formatted(metadata.instrumentType()));
        }
        if (metadata.country() == null || metadata.currency() == null || metadata.mic() == null
                || metadata.exchange() == null || metadata.name() == null) {
            throw new ProviderMetadataIncompleteException("the provider did not report the exchange, MIC, currency "
                    + "and country");
        }
        return new NewStock(metadata.symbol(), metadata.name(), metadata.exchange(), metadata.mic(),
                metadata.currency(), metadata.country(), metadata.sector(), metadata.industry(),
                metadata.assetType(), provider);
    }

    private List<AdminStock> enrich(List<ManagedStock> page) {
        List<UUID> ids = page.stream().map(ManagedStock::id).toList();
        Map<UUID, StockDataSummary> summaries = inventory.summaries(ids);
        Map<UUID, Long> watchers = watchlists.countWatchers(ids);
        return page.stream().map(stock -> new AdminStock(stock, watchers.getOrDefault(stock.id(), 0L),
                summaries.get(stock.id()))).toList();
    }

    public record AdminStock(ManagedStock stock, long watchers, StockDataSummary marketData) {
    }

    public record AdminStockPage(List<AdminStock> content, int page, int size, long totalElements) {
    }

    public record ProviderLookup(String providerId, String providerName, boolean supported,
                                 List<ProviderLookupResult> results) {
    }

    public record ProviderLookupResult(ProviderInstrumentMatch match, boolean alreadyListed) {
    }

    public record Registration(AdminStock stock, MarketDataJobView job) {
    }

    /**
     * Either provider mode ({@code manual == null}: metadata comes from the provider) or manual entry.
     */
    public record RegisterStock(String symbol, String mic, ManualStock manual, boolean loadHistory) {
    }

    public record ManualStock(String companyName, String exchange, String mic, String currency, String country,
                              String sector, String industry, AssetType assetType) {

        NewStock toNewStock(String symbol) {
            return new NewStock(symbol, companyName, exchange, mic, currency, country, sector, industry, assetType,
                    "MANUAL");
        }
    }
}
