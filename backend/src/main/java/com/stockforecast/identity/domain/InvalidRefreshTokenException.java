package com.stockforecast.identity.domain;

import com.stockforecast.shared.domain.DomainException;
import com.stockforecast.shared.domain.ErrorCategory;

/**
 * Raised for missing, unknown, expired, revoked or replayed refresh tokens. One code for all cases so the
 * response cannot be used to probe token state.
 */
public class InvalidRefreshTokenException extends DomainException {

    public InvalidRefreshTokenException() {
        super("INVALID_REFRESH_TOKEN", ErrorCategory.UNAUTHENTICATED,
                "The session is invalid or has expired; please sign in again");
    }
}
