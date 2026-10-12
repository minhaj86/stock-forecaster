package com.stockforecast.user.application;

import com.stockforecast.identity.UserAccountDeleted;
import com.stockforecast.user.domain.UserProfileRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Deletes the profile inside the account-deletion transaction (explicit, instead of relying on the cascading foreign
 * key, which stays as a safety net).
 */
@Component
class UserProfileEraser {

    private final UserProfileRepository profiles;

    UserProfileEraser(UserProfileRepository profiles) {
        this.profiles = profiles;
    }

    @EventListener
    void on(UserAccountDeleted event) {
        profiles.findById(event.userId()).ifPresent(profiles::delete);
    }
}
