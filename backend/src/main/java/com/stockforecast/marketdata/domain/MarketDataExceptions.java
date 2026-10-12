package com.stockforecast.marketdata.domain;

import com.stockforecast.marketdata.MarketDataInterval;
import com.stockforecast.marketdata.MarketDataJobStatus;
import com.stockforecast.shared.domain.DomainException;
import com.stockforecast.shared.domain.ErrorCategory;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-facing business failures of market-data administration. Messages are safe to show to administrators.
 */
public final class MarketDataExceptions {

    private MarketDataExceptions() {
    }

    public static class MarketDataJobNotFoundException extends DomainException {
        public MarketDataJobNotFoundException() {
            super("MARKET_DATA_JOB_NOT_FOUND", ErrorCategory.NOT_FOUND, "Market-data job not found");
        }
    }

    public static class MarketDataJobNotRetryableException extends DomainException {
        public MarketDataJobNotRetryableException(MarketDataJobStatus status, String errorCode) {
            super("MARKET_DATA_JOB_NOT_RETRYABLE", ErrorCategory.CONFLICT, status == MarketDataJobStatus.FAILED
                    ? "This job failed with a permanent error (%s); retrying would fail again. Fix the cause and start a new job."
                    .formatted(errorCode)
                    : "Only failed jobs can be retried (this job is %s)".formatted(status));
        }
    }

    public static class MarketDataJobNotCancellableException extends DomainException {
        public MarketDataJobNotCancellableException(MarketDataJobStatus status) {
            super("MARKET_DATA_JOB_NOT_CANCELLABLE", ErrorCategory.CONFLICT,
                    "Only queued jobs can be cancelled (this job is %s)".formatted(status));
        }
    }

    public static class StockInactiveException extends DomainException {
        public StockInactiveException(String symbol) {
            super("STOCK_INACTIVE", ErrorCategory.CONFLICT,
                    "%s is inactive; activate it before loading market data".formatted(symbol));
        }
    }

    public static class InvalidDateRangeException extends DomainException {
        public InvalidDateRangeException(String message) {
            super("INVALID_DATE_RANGE", ErrorCategory.VALIDATION, message);
        }
    }

    public static class UnsupportedMarketDataIntervalException extends DomainException {
        public UnsupportedMarketDataIntervalException(MarketDataInterval interval, String provider) {
            super("UNSUPPORTED_MARKET_DATA_INTERVAL", ErrorCategory.VALIDATION,
                    "Interval %s is not supported by %s or by the price store".formatted(interval, provider));
        }
    }

    public static class ProviderSymbolNotFoundException extends DomainException {
        public ProviderSymbolNotFoundException(String symbol, String provider) {
            super("PROVIDER_SYMBOL_NOT_FOUND", ErrorCategory.NOT_FOUND,
                    "%s does not know the symbol '%s'".formatted(provider, symbol));
        }
    }

    public static class ProviderMetadataUnavailableException extends DomainException {
        public ProviderMetadataUnavailableException(String provider) {
            super("PROVIDER_METADATA_UNAVAILABLE", ErrorCategory.UNPROCESSABLE,
                    "%s cannot supply stock metadata; enter the details manually".formatted(provider));
        }
    }

    public static class MarketDataProviderRateLimitedException extends DomainException {
        public MarketDataProviderRateLimitedException(String provider, Duration retryAfter) {
            super("MARKET_DATA_PROVIDER_RATE_LIMITED", ErrorCategory.RATE_LIMITED,
                    "The %s rate limit has been reached. Try again in about %d seconds."
                            .formatted(provider, Math.max(1, retryAfter.toSeconds())),
                    Map.of("retryAfterSeconds", Math.max(1, retryAfter.toSeconds())));
        }
    }

    public static class MarketDataProviderUnavailableException extends DomainException {
        public MarketDataProviderUnavailableException(String provider, String providerErrorCode, String message,
                                                      boolean retryable) {
            super("MARKET_DATA_PROVIDER_UNAVAILABLE", ErrorCategory.UNAVAILABLE,
                    "%s request failed: %s".formatted(provider, message), details(providerErrorCode, retryable));
        }

        private static Map<String, Object> details(String providerErrorCode, boolean retryable) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("providerErrorCode", providerErrorCode);
            details.put("retryable", retryable);
            return details;
        }
    }
}
