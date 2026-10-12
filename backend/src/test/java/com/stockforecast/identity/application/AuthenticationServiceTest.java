package com.stockforecast.identity.application;

import com.stockforecast.identity.AccountStatus;
import com.stockforecast.identity.Role;
import com.stockforecast.identity.domain.AccountDisabledException;
import com.stockforecast.identity.domain.AccountLockedException;
import com.stockforecast.identity.domain.EmailAddress;
import com.stockforecast.identity.domain.InvalidCredentialsException;
import com.stockforecast.identity.domain.UserAccount;
import com.stockforecast.identity.domain.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    private static final String PASSWORD = "correct-horse-battery-staple";

    @Mock
    private UserAccountRepository accounts;
    @Mock
    private UserSessionService sessions;

    private final PasswordEncoder passwordEncoder = spy(bcrypt(5));

    private AuthenticationService service() {
        return new AuthenticationService(accounts, passwordEncoder, sessions, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void validCredentialsStartASession() {
        UserAccount account = account(passwordEncoder.encode(PASSWORD));
        when(accounts.findByEmail("ada@example.com")).thenReturn(Optional.of(account));

        service().login(new LoginCommand(" Ada@Example.com ", PASSWORD, true));

        verify(sessions).start(account, true);
    }

    @Test
    void rememberMeChoiceIsPassedToTheSession() {
        UserAccount account = account(passwordEncoder.encode(PASSWORD));
        when(accounts.findByEmail("ada@example.com")).thenReturn(Optional.of(account));

        service().login(new LoginCommand("ada@example.com", PASSWORD, false));

        verify(sessions).start(account, false);
    }

    @Test
    void unknownEmailIsIndistinguishableFromAWrongPassword() {
        AuthenticationService service = service();
        when(accounts.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginCommand("nobody@example.com", PASSWORD, true)))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid e-mail address or password");
        // A hash comparison still happens so response timing does not reveal whether the account exists.
        verify(passwordEncoder).matches(eq(PASSWORD), anyString());
        verifyNoInteractions(sessions);
    }

    @Test
    void malformedEmailIsRejectedAsInvalidCredentials() {
        assertThatThrownBy(() -> service().login(new LoginCommand("not-an-email", PASSWORD, true)))
                .isInstanceOf(InvalidCredentialsException.class);
        verifyNoInteractions(accounts, sessions);
    }

    @Test
    void wrongPasswordIsRejected() {
        UserAccount account = account(passwordEncoder.encode(PASSWORD));
        when(accounts.findByEmail("ada@example.com")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service().login(new LoginCommand("ada@example.com", "wrong-password-123", true)))
                .isInstanceOf(InvalidCredentialsException.class);
        verifyNoInteractions(sessions);
    }

    @Test
    void passwordsBeyondTheBcryptLimitNeverMatchEvenIfTheirPrefixWould() {
        String seventyTwoBytes = "p".repeat(72);
        UserAccount account = account(passwordEncoder.encode(seventyTwoBytes));
        when(accounts.findByEmail("ada@example.com")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service().login(new LoginCommand("ada@example.com", seventyTwoBytes + "suffix", true)))
                .isInstanceOf(InvalidCredentialsException.class);
        verifyNoInteractions(sessions);
    }

    @Test
    void disabledAccountsCannotSignIn() {
        UserAccount account = account(passwordEncoder.encode(PASSWORD));
        account.disable(NOW);
        when(accounts.findByEmail("ada@example.com")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service().login(new LoginCommand("ada@example.com", PASSWORD, true)))
                .isInstanceOf(AccountDisabledException.class);
        verify(sessions, never()).start(account, true);
    }

    @Test
    void lockedAccountsCannotSignInAndAreToldToContactAnAdministrator() {
        UserAccount account = account(passwordEncoder.encode(PASSWORD));
        account.changeStatus(AccountStatus.LOCKED, NOW);
        when(accounts.findByEmail("ada@example.com")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service().login(new LoginCommand("ada@example.com", PASSWORD, true)))
                .isInstanceOf(AccountLockedException.class)
                .hasMessageContaining("Contact an administrator");
        verify(sessions, never()).start(account, true);
    }

    @Test
    void theAccountStatusIsOnlyRevealedAfterACorrectPassword() {
        UserAccount account = account(passwordEncoder.encode(PASSWORD));
        account.changeStatus(AccountStatus.LOCKED, NOW);
        when(accounts.findByEmail("ada@example.com")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service().login(new LoginCommand("ada@example.com", "wrong-password-123", true)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void successfulSignInsAreRecorded() {
        UserAccount account = account(passwordEncoder.encode(PASSWORD));
        when(accounts.findByEmail("ada@example.com")).thenReturn(Optional.of(account));

        service().login(new LoginCommand("ada@example.com", PASSWORD, false));

        assertThat(account.getLastLoginAt()).isEqualTo(NOW);
    }

    @Test
    void outdatedHashesAreUpgradedOnSuccessfulLogin() {
        String weakHash = bcrypt(4).encode(PASSWORD);
        UserAccount account = account(weakHash);
        when(accounts.findByEmail("ada@example.com")).thenReturn(Optional.of(account));

        service().login(new LoginCommand("ada@example.com", PASSWORD, true));

        assertThat(account.getPasswordHash()).isNotEqualTo(weakHash).startsWith("{bcrypt}$2a$05$");
        assertThat(passwordEncoder.matches(PASSWORD, account.getPasswordHash())).isTrue();
    }

    private static UserAccount account(String passwordHash) {
        return UserAccount.register(EmailAddress.of("ada@example.com"), passwordHash, Role.USER, NOW);
    }

    private static PasswordEncoder bcrypt(int strength) {
        return new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(strength)));
    }
}
