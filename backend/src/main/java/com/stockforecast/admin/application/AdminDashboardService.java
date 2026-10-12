package com.stockforecast.admin.application;

import com.stockforecast.identity.AccountAdministration;
import com.stockforecast.identity.AccountStatistics;
import com.stockforecast.marketdata.MarketDataInventory;
import com.stockforecast.marketdata.MarketDataInventory.InventoryStatistics;
import com.stockforecast.marketdata.MarketDataJobs;
import com.stockforecast.marketdata.MarketDataJobs.JobStatistics;
import com.stockforecast.marketdata.MarketDataProviders;
import com.stockforecast.marketdata.PriceHistory;
import com.stockforecast.marketdata.PriceHistory.ProviderInfo;
import com.stockforecast.marketdata.ProviderHealth;
import com.stockforecast.stock.StockAdministration;
import com.stockforecast.stock.StockAdministration.CatalogueCounts;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Operational overview. Reads only; the provider health is the last known result (no provider call).
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardService {

    private final AccountAdministration accounts;
    private final StockAdministration stocks;
    private final MarketDataInventory inventory;
    private final MarketDataJobs jobs;
    private final MarketDataProviders providers;
    private final PriceHistory priceHistory;
    private final Clock clock;

    public AdminDashboardService(AccountAdministration accounts, StockAdministration stocks,
                                 MarketDataInventory inventory, MarketDataJobs jobs, MarketDataProviders providers,
                                 PriceHistory priceHistory, Clock clock) {
        this.accounts = accounts;
        this.stocks = stocks;
        this.inventory = inventory;
        this.jobs = jobs;
        this.providers = providers;
        this.priceHistory = priceHistory;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        InventoryStatistics inventoryStatistics = inventory.statistics();
        JobStatistics jobStatistics = jobs.statistics();
        return new Dashboard(clock.instant(), accounts.statistics(), stocks.counts(), inventoryStatistics,
                priceHistory.activeProvider(), providers.lastKnownHealth(), jobStatistics);
    }

    public record Dashboard(Instant generatedAt, AccountStatistics users, CatalogueCounts catalogue,
                            InventoryStatistics marketData, ProviderInfo provider, ProviderHealth providerHealth,
                            JobStatistics jobs) {
    }
}
