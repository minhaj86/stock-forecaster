package com.stockforecast.identity.application;

import com.stockforecast.identity.AccountStatus;
import com.stockforecast.identity.Role;
import com.stockforecast.identity.domain.AccountAdministrationExceptions.InvalidCurrentPasswordException;
import com.stockforecast.identity.domain.AccountAdministrationExceptions.PasswordUnchangedException;
import com.stockforecast.identity.domain.EmailAddress;
import com.stockforecast.identity.domain.InvalidRefreshTokenException;
import com.stockforecast.identity.domain.PasswordPolicyViolationException;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordChangeServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    private static final String TEMPORARY = "Temporary-Password-42";
    private static final String CHOSEN = "my-own-secret-passphrase";

    @Mock
    private RefreshTokenRepository refreshTokens;
    @Mock
    private UserAccountRepository accounts;
    @Mock
    private UserSessionService sessions;

    private final PasswordEncoder passwordEncoder =
            new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(4)));
    private final OpaqueTokenGenerator tokens = new OpaqueTokenGenerator();
    private UserAccount account;
    private RefreshToken session;
    private PasswordChangeService service;

    @BeforeEach
    void setUp() {
        account = UserAccount.provision(EmailAddress.of("ada@example.com"), passwordEncoder.encode(TEMPORARY),
                Role.USER, AccountStatus.ACTIVE, true, NOW);
        session = RefreshToken.startSession(account.getId(), tokens.hash("raw-token"), NOW, Duration.ofDays(7),
                Duration.ofDays(30), false);
        lenient().when(refreshTokens.findByTokenHashForUpdate(tokens.hash("raw-token"))).thenReturn(Optional.of(session));
        lenient().when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
        service = new PasswordChangeService(refreshTokens, accounts, passwordEncoder, sessions, tokens,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void replacesTheTemporaryPasswordEndsOtherSessionsAndStartsANewOne() {
        service.changePassword("raw-token", TEMPORARY, CHOSEN);

        assertThat(account.isPasswordChangeRequired()).isFalse();
        assertThat(passwordEncoder.matches(CHOSEN, account.getPasswordHash())).isTrue();
        verify(sessions).endAllSessions(account.getId(), RevocationReason.PASSWORD_CHANGED);
        verify(sessions).start(account, false);
    }

    @Test
    void requiresTheCurrentPassword() {
        assertThatThrownBy(() -> service.changePassword("raw-token", "not-the-password", CHOSEN))
                .isInstanceOf(InvalidCurrentPasswordException.class);
        verify(sessions, never()).start(any(), anyBoolean());
    }

    @Test
    void rejectsReusingTheSamePasswordOrAWeakOne() {
        assertThatThrownBy(() -> service.changePassword("raw-token", TEMPORARY, TEMPORARY))
                .isInstanceOf(PasswordUnchangedException.class);
        assertThatThrownBy(() -> service.changePassword("raw-token", TEMPORARY, "short"))
                .isInstanceOf(PasswordPolicyViolationException.class);
    }

    @Test
    void requiresAnActiveSessionOfAnActiveAccount() {
        assertThatThrownBy(() -> service.changePassword("unknown-token", TEMPORARY, CHOSEN))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> service.changePassword(null, TEMPORARY, CHOSEN))
                .isInstanceOf(InvalidRefreshTokenException.class);

        account.disable(NOW);
        assertThatThrownBy(() -> service.changePassword("raw-token", TEMPORARY, CHOSEN))
                .isInstanceOf(InvalidRefreshTokenException.class);
        when(accounts.findById(account.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.changePassword("raw-token", TEMPORARY, CHOSEN))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }
}
