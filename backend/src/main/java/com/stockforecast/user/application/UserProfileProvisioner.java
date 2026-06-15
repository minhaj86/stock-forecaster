package com.stockforecast.user.application;

import com.stockforecast.identity.UserRegisteredEvent;
import com.stockforecast.user.domain.UserProfile;
import com.stockforecast.user.domain.UserProfileRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Creates the profile for a newly registered account.
 *
 * <p>Trade-off: a synchronous listener runs inside the registration transaction, so the account and its profile
 * are created atomically and the profile is readable immediately after sign-up. The cost is temporal coupling;
 * truly independent reactions (welcome e-mails, analytics) should use asynchronous, after-commit listeners.
 */
@Component
class UserProfileProvisioner {

    private final UserProfileRepository profiles;

    UserProfileProvisioner(UserProfileRepository profiles) {
        this.profiles = profiles;
    }

    @EventListener
    void on(UserRegisteredEvent event) {
        if (!profiles.existsById(event.userId())) {
            profiles.save(UserProfile.create(event.userId(), event.displayName(), event.registeredAt()));
        }
    }
}
