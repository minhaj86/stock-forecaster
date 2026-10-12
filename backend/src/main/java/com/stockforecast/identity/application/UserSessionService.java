package com.stockforecast.identity.application;

import com.stockforecast.identity.domain.InvalidRefreshTokenException;
import com.stockforecast.identity.domain.RefreshToken;
import com.stockforecast.identity.domain.RefreshTokenRepository;
import com.stockforecast.identity.domain.RevocationReason;
import com.stockforecast.identity.domain.UserAccount;
import com.stockforecast.identity.domain.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Lifecycle of login sessions: start (after login/registration), refresh with rotation and replay detection,
 * and end (logout).
 */
@Service
public class UserSessionService {

    private static final Logger log = LoggerFactory.getLogger(UserSessionService.class);

    private final RefreshTokenRepository refreshTokens;
    private final UserAccountRepository accounts;
    private final AccessTokenIssuer accessTokenIssuer;
    private final OpaqueTokenGenerator tokenGenerator;
    private final SessionProperties properties;
    private final Clock clock;

    public UserSessionService(RefreshTokenRepository refreshTokens, UserAccountRepository accounts,
                              AccessTokenIssuer accessTokenIssuer, OpaqueTokenGenerator tokenGenerator,
                              SessionProperties properties, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.accounts = accounts;
        this.accessTokenIssuer = accessTokenIssuer;
        this.tokenGenerator = tokenGenerator;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Starts a new session for an already authenticated account, inside the caller's transaction.
     *
     * @param persistent "remember me": the client may keep the refresh token beyond the browser session
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AuthenticationResult start(UserAccount account, boolean persistent) {
        Instant now = clock.instant();
        String rawToken = tokenGenerator.generate();
        RefreshToken token = refreshTokens.save(RefreshToken.startSession(account.getId(),
                tokenGenerator.hash(rawToken), now, properties.refreshTokenTtl(), properties.maxLifetime(),
                persistent));
        return authenticated(account, rawToken, token, now);
    }

    /**
     * Exchanges a refresh token for a new access token and a new refresh token.
     *
     * <p>Revocations performed while rejecting a token must survive the rejection, hence no rollback for
     * {@link InvalidRefreshTokenException}.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthenticationResult refresh(String rawRefreshToken) {
        RefreshToken current = lockToken(rawRefreshToken).orElseThrow(InvalidRefreshTokenException::new);
        Instant now = clock.instant();

        if (current.isRevoked()) {
            handleRevokedTokenPresented(current, now);
            throw new InvalidRefreshTokenException();
        }
        if (!current.isActive(now)) {
            throw new InvalidRefreshTokenException();
        }

        Optional<UserAccount> account = accounts.findById(current.getUserId()).filter(UserAccount::isActive);
        if (account.isEmpty()) {
            refreshTokens.revokeFamily(current.getFamilyId(), RevocationReason.ACCOUNT_DISABLED, now);
            throw new InvalidRefreshTokenException();
        }

        String rawSuccessor = tokenGenerator.generate();
        RefreshToken successor = current.rotate(tokenGenerator.hash(rawSuccessor), now, properties.refreshTokenTtl());
        refreshTokens.save(current);
        refreshTokens.save(successor);
        return authenticated(account.get(), rawSuccessor, successor, now);
    }

    /**
     * Ends the session the token belongs to. Idempotent and silent for unknown tokens.
     */
    @Transactional
    public void end(String rawRefreshToken) {
        lockToken(rawRefreshToken).ifPresent(token ->
                refreshTokens.revokeFamily(token.getFamilyId(), RevocationReason.LOGOUT, clock.instant()));
    }

    private void handleRevokedTokenPresented(RefreshToken token, Instant now) {
        if (token.wasRotatedWithin(properties.reuseGracePeriod(), now)) {
            log.debug("Rotated refresh token replayed within grace period (family {})", token.getFamilyId());
            return;
        }
        if (token.getRevocationReason() == RevocationReason.ROTATED) {
            int revoked = refreshTokens.revokeFamily(token.getFamilyId(), RevocationReason.REUSE_DETECTED, now);
            log.warn("Refresh token replay detected for user {}; revoked session {} ({} active tokens)",
                    token.getUserId(), token.getFamilyId(), revoked);
        }
    }

    private Optional<RefreshToken> lockToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return Optional.empty();
        }
        return refreshTokens.findByTokenHashForUpdate(tokenGenerator.hash(rawRefreshToken));
    }

    /**
     * Signs the user out everywhere, e.g. after a password change or an administrative action.
     *
     * @return number of refresh tokens revoked
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int endAllSessions(UUID userId, RevocationReason reason) {
        return refreshTokens.revokeAllOfUser(userId, reason, clock.instant());
    }

    private AuthenticationResult authenticated(UserAccount account, String rawRefreshToken, RefreshToken token,
                                               Instant now) {
        var user = account.toAuthenticatedUser();
        boolean passwordChangeRequired = account.isPasswordChangeRequired();
        return new AuthenticationResult(user, accessTokenIssuer.issue(user, passwordChangeRequired, now),
                new IssuedRefreshToken(rawRefreshToken, token.getExpiresAt(), token.isPersistent()),
                passwordChangeRequired);
    }
}
