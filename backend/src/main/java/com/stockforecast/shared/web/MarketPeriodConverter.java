package com.stockforecast.shared.web;

import com.stockforecast.shared.domain.MarketPeriod;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Binds request parameters such as {@code period=1Y} or {@code period=ytd}; unknown codes become a 400.
 */
@Component
public class MarketPeriodConverter implements Converter<String, MarketPeriod> {

    @Override
    public MarketPeriod convert(String source) {
        return MarketPeriod.fromCode(source)
                .orElseThrow(() -> new IllegalArgumentException("Unknown period: " + source));
    }
}
