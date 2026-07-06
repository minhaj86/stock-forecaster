package com.stockforecast.watchlist.domain;

import com.stockforecast.shared.domain.DomainException;
import com.stockforecast.shared.domain.ErrorCategory;

/**
 * Business failures of the watchlist module.
 */
public final class WatchlistExceptions {

    private WatchlistExceptions() {
    }

    public static class WatchlistNotFoundException extends DomainException {
        public WatchlistNotFoundException() {
            super("WATCHLIST_NOT_FOUND", ErrorCategory.NOT_FOUND, "Watchlist not found");
        }
    }

    public static class WatchlistNameTakenException extends DomainException {
        public WatchlistNameTakenException(String name) {
            super("WATCHLIST_NAME_TAKEN", ErrorCategory.CONFLICT,
                    "You already have a watchlist named '%s'".formatted(name));
        }
    }

    public static class WatchlistLimitReachedException extends DomainException {
        public WatchlistLimitReachedException(int limit) {
            super("WATCHLIST_LIMIT_REACHED", ErrorCategory.CONFLICT,
                    "You can have at most %d watchlists".formatted(limit));
        }
    }

    public static class WatchlistFullException extends DomainException {
        public WatchlistFullException(int limit) {
            super("WATCHLIST_FULL", ErrorCategory.CONFLICT,
                    "A watchlist can contain at most %d stocks".formatted(limit));
        }
    }

    public static class StockAlreadyInWatchlistException extends DomainException {
        public StockAlreadyInWatchlistException() {
            super("STOCK_ALREADY_IN_WATCHLIST", ErrorCategory.CONFLICT, "This stock is already in the watchlist");
        }
    }

    public static class StockNotInWatchlistException extends DomainException {
        public StockNotInWatchlistException() {
            super("STOCK_NOT_IN_WATCHLIST", ErrorCategory.NOT_FOUND, "This stock is not in the watchlist");
        }
    }

    public static class InvalidWatchlistException extends DomainException {
        public InvalidWatchlistException(String code, String message) {
            super(code, ErrorCategory.VALIDATION, message);
        }
    }
}
