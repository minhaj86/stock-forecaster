package com.stockforecast.forecasting.domain;

import com.stockforecast.shared.domain.DomainException;
import com.stockforecast.shared.domain.ErrorCategory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Raised instead of producing an unreliable forecast from too short a history.
 */
public class InsufficientForecastHistoryException extends DomainException {

    public static final String CODE = "INSUFFICIENT_FORECAST_HISTORY";

    private final int required;
    private final int available;

    public InsufficientForecastHistoryException(int required, int available) {
        this(required, available, null);
    }

    /**
     * @param dataStatus synchronisation state of the stock's market data, if known (e.g. {@code PENDING})
     */
    public InsufficientForecastHistoryException(int required, int available, String dataStatus) {
        super(CODE, ErrorCategory.UNPROCESSABLE,
                "Not enough historical data is available: %d trading days are required, %d are available"
                        .formatted(required, available),
                details(required, available, dataStatus));
        this.required = required;
        this.available = available;
    }

    public int required() {
        return required;
    }

    public int available() {
        return available;
    }

    private static Map<String, Object> details(int required, int available, String dataStatus) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("requiredObservations", required);
        details.put("availableObservations", available);
        if (dataStatus != null) {
            details.put("dataStatus", dataStatus);
        }
        return details;
    }
}
