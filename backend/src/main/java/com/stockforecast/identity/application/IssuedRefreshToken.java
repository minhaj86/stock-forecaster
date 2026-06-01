package com.stockforecast.identity.application;

import java.time.Instant;
import java.util.Objects;

/**
 * The raw (unhashed) refresh token. It exists only in memory on its way to the client cookie.
 *
 * @param persistent whether the cookie may outlive the browser session ("remember me")
 */
public record IssuedRefreshToken(String value, Instant expiresAt, boolean persistent) {

    public IssuedRefreshToken {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    @Override
    public String toString() {
        return "IssuedRefreshToken[value=***, expiresAt=" + expiresAt + ", persistent=" + persistent + "]";
    }
}
