package com.stockforecast.identity.application;

import com.stockforecast.identity.domain.AccountAdministrationExceptions.InvalidCurrentPasswordException;
import com.stockforecast.identity.domain.AccountAdministrationExceptions.PasswordUnchangedException;
import com.stockforecast.identity.domain.InvalidRefreshTokenException;
import com.stockforecast.identity.domain.PasswordPolicy;
import com.stockforecast.identity.domain.RefreshToken;
import com.stockforecast.identity.domain.RefreshTokenRepository;
import com.stockforecast.identity.domain.RevocationReason;
import com.stockforecast.identity.domain.UserAccount;
import com.stockforecast.identity.domain.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Self-service password change, also used to replace a temporary password set by an administrator.
 *
 * <p>The caller is identified by its refresh-token session (the auth endpoints never accept bearer tokens, and a
 * session whose password must change only holds a restricted access token) and re-authenticates with the current
 * password. Every session of the user ends and a fresh one is started for the caller.
 */
@Service
public class PasswordChangeService {

    private final RefreshTokenRepository refreshTokens;
    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final UserSessionService sessions;
    private final OpaqueTokenGenerator tokenGenerator;
    private final Clock clock;

    public PasswordChangeService(RefreshTokenRepository refreshTokens, UserAccountRepository accounts,
                                 PasswordEncoder passwordEncoder, UserSessionService sessions,
                                 OpaqueTokenGenerator tokenGenerator, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.tokenGenerator = tokenGenerator;
        this.clock = clock;
    }

    @Transactional
    public AuthenticationResult changePassword(String rawRefreshToken, String currentPassword, String newPassword) {
        Instant now = clock.instant();
        RefreshToken session = (rawRefreshToken == null || rawRefreshToken.isBlank() ? null
                : refreshTokens.findByTokenHashForUpdate(tokenGenerator.hash(rawRefreshToken)).orElse(null));
        if (session == null || !session.isActive(now)) {
            throw new InvalidRefreshTokenException();
        }
        UserAccount account = accounts.findById(session.getUserId()).filter(UserAccount::isActive)
                .orElseThrow(InvalidRefreshTokenException::new);

        String current = currentPassword == null ? "" : currentPassword;
        if (PasswordPolicy.DEFAULT.exceedsMaximumLength(current)
                || !passwordEncoder.matches(current, account.getPasswordHash())) {
            throw new InvalidCurrentPasswordException();
        }
        PasswordPolicy.DEFAULT.validate(newPassword);
        if (passwordEncoder.matches(newPassword, account.getPasswordHash())) {
            throw new PasswordUnchangedException();
        }

        account.changePassword(passwordEncoder.encode(newPassword), now);
        accounts.save(account);
        sessions.endAllSessions(account.getId(), RevocationReason.PASSWORD_CHANGED);
        return sessions.start(account, session.isPersistent());
    }
}
