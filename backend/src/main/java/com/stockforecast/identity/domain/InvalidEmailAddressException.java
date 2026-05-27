package com.stockforecast.identity.domain;

import com.stockforecast.shared.domain.DomainException;
import com.stockforecast.shared.domain.ErrorCategory;

public class InvalidEmailAddressException extends DomainException {

    public InvalidEmailAddressException() {
        super("INVALID_EMAIL", ErrorCategory.VALIDATION, "E-mail address is not valid");
    }
}
