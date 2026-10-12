package com.stockforecast.admin.api;

import com.stockforecast.admin.application.AdminDashboardService;
import com.stockforecast.admin.application.AdminDashboardService.Dashboard;
import com.stockforecast.marketdata.ProviderHealth;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Tag(name = "Administration", description = "Admin console (ADMIN role required)")
@RestController
@RequestMapping("/api/v1/admin/dashboard")
class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    AdminDashboardController(AdminDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Operation(summary = "Operational overview: users, stocks, market data and jobs")
    @GetMapping
    DashboardResponse dashboard() {
        return DashboardResponse.from(dashboardService.dashboard());
    }

    record DashboardResponse(Instant generatedAt, Users users, Stocks stocks, MarketData marketData, Jobs jobs) {

        static DashboardResponse from(Dashboard dashboard) {
            ProviderHealth health = dashboard.providerHealth();
            return new DashboardResponse(dashboard.generatedAt(),
                    new Users(dashboard.users().total(), dashboard.users().active(), dashboard.users().disabled(),
                            dashboard.users().locked(), dashboard.users().administrators(),
                            dashboard.users().registeredLast7Days()),
                    new Stocks(dashboard.catalogue().total(), dashboard.catalogue().active(),
                            dashboard.marketData().trackedStocks(), dashboard.marketData().stocksWithData(),
                            dashboard.marketData().trackedStocks() - dashboard.marketData().stocksWithData()),
                    new MarketData(new Provider(dashboard.provider().id(), dashboard.provider().name(),
                            dashboard.provider().synthetic(), health.status(), health.checkedAt()),
                            dashboard.marketData().lastSuccessfulSyncAt(), dashboard.jobs().lastFailedAt(),
                            dashboard.jobs().lastFailureMessage()),
                    new Jobs(dashboard.jobs().queued(), dashboard.jobs().running(), dashboard.jobs().completedToday(),
                            dashboard.jobs().failedToday()));
        }
    }

    record Users(long total, long active, long disabled, long locked, long administrators, long newLast7Days) {
    }

    record Stocks(long total, long active, long tracked, long withMarketData, long missingData) {
    }

    record MarketData(Provider provider, Instant lastSuccessfulSyncAt, Instant lastFailedSyncAt,
                      String lastFailureMessage) {
    }

    record Provider(String id, String name, boolean synthetic, ProviderHealth.Status status, Instant checkedAt) {
    }

    record Jobs(long queued, long running, long completedToday, long failedToday) {
    }
}
