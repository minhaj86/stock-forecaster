package com.stockforecast.forecasting.domain;

import com.stockforecast.shared.domain.DomainException;
import com.stockforecast.shared.domain.ErrorCategory;

/**
 * The stored history violates a modelling precondition (duplicate, unordered or non-positive observations).
 */
public class InvalidPriceHistoryException extends DomainException {

    public InvalidPriceHistoryException(String problem) {
        super("INVALID_PRICE_HISTORY", ErrorCategory.UNPROCESSABLE,
                "The price history cannot be used for forecasting: " + problem);
    }
}
