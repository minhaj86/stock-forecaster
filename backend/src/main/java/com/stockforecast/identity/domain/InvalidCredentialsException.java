package com.stockforecast.identity.domain;

import com.stockforecast.shared.domain.DomainException;
import com.stockforecast.shared.domain.ErrorCategory;

/**
 * Deliberately generic: never reveals whether the e-mail or the password was wrong.
 */
public class InvalidCredentialsException extends DomainException {

    public InvalidCredentialsException() {
        super("INVALID_CREDENTIALS", ErrorCategory.UNAUTHENTICATED, "Invalid e-mail address or password");
    }
}
