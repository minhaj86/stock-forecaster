package com.stockforecast.identity;

import java.util.Objects;
import java.util.UUID;

/**
 * The authenticated principal, derived from a validated access token. Other modules use {@link #id()} as the
 * owner key for user-scoped data.
 */
public record AuthenticatedUser(UUID id, String email, Role role) {

    public AuthenticatedUser {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(role, "role");
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
