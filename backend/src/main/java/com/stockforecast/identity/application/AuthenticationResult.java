package com.stockforecast.identity.application;

import com.stockforecast.identity.AuthenticatedUser;

/**
 * @param passwordChangeRequired the user must choose a new password before using the application
 */
public record AuthenticationResult(AuthenticatedUser user, IssuedAccessToken accessToken,
                                   IssuedRefreshToken refreshToken, boolean passwordChangeRequired) {
}
