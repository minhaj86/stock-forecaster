package com.stockforecast.identity.application;

import java.time.Instant;
import java.util.Objects;

public record IssuedAccessToken(String value, Instant expiresAt) {

    public IssuedAccessToken {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    @Override
    public String toString() {
        return "IssuedAccessToken[value=***, expiresAt=" + expiresAt + "]";
    }
}
