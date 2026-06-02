package com.stockforecast.identity.application;

import com.stockforecast.identity.Role;
import com.stockforecast.identity.UserRegisteredEvent;
import com.stockforecast.identity.domain.EmailAddress;
import com.stockforecast.identity.domain.EmailAlreadyRegisteredException;
import com.stockforecast.identity.domain.PasswordPolicy;
import com.stockforecast.identity.domain.UserAccount;
import com.stockforecast.identity.domain.UserAccountRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Self-service sign-up. A successful registration also signs the user in.
 */
@Service
public class RegistrationService {

    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final UserSessionService sessions;
    private final Clock clock;

    public RegistrationService(UserAccountRepository accounts, PasswordEncoder passwordEncoder,
                               ApplicationEventPublisher events, UserSessionService sessions, Clock clock) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.sessions = sessions;
        this.clock = clock;
    }

    /**
     * Registers and signs in with a persistent session: a user who just created an account on this device expects to
     * stay signed in.
     */
    @Transactional
    public AuthenticationResult register(RegisterUserCommand command) {
        UserAccount account = createAccount(EmailAddress.of(command.email()), command.password(),
                command.displayName(), Role.USER);
        return sessions.start(account, true);
    }

    /**
     * Creates an account with the given role. Used by self-service registration and by operational
     * bootstrapping of the first administrator.
     */
    @Transactional
    public UserAccount createAccount(EmailAddress email, String rawPassword, String displayName, Role role) {
        PasswordPolicy.DEFAULT.validate(rawPassword);
        if (accounts.existsByEmail(email.value())) {
            throw new EmailAlreadyRegisteredException();
        }
        Instant now = clock.instant();
        UserAccount account = accounts.save(UserAccount.register(email, passwordEncoder.encode(rawPassword), role, now));
        events.publishEvent(new UserRegisteredEvent(account.getId(), account.getEmail(), displayName, now));
        return account;
    }
}
