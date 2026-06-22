package com.stockforecast.stock;

import com.stockforecast.shared.domain.DomainException;
import com.stockforecast.shared.domain.ErrorCategory;

public class UnknownStockException extends DomainException {

    public UnknownStockException(String symbol) {
        super("STOCK_NOT_FOUND", ErrorCategory.NOT_FOUND, "No stock with symbol '%s' is available".formatted(symbol));
    }
}
