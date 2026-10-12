package com.stockforecast.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Published synchronously inside the deletion transaction, <em>before</em> the account row is removed. Modules that
 * own user data delete it in a synchronous listener, so the account and everything that belongs to it disappear
 * atomically (or not at all). Database cascades remain only as a safety net.
 */
public record UserAccountDeleted(UUID userId, String email, Instant deletedAt) {
}
