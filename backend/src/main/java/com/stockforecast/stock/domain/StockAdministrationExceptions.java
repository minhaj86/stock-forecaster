package com.stockforecast.stock.domain;

import com.stockforecast.shared.domain.DomainException;
import com.stockforecast.shared.domain.ErrorCategory;

/**
 * Business failures of catalogue administration.
 */
public final class StockAdministrationExceptions {

    private StockAdministrationExceptions() {
    }

    public static class StockAlreadyExistsException extends DomainException {
        public StockAlreadyExistsException(String symbol) {
            super("STOCK_ALREADY_EXISTS", ErrorCategory.CONFLICT,
                    "A stock with symbol '%s' already exists in the catalogue".formatted(symbol));
        }
    }

    public static class InvalidStockException extends DomainException {
        public InvalidStockException(String message) {
            super("INVALID_STOCK", ErrorCategory.VALIDATION, message);
        }
    }
}
