package com.stockforecast.user.domain;

import com.stockforecast.shared.domain.DomainException;
import com.stockforecast.shared.domain.ErrorCategory;

public class InvalidProfileException extends DomainException {

    public InvalidProfileException(String code, String message) {
        super(code, ErrorCategory.VALIDATION, message);
    }
}
