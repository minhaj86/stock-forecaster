package com.stockforecast.analytics.api;

import com.stockforecast.analytics.application.AnalyticsViews.PerformanceView;
import com.stockforecast.analytics.application.StockAnalyticsService;
import com.stockforecast.analytics.application.StockSnapshot;
import com.stockforecast.shared.domain.MarketPeriod;
import com.stockforecast.stock.StockCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The analytics read models are designed as client contracts and are returned as-is.
 */
@Tag(name = "Analytics", description = "Metrics computed from stored market data")
@RestController
@RequestMapping("/api/v1")
class AnalyticsController {

    private final StockAnalyticsService analytics;
    private final StockCatalog stockCatalog;

    AnalyticsController(StockAnalyticsService analytics, StockCatalog stockCatalog) {
        this.analytics = analytics;
        this.stockCatalog = stockCatalog;
    }

    @Operation(summary = "Metric snapshots for up to 50 symbols",
            description = "Price, daily change, 1W-1Y and YTD returns, 30/90-day volatility, 1-year maximum drawdown, "
                    + "RSI(14) and 3-month momentum. Ratios are decimal fractions (0.052 = 5.2%).")
    @GetMapping("/analytics/snapshots")
    List<StockSnapshot> snapshots(@RequestParam @Size(min = 1, max = 50) List<@NotBlank String> symbols) {
        return analytics.snapshotsForSymbols(symbols);
    }

    @Operation(summary = "Metric snapshot of one stock")
    @GetMapping("/stocks/{symbol}/analytics")
    StockSnapshot stock(@PathVariable String symbol) {
        return analytics.snapshot(symbol);
    }

    @Operation(summary = "Performance rebased to 100 at the start of the period, for 1-20 symbols")
    @GetMapping("/analytics/performance")
    PerformanceView performance(@RequestParam @Size(min = 1, max = 20) List<@NotBlank String> symbols,
                                @RequestParam(defaultValue = "1M") MarketPeriod period) {
        return analytics.performance(symbols.stream().distinct().map(stockCatalog::getBySymbol).toList(), period);
    }
}
