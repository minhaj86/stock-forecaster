package com.stockforecast.identity.application;

import com.stockforecast.identity.AuthenticatedUser;

import java.time.Instant;

/**
 * Port for minting short-lived access tokens; the JWT implementation lives in infrastructure.
 */
public interface AccessTokenIssuer {

    IssuedAccessToken issue(AuthenticatedUser user, Instant issuedAt);
}
