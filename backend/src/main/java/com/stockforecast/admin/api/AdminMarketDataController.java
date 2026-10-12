package com.stockforecast.admin.api;

import com.stockforecast.admin.api.AdminResponses.JobSubmissionResponse;
import com.stockforecast.admin.api.AdminResponses.MarketDataJobResponse;
import com.stockforecast.admin.application.AdminMarketDataService;
import com.stockforecast.admin.application.AdminMarketDataService.BulkLoad;
import com.stockforecast.admin.application.AdminMarketDataService.JobList;
import com.stockforecast.identity.AuthenticatedUser;
import com.stockforecast.identity.CurrentUser;
import com.stockforecast.marketdata.ExistingDataStrategy;
import com.stockforecast.marketdata.MarketDataInterval;
import com.stockforecast.marketdata.MarketDataJobStatus;
import com.stockforecast.marketdata.MarketDataJobTrigger;
import com.stockforecast.marketdata.MarketDataJobType;
import com.stockforecast.marketdata.MarketDataJobs.BackfillRequest;
import com.stockforecast.marketdata.MarketDataJobs.SyncSummary;
import com.stockforecast.marketdata.MarketDataProviders.ProviderDescriptor;
import com.stockforecast.marketdata.MarketDataProviders.ProviderOverview;
import com.stockforecast.marketdata.MarketDataProviders.ProviderUsage;
import com.stockforecast.marketdata.ProviderCapabilities;
import com.stockforecast.marketdata.ProviderHealth;
import com.stockforecast.shared.api.ApiError;
import com.stockforecast.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Administration")
@RestController
@RequestMapping("/api/v1/admin/market-data")
class AdminMarketDataController {

    private final AdminMarketDataService marketDataService;

    AdminMarketDataController(AdminMarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @Operation(summary = "Queue an incremental update for every watched, active stock without an active job")
    @ApiResponse(responseCode = "202", description = "Jobs queued")
    @PostMapping("/synchronize")
    ResponseEntity<SyncSummary> synchronize(@CurrentUser AuthenticatedUser actor) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(marketDataService.synchronizeWatchedStocks(actor));
    }

    @Operation(summary = "Queue a historical backfill for several stocks (executed one after another)")
    @ApiResponse(responseCode = "202", description = "Jobs queued")
    @PostMapping("/bulk-load")
    ResponseEntity<BulkLoadResponse> bulkLoad(@CurrentUser AuthenticatedUser actor,
                                              @Valid @RequestBody BulkLoadRequest request) {
        BulkLoad bulk = marketDataService.requestBulkLoad(actor, request.symbols(), new BackfillRequest(
                request.fromDate(), request.toDate(), request.interval(), request.existingDataStrategy()));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new BulkLoadResponse(bulk.batchId(),
                bulk.jobs().stream().map(MarketDataJobResponse::from).toList()));
    }

    @Operation(summary = "Job history, newest first")
    @GetMapping("/jobs")
    PageResponse<MarketDataJobResponse> jobs(@RequestParam(required = false) MarketDataJobStatus status,
                                             @RequestParam(required = false) MarketDataJobType type,
                                             @RequestParam(required = false) MarketDataJobTrigger trigger,
                                             @RequestParam(required = false) @Size(max = 20) String symbol,
                                             @RequestParam(defaultValue = "0") @Min(0) int page,
                                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        JobList result = marketDataService.findJobs(status, type, trigger, symbol, page, size);
        return PageResponse.of(result.content(), result.page(), result.size(), result.totalElements(),
                MarketDataJobResponse::from);
    }

    @Operation(summary = "Job details and progress")
    @ApiResponse(responseCode = "404", description = "MARKET_DATA_JOB_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping("/jobs/{jobId}")
    MarketDataJobResponse job(@PathVariable UUID jobId) {
        return MarketDataJobResponse.from(marketDataService.getJob(jobId));
    }

    @Operation(summary = "Retry a failed job with a transient error (creates a new, linked job)")
    @ApiResponse(responseCode = "202", description = "New job queued")
    @ApiResponse(responseCode = "409", description = "MARKET_DATA_JOB_NOT_RETRYABLE",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping("/jobs/{jobId}/retry")
    ResponseEntity<JobSubmissionResponse> retry(@CurrentUser AuthenticatedUser actor, @PathVariable UUID jobId) {
        return AdminStockController.accepted(marketDataService.retry(actor, jobId));
    }

    @Operation(summary = "Cancel a queued job")
    @ApiResponse(responseCode = "409", description = "MARKET_DATA_JOB_NOT_CANCELLABLE",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping("/jobs/{jobId}/cancel")
    MarketDataJobResponse cancel(@CurrentUser AuthenticatedUser actor, @PathVariable UUID jobId) {
        return MarketDataJobResponse.from(marketDataService.cancel(actor, jobId));
    }

    @Operation(summary = "Active provider: configuration, capabilities, health, usage and rate limit",
            description = "The API key is never returned, only whether it is configured.")
    @GetMapping("/providers")
    ProvidersResponse providers() {
        return ProvidersResponse.from(marketDataService.providerOverview());
    }

    @Operation(summary = "Provider health",
            description = "refresh=true runs an active check (one provider request, throttled); otherwise the cached "
                    + "result is returned.")
    @GetMapping("/providers/health")
    ProviderHealth health(@RequestParam(defaultValue = "false") boolean refresh) {
        return marketDataService.providerHealth(refresh);
    }

    record BulkLoadRequest(@NotEmpty @Size(max = 50) List<@NotBlank @Size(max = 20) String> symbols,
                           @NotNull LocalDate fromDate, @NotNull LocalDate toDate, MarketDataInterval interval,
                           ExistingDataStrategy existingDataStrategy) {
    }

    record BulkLoadResponse(UUID batchId, List<MarketDataJobResponse> jobs) {
    }

    record ProvidersResponse(ActiveProvider active, List<ProviderDescriptor> available, String configurationHint) {

        static ProvidersResponse from(ProviderOverview overview) {
            return new ProvidersResponse(new ActiveProvider(overview.info().id(), overview.info().name(),
                    overview.info().synthetic(), overview.info().description(), overview.apiKeyRequired(),
                    overview.apiKeyConfigured(), overview.capabilities(), overview.health(), overview.usage()),
                    overview.available(), overview.configurationHint());
        }
    }

    /**
     * @param apiKeyRequired   {@code false} for providers without credentials (synthetic demo data)
     * @param apiKeyConfigured an API key is present; the key itself is never returned
     */
    record ActiveProvider(String id, String name, boolean synthetic, String description, boolean apiKeyRequired,
                          boolean apiKeyConfigured, ProviderCapabilities capabilities, ProviderHealth health,
                          ProviderUsage usage) {
    }
}
