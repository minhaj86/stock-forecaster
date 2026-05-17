package com.stockforecast.shared.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Base type for expected business failures.
 *
 * <p>Contract: {@link #getMessage()} must be safe to show to API clients (no internals, secrets or stack
 * details) and {@link #code()} must be a stable, machine-readable identifier such as
 * {@code EMAIL_ALREADY_REGISTERED}. Optional {@link #details()} carry structured, client-safe values (e.g. required
 * versus available history) so clients can render precise guidance without parsing the message.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;
    private final ErrorCategory category;
    private final Map<String, Object> details;

    protected DomainException(String code, ErrorCategory category, String message) {
        this(code, category, message, Map.of());
    }

    /**
     * @param details client-safe structured values; iteration order is preserved in responses
     */
    protected DomainException(String code, ErrorCategory category, String message, Map<String, ?> details) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
        this.category = Objects.requireNonNull(category, "category");
        this.details = Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public String code() {
        return code;
    }

    public ErrorCategory category() {
        return category;
    }

    public Map<String, Object> details() {
        return details;
    }
}
