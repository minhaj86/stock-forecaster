package com.stockforecast.identity.api;

import com.stockforecast.identity.AuthenticatedUser;
import com.stockforecast.identity.Role;
import com.stockforecast.identity.application.AuthenticationResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Access token for the {@code Authorization: Bearer} header. The refresh token is set as an HttpOnly cookie
 * and intentionally absent from the body.
 */
@Schema(name = "AuthResponse")
record AuthResponse(String accessToken, String tokenType, long expiresIn, Instant expiresAt, UserSummary user) {

    static AuthResponse from(AuthenticationResult result, Instant now) {
        Instant expiresAt = result.accessToken().expiresAt();
        Duration remaining = Duration.between(now, expiresAt);
        long expiresIn = remaining.isNegative() ? 0 : remaining.plusMillis(999).toSeconds();
        return new AuthResponse(result.accessToken().value(), "Bearer", expiresIn, expiresAt,
                UserSummary.from(result.user()));
    }

    @Override
    public String toString() {
        return "AuthResponse[accessToken=***, expiresAt=" + expiresAt + ", user=" + user + "]";
    }

    record UserSummary(UUID id, String email, Role role) {

        static UserSummary from(AuthenticatedUser user) {
            return new UserSummary(user.id(), user.email(), user.role());
        }
    }
}
