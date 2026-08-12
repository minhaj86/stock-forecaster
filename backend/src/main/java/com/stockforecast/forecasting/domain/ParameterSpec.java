package com.stockforecast.forecasting.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Describes one tunable model setting so clients can render a control and requests can be validated uniformly.
 *
 * @param defaultValue canonical default, or {@code null} when the value is estimated from the data unless supplied
 * @param minimum      inclusive lower bound for numeric kinds
 * @param maximum      inclusive upper bound for numeric kinds
 * @param options      allowed values for {@link Kind#CHOICE}
 */
public record ParameterSpec(String name, String label, Kind kind, String defaultValue, Double minimum, Double maximum,
                            List<String> options, String description) {

    public enum Kind {
        INTEGER,
        DECIMAL,
        CHOICE
    }

    public ParameterSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        options = options == null ? List.of() : List.copyOf(options);
    }

    public static ParameterSpec integer(String name, String label, int defaultValue, int minimum, int maximum,
                                        String description) {
        return new ParameterSpec(name, label, Kind.INTEGER, Integer.toString(defaultValue), (double) minimum,
                (double) maximum, List.of(), description);
    }

    /** A decimal setting that is estimated from the data when not supplied. */
    public static ParameterSpec optionalDecimal(String name, String label, double minimum, double maximum,
                                                String description) {
        return new ParameterSpec(name, label, Kind.DECIMAL, null, minimum, maximum, List.of(), description);
    }

    public static ParameterSpec choice(String name, String label, String defaultValue, List<String> options,
                                       String description) {
        return new ParameterSpec(name, label, Kind.CHOICE, defaultValue, null, null, options, description);
    }

    /**
     * Validates a raw value and returns its canonical form ({@code "020"} becomes {@code "20"}, choices upper case).
     */
    String normalise(String raw) {
        String value = raw == null ? "" : raw.trim();
        return switch (kind) {
            case INTEGER -> {
                int parsed;
                try {
                    parsed = Integer.parseInt(value);
                } catch (NumberFormatException ex) {
                    throw new InvalidForecastParameterException(name, "must be a whole number");
                }
                requireInRange(parsed);
                yield Integer.toString(parsed);
            }
            case DECIMAL -> {
                double parsed;
                try {
                    parsed = Double.parseDouble(value);
                } catch (NumberFormatException ex) {
                    throw new InvalidForecastParameterException(name, "must be a number");
                }
                if (!Double.isFinite(parsed)) {
                    throw new InvalidForecastParameterException(name, "must be a finite number");
                }
                requireInRange(parsed);
                yield BigDecimal.valueOf(parsed).stripTrailingZeros().toPlainString();
            }
            case CHOICE -> {
                String upper = value.toUpperCase(Locale.ROOT);
                if (!options.contains(upper)) {
                    throw new InvalidForecastParameterException(name, "must be one of " + String.join(", ", options));
                }
                yield upper;
            }
        };
    }

    private void requireInRange(double value) {
        if ((minimum != null && value < minimum) || (maximum != null && value > maximum)) {
            throw new InvalidForecastParameterException(name, "must be between %s and %s"
                    .formatted(format(minimum), format(maximum)));
        }
    }

    private static String format(Double bound) {
        return BigDecimal.valueOf(bound).stripTrailingZeros().toPlainString();
    }
}
