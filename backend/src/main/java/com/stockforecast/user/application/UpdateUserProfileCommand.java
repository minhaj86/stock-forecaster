package com.stockforecast.user.application;

/**
 * Partial update; {@code null} fields are left unchanged.
 */
public record UpdateUserProfileCommand(String displayName, String timeZone) {
}
