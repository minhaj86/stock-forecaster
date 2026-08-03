package com.stockforecast.analytics.api;

import com.stockforecast.analytics.application.AnalyticsViews.DashboardView;
import com.stockforecast.analytics.application.DashboardService;
import com.stockforecast.identity.AuthenticatedUser;
import com.stockforecast.identity.CurrentUser;
import com.stockforecast.shared.domain.MarketPeriod;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@Tag(name = "Dashboard")
@RestController
@RequestMapping("/api/v1/dashboard")
class DashboardController {

    private final DashboardService dashboardService;

    DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Operation(summary = "Personalised dashboard of my watched stocks",
            description = "Covers all my watchlists, or one when watchlistId is given. The period drives the "
                    + "performance chart and the top/weakest performer.")
    @GetMapping
    DashboardView dashboard(@CurrentUser AuthenticatedUser user,
                            @RequestParam(defaultValue = "1M") MarketPeriod period,
                            @RequestParam(required = false) UUID watchlistId) {
        return dashboardService.dashboard(user.id(), period, Optional.ofNullable(watchlistId));
    }
}
