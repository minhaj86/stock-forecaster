package com.stockforecast.analytics.application;

import com.stockforecast.shared.domain.DomainException;
import com.stockforecast.shared.domain.ErrorCategory;

public class WatchlistNotFoundForDashboardException extends DomainException {

    public WatchlistNotFoundForDashboardException() {
        super("WATCHLIST_NOT_FOUND", ErrorCategory.NOT_FOUND, "Watchlist not found");
    }
}
