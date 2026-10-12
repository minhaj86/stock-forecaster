package com.stockforecast.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Administrative view of an account. Never contains credentials.
 */
public record ManagedAccount(UUID id, String email, Role role, AccountStatus status, boolean passwordChangeRequired,
                             Instant createdAt, Instant updatedAt, Instant lastLoginAt) {

    public boolean isActiveAdministrator() {
        return role == Role.ADMIN && status == AccountStatus.ACTIVE;
    }
}
