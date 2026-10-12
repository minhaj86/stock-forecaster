package com.stockforecast.identity.application;

import com.stockforecast.identity.domain.AccountDisabledException;
import com.stockforecast.identity.domain.AccountLockedException;
import com.stockforecast.identity.domain.EmailAddress;
import com.stockforecast.identity.domain.InvalidCredentialsException;
import com.stockforecast.identity.domain.PasswordPolicy;
import com.stockforecast.identity.domain.UserAccount;
import com.stockforecast.identity.domain.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Credential verification (login).
 */
@Service
public class AuthenticationService {

    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final UserSessionService sessions;
    private final Clock clock;
    /** Hash of a random secret, verified when the account does not exist so both paths cost the same. */
    private final String timingEqualizationHash;

    public AuthenticationService(UserAccountRepository accounts, PasswordEncoder passwordEncoder,
                                 UserSessionService sessions, Clock clock) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.clock = clock;
        this.timingEqualizationHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthenticationResult login(LoginCommand command) {
        Optional<UserAccount> candidate = EmailAddress.tryParse(command.email())
                .flatMap(email -> accounts.findByEmail(email.value()));
        String password = command.password() == null ? "" : command.password();

        if (candidate.isEmpty() || PasswordPolicy.DEFAULT.exceedsMaximumLength(password)) {
            passwordEncoder.matches(password, timingEqualizationHash);
            throw new InvalidCredentialsException();
        }
        UserAccount account = candidate.get();
        if (!passwordEncoder.matches(password, account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        // Status is only revealed after a correct password, so it cannot be probed for arbitrary e-mails.
        switch (account.getStatus()) {
            case DISABLED -> throw new AccountDisabledException();
            case LOCKED -> throw new AccountLockedException();
            case ACTIVE -> {
                // may sign in
            }
        }
        Instant now = clock.instant();
        if (passwordEncoder.upgradeEncoding(account.getPasswordHash())) {
            account.changePasswordHash(passwordEncoder.encode(password), now);
        }
        account.recordLogin(now);
        return sessions.start(account, command.rememberMe());
    }
}
