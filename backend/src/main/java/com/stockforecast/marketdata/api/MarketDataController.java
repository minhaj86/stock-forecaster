package com.stockforecast.marketdata.api;

import com.stockforecast.marketdata.DailyBar;
import com.stockforecast.marketdata.MarketDataStatus;
import com.stockforecast.marketdata.PriceHistory;
import com.stockforecast.marketdata.PriceHistory.ProviderInfo;
import com.stockforecast.shared.domain.MarketPeriod;
import com.stockforecast.stock.StockCatalog;
import com.stockforecast.stock.StockRef;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "Market data", description = "Stored end-of-day prices (never fetched synchronously from providers)")
@RestController
@RequestMapping("/api/v1")
class MarketDataController {

    private final PriceHistory priceHistory;
    private final StockCatalog stockCatalog;

    MarketDataController(PriceHistory priceHistory, StockCatalog stockCatalog) {
        this.priceHistory = priceHistory;
        this.stockCatalog = stockCatalog;
    }

    @Operation(summary = "Daily OHLCV bars for a period (5D, 1M, 3M, 6M, YTD, 1Y, 5Y, MAX)",
            description = "Bars are only collected for watched stocks; dataStatus explains an empty result.")
    @GetMapping("/stocks/{symbol}/prices")
    PriceHistoryResponse prices(@PathVariable String symbol,
                                @RequestParam(defaultValue = "1Y") MarketPeriod period) {
        StockRef stock = stockCatalog.getBySymbol(symbol);
        MarketDataStatus status = priceHistory.findStatuses(List.of(stock.id())).get(stock.id());
        LocalDate asOf = status.lastBarDate();
        List<BarResponse> bars = asOf == null ? List.of()
                : priceHistory.findDailyBars(stock.id(), period.startDate(asOf), asOf).stream()
                .map(BarResponse::from).toList();
        return new PriceHistoryResponse(stock.symbol(), stock.currency(), period.code(), "1d", status.state(),
                status.provider(), asOf, status.lastSuccessAt(), bars);
    }

    @Operation(summary = "The active market-data provider",
            description = "Clients must label data from synthetic providers as simulated.")
    @GetMapping("/market-data/provider")
    ProviderInfo provider() {
        return priceHistory.activeProvider();
    }

    record PriceHistoryResponse(String symbol, String currency, String period, String interval,
                                MarketDataStatus.State dataStatus, String dataProvider, LocalDate asOf,
                                Instant lastSyncedAt, List<BarResponse> bars) {
    }

    record BarResponse(LocalDate date, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
                       BigDecimal adjustedClose, long volume) {

        static BarResponse from(DailyBar bar) {
            return new BarResponse(bar.date(), bar.open(), bar.high(), bar.low(), bar.close(), bar.adjustedClose(),
                    bar.volume());
        }
    }
}
