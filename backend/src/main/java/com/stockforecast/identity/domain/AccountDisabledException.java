package com.stockforecast.identity.domain;

import com.stockforecast.shared.domain.DomainException;
import com.stockforecast.shared.domain.ErrorCategory;

public class AccountDisabledException extends DomainException {

    public AccountDisabledException() {
        super("ACCOUNT_DISABLED", ErrorCategory.FORBIDDEN, "This account has been disabled");
    }
}
