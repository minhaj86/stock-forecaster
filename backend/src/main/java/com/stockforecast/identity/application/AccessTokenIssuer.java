package com.stockforecast.identity.application;

import com.stockforecast.identity.AuthenticatedUser;

import java.time.Instant;

/**
 * Port for minting short-lived access tokens; the JWT implementation lives in infrastructure.
 */
public interface AccessTokenIssuer {

    /**
     * @param passwordChangeRequired the token only grants access to the password change until the user picks a
     *                               new password
     */
    IssuedAccessToken issue(AuthenticatedUser user, boolean passwordChangeRequired, Instant issuedAt);

    default IssuedAccessToken issue(AuthenticatedUser user, Instant issuedAt) {
        return issue(user, false, issuedAt);
    }
}
