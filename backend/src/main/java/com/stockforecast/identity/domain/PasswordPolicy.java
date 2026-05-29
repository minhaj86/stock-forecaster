package com.stockforecast.identity.domain;

import java.nio.charset.StandardCharsets;

/**
 * Password rules applied before hashing.
 *
 * <p>The 72-byte ceiling is a BCrypt property: longer inputs are silently truncated when matching (and rejected
 * when hashing), so allowing them would give users a false sense of security.
 */
public record PasswordPolicy(int minLength, int maxBytes) {

    public static final PasswordPolicy DEFAULT = new PasswordPolicy(12, 72);

    public void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank() || rawPassword.codePointCount(0, rawPassword.length()) < minLength
                || exceedsMaximumLength(rawPassword)) {
            throw new PasswordPolicyViolationException(
                    "Password must be at least %d characters and at most %d bytes long".formatted(minLength, maxBytes));
        }
    }

    public boolean exceedsMaximumLength(String rawPassword) {
        return rawPassword.getBytes(StandardCharsets.UTF_8).length > maxBytes;
    }
}
