package com.stockforecast.forecasting.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * Dates of future trading sessions. Forecast horizons are counted in trading days, never calendar days.
 */
public interface TradingCalendar {

    /** The {@code count} trading days strictly after {@code date}. */
    List<LocalDate> nextTradingDays(LocalDate date, int count);
}
