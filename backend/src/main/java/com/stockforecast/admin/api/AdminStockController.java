package com.stockforecast.admin.api;

import com.stockforecast.admin.api.AdminResponses.AdminStockResponse;
import com.stockforecast.admin.api.AdminResponses.JobSubmissionResponse;
import com.stockforecast.admin.api.AdminResponses.MarketDataJobResponse;
import com.stockforecast.admin.application.AdminMarketDataService;
import com.stockforecast.admin.application.AdminMarketDataService.JobWithRequester;
import com.stockforecast.admin.application.AdminMarketDataService.Submission;
import com.stockforecast.admin.application.AdminStockService;
import com.stockforecast.admin.application.AdminStockService.AdminStockPage;
import com.stockforecast.admin.application.AdminStockService.ManualStock;
import com.stockforecast.admin.application.AdminStockService.ProviderLookup;
import com.stockforecast.admin.application.AdminStockService.ProviderLookupResult;
import com.stockforecast.admin.application.AdminStockService.RegisterStock;
import com.stockforecast.admin.application.AdminStockService.Registration;
import com.stockforecast.identity.AuthenticatedUser;
import com.stockforecast.identity.CurrentUser;
import com.stockforecast.marketdata.ExistingDataStrategy;
import com.stockforecast.marketdata.MarketDataInterval;
import com.stockforecast.marketdata.MarketDataJobs.BackfillRequest;
import com.stockforecast.marketdata.MarketDataJobs.RepopulationRequest;
import com.stockforecast.shared.api.ApiError;
import com.stockforecast.shared.api.PageResponse;
import com.stockforecast.stock.AssetType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "Administration")
@RestController
@RequestMapping("/api/v1/admin/stocks")
class AdminStockController {

    private final AdminStockService stockService;
    private final AdminMarketDataService marketDataService;

    AdminStockController(AdminStockService stockService, AdminMarketDataService marketDataService) {
        this.stockService = stockService;
        this.marketDataService = marketDataService;
    }

    @Operation(summary = "All stocks (including inactive ones) with market-data coverage, ordered by symbol")
    @GetMapping
    PageResponse<AdminStockResponse> search(@RequestParam(required = false) @Size(max = 50) String query,
                                            @RequestParam(required = false) StockStatusFilter status,
                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        AdminStockPage result = stockService.search(query, status == null ? null : status == StockStatusFilter.ACTIVE,
                page, size);
        return PageResponse.of(result.content(), result.page(), result.size(), result.totalElements(),
                AdminStockResponse::from);
    }

    @Operation(summary = "Search the market-data provider for securities to add",
            description = "A synchronous, rate-limited provider call. supported=false means the provider cannot "
                    + "search (enter the stock manually).")
    @ApiResponse(responseCode = "429", description = "MARKET_DATA_PROVIDER_RATE_LIMITED",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "503", description = "MARKET_DATA_PROVIDER_UNAVAILABLE",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping("/provider-search")
    ProviderSearchResponse providerSearch(@RequestParam @NotBlank @Size(max = 50) String query) {
        return ProviderSearchResponse.from(stockService.searchProvider(query));
    }

    @Operation(summary = "Stock details including inactive stocks")
    @GetMapping("/{symbol}")
    AdminStockResponse get(@PathVariable String symbol) {
        return AdminStockResponse.from(stockService.get(symbol));
    }

    @Operation(summary = "Register a stock from provider metadata (or manually) and optionally load its history")
    @ApiResponse(responseCode = "201", description = "Registered")
    @ApiResponse(responseCode = "409", description = "STOCK_ALREADY_EXISTS",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "422", description = "PROVIDER_METADATA_UNAVAILABLE (enter the details manually)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping
    ResponseEntity<StockRegistrationResponse> create(@CurrentUser AuthenticatedUser actor,
                                                     @Valid @RequestBody CreateStockRequest request) {
        Registration registration = stockService.register(actor, request.toCommand());
        AdminStockResponse stock = AdminStockResponse.from(registration.stock());
        MarketDataJobResponse job = registration.job() == null ? null
                : MarketDataJobResponse.from(new JobWithRequester(registration.job(), actor.email()));
        return ResponseEntity.created(URI.create("/api/v1/admin/stocks/" + stock.symbol()))
                .body(new StockRegistrationResponse(stock, job));
    }

    @Operation(summary = "Deactivate a stock", description = "Hidden from search and watchlist additions; no longer "
            + "synchronised. Existing watchlist items and history stay.")
    @PostMapping("/{symbol}/deactivate")
    AdminStockResponse deactivate(@CurrentUser AuthenticatedUser actor, @PathVariable String symbol) {
        return AdminStockResponse.from(stockService.changeActive(actor, symbol, false));
    }

    @Operation(summary = "Activate a stock")
    @PostMapping("/{symbol}/activate")
    AdminStockResponse activate(@CurrentUser AuthenticatedUser actor, @PathVariable String symbol) {
        return AdminStockResponse.from(stockService.changeActive(actor, symbol, true));
    }

    @Operation(summary = "Queue an update from the newest stored bar (incremental, no confirmation needed)")
    @ApiResponse(responseCode = "202", description = "Job queued (or an identical job is already active)")
    @PostMapping("/{symbol}/market-data/update")
    ResponseEntity<JobSubmissionResponse> update(@CurrentUser AuthenticatedUser actor, @PathVariable String symbol) {
        return accepted(marketDataService.requestLatestUpdate(actor, symbol));
    }

    @Operation(summary = "Queue a historical backfill of a date range",
            description = "existingDataStrategy SKIP (default) keeps stored bars; UPDATE overwrites changed ones.")
    @ApiResponse(responseCode = "202", description = "Job queued")
    @PostMapping("/{symbol}/market-data/backfill")
    ResponseEntity<JobSubmissionResponse> backfill(@CurrentUser AuthenticatedUser actor, @PathVariable String symbol,
                                                   @Valid @RequestBody BackfillBody request) {
        return accepted(marketDataService.requestBackfill(actor, symbol, new BackfillRequest(request.fromDate(),
                request.toDate(), request.interval(), request.existingDataStrategy())));
    }

    @Operation(summary = "Queue a safe re-population of the stored history in a date range",
            description = "Fetches, validates and stages the provider data first; replaces stored bars in one "
                    + "transaction. Existing data is untouched if anything fails. Recalculates analytics and "
                    + "invalidates forecasts.")
    @ApiResponse(responseCode = "202", description = "Job queued")
    @PostMapping("/{symbol}/market-data/repopulate")
    ResponseEntity<JobSubmissionResponse> repopulate(@CurrentUser AuthenticatedUser actor,
                                                     @PathVariable String symbol,
                                                     @Valid @RequestBody RepopulateBody request) {
        return accepted(marketDataService.requestRepopulation(actor, symbol, new RepopulationRequest(
                request.fromDate(), request.toDate(), request.interval())));
    }

    static ResponseEntity<JobSubmissionResponse> accepted(Submission submission) {
        MarketDataJobResponse job = MarketDataJobResponse.from(submission.job());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/v1/admin/market-data/jobs/" + job.id()))
                .body(new JobSubmissionResponse(job, submission.created()));
    }

    enum StockStatusFilter {
        ACTIVE,
        INACTIVE
    }

    record CreateStockRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9.\\-]{0,19}") String symbol,
            @Size(max = 10) String mic,
            Boolean loadHistory,
            @Valid ManualStockRequest manual) {

        RegisterStock toCommand() {
            return new RegisterStock(symbol.strip(), mic, manual == null ? null : manual.toManualStock(),
                    loadHistory == null || loadHistory);
        }
    }

    record ManualStockRequest(
            @NotBlank @Size(max = 200) String companyName,
            @NotBlank @Size(max = 40) String exchange,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9]{4}") String mic,
            @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String country,
            @Size(max = 80) String sector,
            @Size(max = 120) String industry,
            @NotNull AssetType assetType) {

        ManualStock toManualStock() {
            return new ManualStock(companyName, exchange, mic, currency, country, sector, industry, assetType);
        }
    }

    record BackfillBody(@NotNull LocalDate fromDate, @NotNull LocalDate toDate, MarketDataInterval interval,
                        ExistingDataStrategy existingDataStrategy) {
    }

    record RepopulateBody(@NotNull LocalDate fromDate, @NotNull LocalDate toDate, MarketDataInterval interval) {
    }

    record StockRegistrationResponse(AdminStockResponse stock, MarketDataJobResponse job) {
    }

    record ProviderSearchResponse(ProviderRef provider, boolean supported, List<ProviderResult> results) {

        static ProviderSearchResponse from(ProviderLookup lookup) {
            return new ProviderSearchResponse(new ProviderRef(lookup.providerId(), lookup.providerName()),
                    lookup.supported(), lookup.results().stream().map(ProviderResult::from).toList());
        }
    }

    record ProviderRef(String id, String name) {
    }

    record ProviderResult(String symbol, String name, String exchange, String mic, String currency, String country,
                          String instrumentType, AssetType assetType, boolean alreadyListed) {

        static ProviderResult from(ProviderLookupResult result) {
            var match = result.match();
            return new ProviderResult(match.symbol(), match.name(), match.exchange(), match.mic(), match.currency(),
                    match.country(), match.instrumentType(), match.assetType(), result.alreadyListed());
        }
    }
}
