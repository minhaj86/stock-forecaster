package com.stockforecast.identity.domain;

import com.stockforecast.shared.domain.DomainException;
import com.stockforecast.shared.domain.ErrorCategory;

public class PasswordPolicyViolationException extends DomainException {

    public PasswordPolicyViolationException(String message) {
        super("PASSWORD_POLICY_VIOLATION", ErrorCategory.VALIDATION, message);
    }
}
