package com.stockforecast.marketdata.application;

import com.stockforecast.marketdata.DailyBar;
import com.stockforecast.marketdata.MarketDataInterval;
import com.stockforecast.marketdata.application.MarketDataProvider.HistoricalPrice;
import com.stockforecast.marketdata.application.MarketDataProvider.HistoricalPrices;
import com.stockforecast.stock.StockRef;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Checks provider data before anything is stored. Malformed external data is never persisted blindly.
 *
 * <ul>
 *     <li>Response level (the whole response is rejected): wrong instrument, wrong currency, wrong interval,
 *     duplicate dates.</li>
 *     <li>Bar level (the bar is rejected and counted as failed): missing date, date outside the requested range or in
 *     the future, missing, non-positive or implausible prices, inconsistent OHLC ({@code high ≥ open, close, low};
 *     {@code low ≤ open, close}), missing or negative volume.</li>
 * </ul>
 * Valid prices are normalised to the stored precision (6 decimals).
 */
@Service
public class MarketDataValidationService {

    /** Far above any listed share price, far below the column limit: catches unit and parsing errors. */
    static final BigDecimal MAX_PLAUSIBLE_PRICE = new BigDecimal("10000000");
    private static final int STORED_SCALE = 6;
    private static final int REPORTED_VIOLATIONS = 3;

    private final Clock clock;

    public MarketDataValidationService(Clock clock) {
        this.clock = clock;
    }

    public ValidationReport validate(StockRef stock, LocalDate from, LocalDate to, MarketDataInterval interval,
                                     HistoricalPrices response) {
        int fetched = response.prices().size();
        String rejection = rejectionReason(stock, interval, response);
        if (rejection != null) {
            return new ValidationReport(fetched, List.of(), List.of(), rejection);
        }
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        List<DailyBar> valid = new ArrayList<>();
        List<Violation> violations = new ArrayList<>();
        for (HistoricalPrice price : response.prices()) {
            String problem = problemWith(price, from, to, today);
            if (problem == null) {
                valid.add(toBar(price));
            } else {
                violations.add(new Violation(price.date(), problem));
            }
        }
        valid.sort(Comparator.comparing(DailyBar::date));
        return new ValidationReport(fetched, valid, violations, null);
    }

    private static String rejectionReason(StockRef stock, MarketDataInterval interval, HistoricalPrices response) {
        if (response.interval() != null && response.interval() != interval) {
            return "The provider returned %s bars instead of %s".formatted(response.interval(), interval);
        }
        if (response.symbol() != null && !normalizeSymbol(response.symbol()).equals(normalizeSymbol(stock.symbol()))) {
            return "The provider returned data for '%s' instead of '%s'".formatted(response.symbol(), stock.symbol());
        }
        if (response.currency() != null && !response.currency().strip().equalsIgnoreCase(stock.currency())) {
            return "The provider returned prices in %s, but %s is quoted in %s"
                    .formatted(response.currency(), stock.symbol(), stock.currency());
        }
        Map<LocalDate, Long> perDate = new HashMap<>();
        response.prices().stream().map(HistoricalPrice::date).filter(Objects::nonNull)
                .forEach(date -> perDate.merge(date, 1L, Long::sum));
        List<LocalDate> duplicates = perDate.entrySet().stream().filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey).sorted().limit(REPORTED_VIOLATIONS).toList();
        if (!duplicates.isEmpty()) {
            return "The provider returned duplicate bars for " + duplicates.stream().map(LocalDate::toString)
                    .collect(Collectors.joining(", "));
        }
        return null;
    }

    private static String problemWith(HistoricalPrice price, LocalDate from, LocalDate to, LocalDate today) {
        if (price.date() == null) {
            return "missing date";
        }
        if (price.date().isBefore(from) || price.date().isAfter(to)) {
            return "outside the requested range";
        }
        if (price.date().isAfter(today)) {
            return "dated in the future";
        }
        BigDecimal[] values = {price.open(), price.high(), price.low(), price.close(), price.adjustedClose()};
        for (BigDecimal value : values) {
            if (value == null) {
                return "missing price";
            }
            if (value.signum() <= 0) {
                return "non-positive price";
            }
            if (value.compareTo(MAX_PLAUSIBLE_PRICE) > 0) {
                return "implausible price";
            }
        }
        if (price.high().compareTo(price.low()) < 0) {
            return "high is below low";
        }
        if (price.high().compareTo(price.open()) < 0 || price.high().compareTo(price.close()) < 0) {
            return "high is below open or close";
        }
        if (price.low().compareTo(price.open()) > 0 || price.low().compareTo(price.close()) > 0) {
            return "low is above open or close";
        }
        if (price.volume() == null) {
            return "missing volume";
        }
        if (price.volume() < 0) {
            return "negative volume";
        }
        return null;
    }

    private static DailyBar toBar(HistoricalPrice price) {
        return new DailyBar(price.date(), scale(price.open()), scale(price.high()), scale(price.low()),
                scale(price.close()), scale(price.adjustedClose()), price.volume());
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(STORED_SCALE, RoundingMode.HALF_UP);
    }

    private static String normalizeSymbol(String symbol) {
        return symbol.strip().toUpperCase(Locale.ROOT);
    }

    public record Violation(LocalDate date, String reason) {
    }

    /**
     * @param fetched         bars in the provider response
     * @param validBars       bars that passed every check, oldest first
     * @param violations      rejected bars
     * @param rejectionReason set when the whole response must be discarded
     */
    public record ValidationReport(int fetched, List<DailyBar> validBars, List<Violation> violations,
                                   String rejectionReason) {

        public boolean isRejected() {
            return rejectionReason != null;
        }

        public boolean hasViolations() {
            return !violations.isEmpty();
        }

        /** Client-safe description of the rejected bars. */
        public String violationSummary() {
            String examples = violations.stream().limit(REPORTED_VIOLATIONS)
                    .map(violation -> (violation.date() == null ? "undated bar" : violation.date().toString())
                            + ": " + violation.reason())
                    .collect(Collectors.joining("; "));
            return "%d of %d bars failed validation (%s%s)".formatted(violations.size(), fetched, examples,
                    violations.size() > REPORTED_VIOLATIONS ? "; …" : "");
        }
    }
}
