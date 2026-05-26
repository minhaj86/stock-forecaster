package com.stockforecast.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Published synchronously inside the registration transaction, so listeners that create user-owned data
 * (such as the profile) commit or roll back atomically with the account.
 */
public record UserRegisteredEvent(UUID userId, String email, String displayName, Instant registeredAt) {
}
