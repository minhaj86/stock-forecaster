package com.stockforecast.forecasting.domain;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Immutable, name-sorted model settings as strings (as they arrive from a request). {@link #resolve} validates them
 * against a model's {@link ParameterSpec}s and fills in defaults, producing the canonical form used for caching and
 * echoed back to clients.
 */
public record ModelParameters(Map<String, String> values) {

    private static final ModelParameters NONE = new ModelParameters(Map.of());

    public ModelParameters {
        values = Collections.unmodifiableMap(new TreeMap<>(values == null ? Map.of() : values));
    }

    public static ModelParameters none() {
        return NONE;
    }

    public static ModelParameters of(Map<String, String> values) {
        return new ModelParameters(values);
    }

    public Optional<String> get(String name) {
        return Optional.ofNullable(values.get(name));
    }

    public int integer(String name) {
        return Integer.parseInt(require(name));
    }

    public OptionalDouble decimal(String name) {
        String value = values.get(name);
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(Double.parseDouble(value));
    }

    public String choice(String name) {
        return require(name);
    }

    /** Stable textual form, e.g. {@code lookback=252;window=20}. */
    public String canonical() {
        return values.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(";"));
    }

    /**
     * Validates every supplied parameter against {@code specs} (unknown names are rejected) and adds defaults.
     *
     * @throws InvalidForecastParameterException if a parameter is unknown, malformed or out of range
     */
    public ModelParameters resolve(ForecastModelType model, List<ParameterSpec> specs) {
        Map<String, ParameterSpec> byName = specs.stream()
                .collect(Collectors.toMap(ParameterSpec::name, spec -> spec));
        Map<String, String> resolved = new TreeMap<>();
        values.forEach((name, raw) -> {
            ParameterSpec spec = byName.get(name);
            if (spec == null) {
                throw new InvalidForecastParameterException(name,
                        "is not supported by the %s model".formatted(model.name()));
            }
            resolved.put(name, spec.normalise(raw));
        });
        for (ParameterSpec spec : specs) {
            if (!resolved.containsKey(spec.name()) && spec.defaultValue() != null) {
                resolved.put(spec.name(), spec.defaultValue());
            }
        }
        return new ModelParameters(resolved);
    }

    private String require(String name) {
        String value = values.get(name);
        if (value == null) {
            throw new IllegalStateException("parameter '" + name + "' has not been resolved");
        }
        return value;
    }
}
