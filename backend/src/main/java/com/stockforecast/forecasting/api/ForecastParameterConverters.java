package com.stockforecast.forecasting.api;

import com.stockforecast.forecasting.domain.ForecastModelType;
import com.stockforecast.forecasting.domain.ForecastTarget;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Lenient binding of enum query parameters: {@code model=exponential-smoothing} and
 * {@code model=EXPONENTIAL_SMOOTHING} are equivalent. Unknown values become a 400.
 */
final class ForecastParameterConverters {

    private ForecastParameterConverters() {
    }

    static <E extends Enum<E>> E parse(Class<E> type, String source) {
        String normalised = source.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return Enum.valueOf(type, normalised);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown " + type.getSimpleName() + ": " + source, ex);
        }
    }

    @Component
    static class ModelTypeConverter implements Converter<String, ForecastModelType> {

        @Override
        public ForecastModelType convert(String source) {
            return parse(ForecastModelType.class, source);
        }
    }

    @Component
    static class TargetConverter implements Converter<String, ForecastTarget> {

        @Override
        public ForecastTarget convert(String source) {
            return parse(ForecastTarget.class, source);
        }
    }
}
