package com.stockforecast.admin.api;

import com.stockforecast.admin.application.AdminMarketDataService.JobWithRequester;
import com.stockforecast.admin.application.AdminStockService.AdminStock;
import com.stockforecast.admin.application.AdminUserService.UserDetails;
import com.stockforecast.admin.application.AdminUserService.UserView;
import com.stockforecast.identity.AccountStatus;
import com.stockforecast.identity.ManagedAccount;
import com.stockforecast.identity.Role;
import com.stockforecast.marketdata.ExistingDataStrategy;
import com.stockforecast.marketdata.MarketDataInterval;
import com.stockforecast.marketdata.MarketDataInventory.StockDataSummary;
import com.stockforecast.marketdata.MarketDataJobStatus;
import com.stockforecast.marketdata.MarketDataJobTrigger;
import com.stockforecast.marketdata.MarketDataJobType;
import com.stockforecast.marketdata.MarketDataJobView;
import com.stockforecast.marketdata.MarketDataStatus;
import com.stockforecast.stock.AssetType;
import com.stockforecast.stock.ManagedStock;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response bodies of the admin API (see the admin contract in the README/API docs). Never contain credentials.
 */
final class AdminResponses {

    private AdminResponses() {
    }

    record AdminUserResponse(UUID id, String email, String displayName, Role role, AccountStatus status,
                             boolean passwordChangeRequired, Instant createdAt, Instant updatedAt,
                             Instant lastLoginAt) {

        static AdminUserResponse from(UserView view) {
            ManagedAccount account = view.account();
            return new AdminUserResponse(account.id(), account.email(), view.displayName(), account.role(),
                    account.status(), account.passwordChangeRequired(), account.createdAt(), account.updatedAt(),
                    account.lastLoginAt());
        }
    }

    record AdminUserDetailsResponse(UUID id, String email, String displayName, Role role, AccountStatus status,
                                    boolean passwordChangeRequired, Instant createdAt, Instant updatedAt,
                                    Instant lastLoginAt, String timeZone, int watchlistCount) {

        static AdminUserDetailsResponse from(UserDetails details) {
            ManagedAccount account = details.user().account();
            return new AdminUserDetailsResponse(account.id(), account.email(), details.user().displayName(),
                    account.role(), account.status(), account.passwordChangeRequired(), account.createdAt(),
                    account.updatedAt(), account.lastLoginAt(), details.timeZone(), details.watchlistCount());
        }
    }

    record AdminStockResponse(UUID id, String symbol, String companyName, String exchange, String mic, String currency,
                              String country, String sector, String industry, AssetType assetType, boolean active,
                              String source, Instant createdAt, Instant updatedAt, long watchers,
                              MarketDataSummaryResponse marketData) {

        static AdminStockResponse from(AdminStock view) {
            ManagedStock stock = view.stock();
            return new AdminStockResponse(stock.id(), stock.symbol(), stock.companyName(), stock.exchange(),
                    stock.mic(), stock.currency(), stock.country(), stock.sector(), stock.industry(),
                    stock.assetType(), stock.active(), stock.source(), stock.createdAt(), stock.updatedAt(),
                    view.watchers(), MarketDataSummaryResponse.from(view.marketData()));
        }
    }

    record MarketDataSummaryResponse(MarketDataStatus.State dataStatus, String provider, LocalDate firstBarDate,
                                     LocalDate lastBarDate, long barCount, Instant lastSuccessAt,
                                     Instant lastAttemptAt, String lastError, ActiveJobResponse activeJob) {

        static MarketDataSummaryResponse from(StockDataSummary summary) {
            if (summary == null) {
                return new MarketDataSummaryResponse(MarketDataStatus.State.NOT_TRACKED, null, null, null, 0, null,
                        null, null, null);
            }
            return new MarketDataSummaryResponse(summary.dataStatus(), summary.provider(), summary.firstBarDate(),
                    summary.lastBarDate(), summary.barCount(), summary.lastSuccessAt(), summary.lastAttemptAt(),
                    summary.lastError(), summary.activeJob() == null ? null
                    : new ActiveJobResponse(summary.activeJob().id(), summary.activeJob().type(),
                    summary.activeJob().status(), summary.activeJob().progress()));
        }
    }

    record ActiveJobResponse(UUID id, MarketDataJobType type, MarketDataJobStatus status, int progress) {
    }

    record RequesterResponse(UUID id, String email) {
    }

    record MarketDataJobResponse(UUID id, MarketDataJobType type, MarketDataJobStatus status,
                                 MarketDataJobTrigger trigger, UUID stockId, String symbol, String companyName,
                                 String provider, MarketDataInterval interval,
                                 ExistingDataStrategy existingDataStrategy, LocalDate fromDate, LocalDate toDate,
                                 RequesterResponse requestedBy, Instant requestedAt, Instant startedAt,
                                 Instant completedAt, Long durationMs, int progress, String statusMessage,
                                 int attempts, int maxAttempts, Instant nextAttemptAt, int recordsFetched,
                                 int recordsInserted, int recordsUpdated, int recordsSkipped, int recordsFailed,
                                 int recordsDeleted, String errorCode, String errorMessage, boolean retryable,
                                 UUID retryOfJobId, UUID batchId) {

        static MarketDataJobResponse from(JobWithRequester withRequester) {
            MarketDataJobView job = withRequester.job();
            return new MarketDataJobResponse(job.id(), job.type(), job.status(), job.trigger(), job.stockId(),
                    job.symbol(), job.companyName(), job.provider(), job.interval(), job.existingDataStrategy(),
                    job.fromDate(), job.toDate(), job.requestedBy() == null ? null
                    : new RequesterResponse(job.requestedBy(), withRequester.requestedByEmail()), job.requestedAt(),
                    job.startedAt(), job.completedAt(), job.durationMs(), job.progress(), job.statusMessage(),
                    job.attempts(), job.maxAttempts(), job.nextAttemptAt(), job.recordsFetched(),
                    job.recordsInserted(), job.recordsUpdated(), job.recordsSkipped(), job.recordsFailed(),
                    job.recordsDeleted(), job.errorCode(), job.errorMessage(), job.retryable(), job.retryOfJobId(),
                    job.batchId());
        }
    }

    /**
     * @param created {@code false} if an identical job was already queued or running and is returned instead
     */
    record JobSubmissionResponse(MarketDataJobResponse job, boolean created) {
    }
}
