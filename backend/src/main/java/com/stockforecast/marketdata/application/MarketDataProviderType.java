package com.stockforecast.marketdata.application;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Provider adapters this application ships. {@code MARKET_DATA_PROVIDER} accepts the id or the constant name in any
 * case ({@code twelve-data}, {@code TWELVE_DATA}).
 */
public enum MarketDataProviderType {
    SYNTHETIC("synthetic", "Synthetic demo data"),
    TWELVE_DATA("twelve-data", "Twelve Data");

    private final String id;
    private final String displayName;

    MarketDataProviderType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<MarketDataProviderType> fromConfiguration(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT).replace('_', '-');
        return Arrays.stream(values()).filter(type -> type.id.equals(normalized)).findFirst();
    }

    public static String supportedValues() {
        return Arrays.stream(values()).map(type -> type.name() + " (" + type.id + ")")
                .collect(Collectors.joining(", "));
    }
}
