package com.stockforecast.identity.application;

import com.stockforecast.identity.Role;
import com.stockforecast.identity.domain.EmailAddress;
import com.stockforecast.identity.domain.InvalidRefreshTokenException;
import com.stockforecast.identity.domain.RefreshToken;
import com.stockforecast.identity.domain.RefreshTokenRepository;
import com.stockforecast.identity.domain.RevocationReason;
import com.stockforecast.identity.domain.UserAccount;
import com.stockforecast.identity.domain.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    private static final SessionProperties PROPERTIES =
            new SessionProperties(Duration.ofDays(7), Duration.ofDays(30), Duration.ofSeconds(10));

    @Mock
    private RefreshTokenRepository refreshTokens;
    @Mock
    private UserAccountRepository accounts;
    @Mock
    private AccessTokenIssuer accessTokenIssuer;

    private final OpaqueTokenGenerator tokenGenerator = new OpaqueTokenGenerator();
    private final UserAccount account =
            UserAccount.register(EmailAddress.of("ada@example.com"), "{bcrypt}hash", Role.USER, NOW);

    @BeforeEach
    void stubCollaborators() {
        lenient().when(refreshTokens.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(accessTokenIssuer.issue(any(), anyBoolean(), any()))
                .thenAnswer(invocation -> new IssuedAccessToken("access-token",
                        invocation.<Instant>getArgument(2).plus(Duration.ofMinutes(15))));
    }

    @Test
    void startCreatesANewSessionWithHashedTokenStorage() {
        AuthenticationResult result = serviceAt(NOW).start(account, true);

        assertThat(result.user().id()).isEqualTo(account.getId());
        assertThat(result.accessToken().value()).isEqualTo("access-token");
        assertThat(result.refreshToken().expiresAt()).isEqualTo(NOW.plus(PROPERTIES.refreshTokenTtl()));
        verify(refreshTokens).save(argThat(token ->
                token.getTokenHash().equals(tokenGenerator.hash(result.refreshToken().value()))
                        && !token.getTokenHash().equals(result.refreshToken().value())));
    }

    @Test
    void refreshRotatesTheTokenAndIssuesNewCredentials() {
        String raw = tokenGenerator.generate();
        RefreshToken current = existingToken(raw, NOW);
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
        Instant later = NOW.plus(Duration.ofHours(1));

        AuthenticationResult result = serviceAt(later).refresh(raw);

        assertThat(current.getRevocationReason()).isEqualTo(RevocationReason.ROTATED);
        assertThat(result.refreshToken().value()).isNotEqualTo(raw);
        assertThat(result.refreshToken().expiresAt()).isEqualTo(later.plus(PROPERTIES.refreshTokenTtl()));
        assertThat(result.accessToken().expiresAt()).isEqualTo(later.plus(Duration.ofMinutes(15)));
    }

    @Test
    void rememberMeChoiceIsKeptAcrossRotations() {
        AuthenticationResult started = serviceAt(NOW).start(account, false);
        assertThat(started.refreshToken().persistent()).isFalse();

        String raw = tokenGenerator.generate();
        RefreshToken sessionOnly = RefreshToken.startSession(account.getId(), tokenGenerator.hash(raw), NOW,
                PROPERTIES.refreshTokenTtl(), PROPERTIES.maxLifetime(), false);
        when(refreshTokens.findByTokenHashForUpdate(tokenGenerator.hash(raw))).thenReturn(Optional.of(sessionOnly));
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));

        AuthenticationResult refreshed = serviceAt(NOW.plusSeconds(60)).refresh(raw);

        assertThat(refreshed.refreshToken().persistent()).isFalse();
    }

    @Test
    void refreshRejectsUnknownAndBlankTokens() {
        when(refreshTokens.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceAt(NOW).refresh("unknown")).isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> serviceAt(NOW).refresh(" ")).isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> serviceAt(NOW).refresh(null)).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refreshRejectsExpiredTokensWithoutRevokingTheSession() {
        String raw = tokenGenerator.generate();
        existingToken(raw, NOW);

        assertThatThrownBy(() -> serviceAt(NOW.plus(Duration.ofDays(8))).refresh(raw))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokens, never()).revokeFamily(any(), any(), any());
    }

    @Test
    void replayOfARotatedTokenAfterTheGracePeriodRevokesTheWholeSession() {
        String raw = tokenGenerator.generate();
        RefreshToken current = existingToken(raw, NOW);
        current.rotate(tokenGenerator.hash("successor"), NOW, PROPERTIES.refreshTokenTtl());

        assertThatThrownBy(() -> serviceAt(NOW.plusSeconds(11)).refresh(raw))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokens).revokeFamily(current.getFamilyId(), RevocationReason.REUSE_DETECTED, NOW.plusSeconds(11));
    }

    @Test
    void replayWithinTheGracePeriodIsRejectedWithoutRevokingTheSession() {
        String raw = tokenGenerator.generate();
        RefreshToken current = existingToken(raw, NOW);
        current.rotate(tokenGenerator.hash("successor"), NOW, PROPERTIES.refreshTokenTtl());

        assertThatThrownBy(() -> serviceAt(NOW.plusSeconds(3)).refresh(raw))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokens, never()).revokeFamily(any(), any(), any());
    }

    @Test
    void refreshForADisabledAccountRevokesTheSession() {
        String raw = tokenGenerator.generate();
        RefreshToken current = existingToken(raw, NOW);
        account.disable(NOW);
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> serviceAt(NOW).refresh(raw)).isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokens).revokeFamily(current.getFamilyId(), RevocationReason.ACCOUNT_DISABLED, NOW);
        verifyNoInteractions(accessTokenIssuer);
    }

    @Test
    void endRevokesTheSessionAndIgnoresUnknownOrMissingTokens() {
        String raw = tokenGenerator.generate();
        RefreshToken current = existingToken(raw, NOW);
        when(refreshTokens.findByTokenHashForUpdate(tokenGenerator.hash("unknown"))).thenReturn(Optional.empty());

        serviceAt(NOW).end(raw);
        serviceAt(NOW).end("unknown");
        serviceAt(NOW).end(null);

        verify(refreshTokens).revokeFamily(current.getFamilyId(), RevocationReason.LOGOUT, NOW);
    }

    private RefreshToken existingToken(String raw, Instant issuedAt) {
        RefreshToken token = RefreshToken.startSession(account.getId(), tokenGenerator.hash(raw), issuedAt,
                PROPERTIES.refreshTokenTtl(), PROPERTIES.maxLifetime(), true);
        when(refreshTokens.findByTokenHashForUpdate(tokenGenerator.hash(raw))).thenReturn(Optional.of(token));
        return token;
    }

    private UserSessionService serviceAt(Instant instant) {
        return new UserSessionService(refreshTokens, accounts, accessTokenIssuer, tokenGenerator, PROPERTIES,
                Clock.fixed(instant, ZoneOffset.UTC));
    }
}
