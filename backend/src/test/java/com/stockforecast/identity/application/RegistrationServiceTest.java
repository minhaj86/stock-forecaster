package com.stockforecast.identity.application;

import com.stockforecast.identity.Role;
import com.stockforecast.identity.UserRegisteredEvent;
import com.stockforecast.identity.domain.EmailAlreadyRegisteredException;
import com.stockforecast.identity.domain.InvalidEmailAddressException;
import com.stockforecast.identity.domain.PasswordPolicyViolationException;
import com.stockforecast.identity.domain.UserAccount;
import com.stockforecast.identity.domain.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    private static final String PASSWORD = "correct-horse-battery-staple";

    @Mock
    private UserAccountRepository accounts;
    @Mock
    private ApplicationEventPublisher events;
    @Mock
    private UserSessionService sessions;

    private final PasswordEncoder passwordEncoder =
            new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(4)));

    private RegistrationService service() {
        return new RegistrationService(accounts, passwordEncoder, events, sessions, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void registersAHashedUserAccountPublishesTheEventAndSignsIn() {
        when(accounts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service().register(new RegisterUserCommand("Ada@Example.com", PASSWORD, "Ada"));

        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(accounts).save(saved.capture());
        UserAccount account = saved.getValue();
        assertThat(account.getEmail()).isEqualTo("ada@example.com");
        assertThat(account.getRole()).isEqualTo(Role.USER);
        assertThat(account.getPasswordHash()).startsWith("{bcrypt}").doesNotContain(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, account.getPasswordHash())).isTrue();
        verify(events).publishEvent(new UserRegisteredEvent(account.getId(), "ada@example.com", "Ada", NOW));
        verify(sessions).start(account, true);
    }

    @Test
    void rejectsAlreadyRegisteredEmail() {
        when(accounts.existsByEmail("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service().register(new RegisterUserCommand("ADA@example.com", PASSWORD, "Ada")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        verify(accounts, never()).save(any());
        verifyNoInteractions(events, sessions);
    }

    @Test
    void enforcesThePasswordPolicyBeforeTouchingStorage() {
        assertThatThrownBy(() -> service().register(new RegisterUserCommand("ada@example.com", "short", "Ada")))
                .isInstanceOf(PasswordPolicyViolationException.class);
        verifyNoInteractions(accounts, events, sessions);
    }

    @Test
    void rejectsInvalidEmail() {
        assertThatThrownBy(() -> service().register(new RegisterUserCommand("ada@", PASSWORD, "Ada")))
                .isInstanceOf(InvalidEmailAddressException.class);
        verifyNoInteractions(accounts, events, sessions);
    }
}
