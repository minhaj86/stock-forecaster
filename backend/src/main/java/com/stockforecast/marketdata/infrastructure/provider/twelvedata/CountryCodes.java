package com.stockforecast.marketdata.infrastructure.provider.twelvedata;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Maps the country names Twelve Data reports ("United States") to ISO 3166 alpha-2 codes ("US").
 */
final class CountryCodes {

    private static final Map<String, String> BY_NAME = new HashMap<>();

    static {
        for (String code : Locale.getISOCountries()) {
            BY_NAME.put(normalize(Locale.of("", code).getDisplayCountry(Locale.ENGLISH)), code);
            BY_NAME.put(normalize(code), code);
        }
        BY_NAME.put("usa", "US");
        BY_NAME.put("united states of america", "US");
        BY_NAME.put("uk", "GB");
        BY_NAME.put("great britain", "GB");
        BY_NAME.put("south korea", "KR");
        BY_NAME.put("korea", "KR");
        BY_NAME.put("russia", "RU");
        BY_NAME.put("taiwan", "TW");
        BY_NAME.put("czech republic", "CZ");
        BY_NAME.put("turkey", "TR");
        BY_NAME.put("vietnam", "VN");
        BY_NAME.put("hong kong", "HK");
    }

    private CountryCodes() {
    }

    static Optional<String> toIsoCode(String countryName) {
        if (countryName == null || countryName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_NAME.get(normalize(countryName)));
    }

    private static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
