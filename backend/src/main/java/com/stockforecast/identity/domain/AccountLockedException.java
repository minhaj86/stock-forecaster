package com.stockforecast.identity.domain;

import com.stockforecast.shared.domain.DomainException;
import com.stockforecast.shared.domain.ErrorCategory;

public class AccountLockedException extends DomainException {

    public AccountLockedException() {
        super("ACCOUNT_LOCKED", ErrorCategory.FORBIDDEN, "This account is locked. Contact an administrator.");
    }
}
