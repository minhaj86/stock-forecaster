package com.stockforecast.user.domain;

import com.stockforecast.shared.domain.DomainException;
import com.stockforecast.shared.domain.ErrorCategory;

public class UserProfileNotFoundException extends DomainException {

    public UserProfileNotFoundException() {
        super("PROFILE_NOT_FOUND", ErrorCategory.NOT_FOUND, "User profile not found");
    }
}
