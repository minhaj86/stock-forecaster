package com.stockforecast.forecasting.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Monday to Friday sessions. Exchange holidays are not modelled, so a projected date can fall on a holiday; the
 * horizon itself is unaffected because it is defined in trading days.
 */
public final class WeekdayTradingCalendar implements TradingCalendar {

    @Override
    public List<LocalDate> nextTradingDays(LocalDate date, int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
        List<LocalDate> result = new ArrayList<>(count);
        LocalDate current = date;
        while (result.size() < count) {
            current = current.plusDays(1);
            if (current.getDayOfWeek() != DayOfWeek.SATURDAY && current.getDayOfWeek() != DayOfWeek.SUNDAY) {
                result.add(current);
            }
        }
        return result;
    }
}
