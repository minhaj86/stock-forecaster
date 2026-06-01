package com.stockforecast.identity.application;

import com.stockforecast.identity.AuthenticatedUser;

public record AuthenticationResult(AuthenticatedUser user, IssuedAccessToken accessToken,
                                   IssuedRefreshToken refreshToken) {
}
