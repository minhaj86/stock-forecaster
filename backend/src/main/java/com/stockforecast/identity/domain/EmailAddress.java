package com.stockforecast.identity.domain;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Normalised (trimmed, lower-cased) e-mail address. Normalisation guarantees one account per mailbox
 * regardless of the casing a user types; the database enforces the same invariant with a check constraint.
 */
public record EmailAddress(String value) {

    public static final int MAX_LENGTH = 320;

    private static final Pattern FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");

    public EmailAddress {
        String normalized = normalize(value);
        if (!isWellFormed(normalized)) {
            throw new InvalidEmailAddressException();
        }
        value = normalized;
    }

    public static EmailAddress of(String raw) {
        return new EmailAddress(raw);
    }

    /**
     * Lenient parsing for flows where an invalid address must not be distinguishable from an unknown one.
     */
    public static Optional<EmailAddress> tryParse(String raw) {
        String normalized = normalize(raw);
        return isWellFormed(normalized) ? Optional.of(new EmailAddress(normalized)) : Optional.empty();
    }

    private static String normalize(String raw) {
        return raw == null ? null : raw.strip().toLowerCase(Locale.ROOT);
    }

    private static boolean isWellFormed(String normalized) {
        return normalized != null && normalized.length() <= MAX_LENGTH && FORMAT.matcher(normalized).matches();
    }

    @Override
    public String toString() {
        return value;
    }
}
