package com.stockforecast.identity.domain;

import com.stockforecast.shared.domain.DomainException;
import com.stockforecast.shared.domain.ErrorCategory;

public class EmailAlreadyRegisteredException extends DomainException {

    public EmailAlreadyRegisteredException() {
        super("EMAIL_ALREADY_REGISTERED", ErrorCategory.CONFLICT, "An account with this e-mail address already exists");
    }
}
