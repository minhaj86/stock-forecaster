package com.stockforecast.stock.api;

import com.stockforecast.shared.api.PageResponse;
import com.stockforecast.stock.application.StockCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Stocks", description = "Stock catalogue and search")
@RestController
@RequestMapping("/api/v1/stocks")
class StockController {

    private final StockCatalogService catalog;

    StockController(StockCatalogService catalog) {
        this.catalog = catalog;
    }

    @Operation(summary = "Type-ahead search by symbol, company name or alias",
            description = "Ranked: exact symbol, symbol prefix, name prefix, then substring matches.")
    @GetMapping("/search")
    List<StockResponse> search(@RequestParam @NotBlank @Size(max = 50) String q,
                               @RequestParam(defaultValue = "8") @Min(1) @Max(20) int limit) {
        return catalog.search(q, limit).stream().map(StockResponse::from).toList();
    }

    @Operation(summary = "Browse the catalogue, optionally filtered by sector")
    @GetMapping
    PageResponse<StockResponse> browse(@RequestParam(required = false) @Size(max = 80) String sector,
                                       @RequestParam(defaultValue = "0") @Min(0) int page,
                                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        StockCatalogService.CataloguePage result = catalog.browse(sector, page, size);
        return PageResponse.of(result.content(), result.page(), result.size(), result.totalElements(),
                StockResponse::from);
    }

    @Operation(summary = "Sectors present in the catalogue")
    @GetMapping("/sectors")
    List<String> sectors() {
        return catalog.sectors();
    }

    @Operation(summary = "Stock metadata")
    @GetMapping("/{symbol}")
    StockResponse get(@PathVariable String symbol) {
        return StockResponse.from(catalog.getBySymbol(symbol));
    }
}
