package com.stockforecast.stock.domain;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Normalised ticker symbol (upper case, e.g. {@code AAPL}, {@code BRK.B}).
 */
public record StockSymbol(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Z0-9][A-Z0-9.\\-]{0,19}");

    public StockSymbol {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid symbol: " + value);
        }
    }

    /**
     * Lenient parsing of user input; empty if it cannot be a valid symbol.
     */
    public static Optional<StockSymbol> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = raw.strip().toUpperCase(Locale.ROOT);
        return FORMAT.matcher(normalized).matches() ? Optional.of(new StockSymbol(normalized)) : Optional.empty();
    }

    @Override
    public String toString() {
        return value;
    }
}
