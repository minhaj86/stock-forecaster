package com.stockforecast.identity.application;

import com.stockforecast.identity.AccountAdministration.AccountChanges;
import com.stockforecast.identity.AccountAdministration.NewAccount;
import com.stockforecast.identity.AccountStatus;
import com.stockforecast.identity.ManagedAccount;
import com.stockforecast.identity.Role;
import com.stockforecast.identity.UserAccountDeleted;
import com.stockforecast.identity.domain.AccountAdministrationExceptions.AccountNotFoundException;
import com.stockforecast.identity.domain.AccountAdministrationExceptions.LastAdministratorException;
import com.stockforecast.identity.domain.AccountAdministrationExceptions.SelfAdministrationException;
import com.stockforecast.identity.domain.EmailAddress;
import com.stockforecast.identity.domain.EmailAlreadyRegisteredException;
import com.stockforecast.identity.domain.PasswordPolicyViolationException;
import com.stockforecast.identity.domain.RefreshTokenRepository;
import com.stockforecast.identity.domain.RevocationReason;
import com.stockforecast.identity.domain.UserAccount;
import com.stockforecast.identity.domain.UserAccountRepository;
import com.stockforecast.identity.domain.UserAccountSearch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountAdministrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    private static final String TEMPORARY_PASSWORD = "Temporary-Password-42";

    @Mock
    private UserAccountRepository accounts;
    @Mock
    private UserAccountSearch accountSearch;
    @Mock
    private RefreshTokenRepository refreshTokens;
    @Mock
    private RegistrationService registrationService;
    @Mock
    private ApplicationEventPublisher events;

    private final PasswordEncoder passwordEncoder =
            new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(4)));
    private final UserAccount admin = account("admin@example.com", Role.ADMIN);
    private final UserAccount otherAdmin = account("other-admin@example.com", Role.ADMIN);
    private final UserAccount member = account("member@example.com", Role.USER);

    private AccountAdministrationService service;

    @BeforeEach
    void setUp() {
        lenient().when(accounts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        for (UserAccount account : List.of(admin, otherAdmin, member)) {
            lenient().when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
        }
        service = new AccountAdministrationService(accounts, accountSearch, refreshTokens, registrationService,
                passwordEncoder, events, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void administratorsCannotDeleteDisableLockOrDemoteThemselves() {
        assertThatThrownBy(() -> service.delete(admin.getId(), admin.getId()))
                .isInstanceOf(SelfAdministrationException.class);
        assertThatThrownBy(() -> service.update(admin.getId(), admin.getId(),
                new AccountChanges(null, Role.USER, null))).isInstanceOf(SelfAdministrationException.class);
        assertThatThrownBy(() -> service.update(admin.getId(), admin.getId(),
                new AccountChanges(null, null, AccountStatus.DISABLED))).isInstanceOf(SelfAdministrationException.class);
        assertThatThrownBy(() -> service.update(admin.getId(), admin.getId(),
                new AccountChanges(null, null, AccountStatus.LOCKED))).isInstanceOf(SelfAdministrationException.class);
        verify(accounts, never()).delete(any());
    }

    @Test
    void theLastActiveAdministratorCannotBeDeletedDemotedOrDisabled() {
        when(accounts.lockActiveAdministrators()).thenReturn(List.of(otherAdmin));

        assertThatThrownBy(() -> service.delete(admin.getId(), otherAdmin.getId()))
                .isInstanceOf(LastAdministratorException.class);
        assertThatThrownBy(() -> service.update(admin.getId(), otherAdmin.getId(),
                new AccountChanges(null, Role.USER, null))).isInstanceOf(LastAdministratorException.class);
        assertThatThrownBy(() -> service.update(admin.getId(), otherAdmin.getId(),
                new AccountChanges(null, null, AccountStatus.DISABLED))).isInstanceOf(LastAdministratorException.class);
        assertThat(otherAdmin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(otherAdmin.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(events, never()).publishEvent(any());
    }

    @Test
    void anotherAdministratorCanBeDemotedWhileOneRemains() {
        when(accounts.lockActiveAdministrators()).thenReturn(List.of(admin, otherAdmin));

        ManagedAccount demoted = service.update(admin.getId(), otherAdmin.getId(),
                new AccountChanges(null, Role.USER, null));

        assertThat(demoted.role()).isEqualTo(Role.USER);
    }

    @Test
    void deletionLetsModulesEraseTheirDataBeforeTheAccountIsRemoved() {
        ManagedAccount deleted = service.delete(admin.getId(), member.getId());

        assertThat(deleted.email()).isEqualTo("member@example.com");
        InOrder order = inOrder(events, refreshTokens, accounts);
        order.verify(events).publishEvent(new UserAccountDeleted(member.getId(), "member@example.com", NOW));
        order.verify(refreshTokens).deleteAllByUserId(member.getId());
        order.verify(accounts).delete(member);
    }

    @Test
    void disablingOrLockingSignsTheUserOutEverywhere() {
        ManagedAccount disabled = service.update(admin.getId(), member.getId(),
                new AccountChanges(null, null, AccountStatus.DISABLED));

        assertThat(disabled.status()).isEqualTo(AccountStatus.DISABLED);
        verify(refreshTokens).revokeAllOfUser(member.getId(), RevocationReason.ADMIN_ACTION, NOW);

        service.update(admin.getId(), member.getId(), new AccountChanges(null, null, AccountStatus.ACTIVE));
        verify(refreshTokens).revokeAllOfUser(eq(member.getId()), any(), any());
    }

    @Test
    void emailChangesMustStayUnique() {
        when(accounts.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.update(admin.getId(), member.getId(),
                new AccountChanges(" Taken@Example.com ", null, null)))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        ManagedAccount renamed = service.update(admin.getId(), member.getId(),
                new AccountChanges("New.Address@Example.com", null, null));
        assertThat(renamed.email()).isEqualTo("new.address@example.com");
    }

    @Test
    void passwordResetsSetATemporaryPasswordAndEndAllSessions() {
        ManagedAccount reset = service.resetPassword(admin.getId(), member.getId(), TEMPORARY_PASSWORD, true);

        assertThat(reset.passwordChangeRequired()).isTrue();
        assertThat(passwordEncoder.matches(TEMPORARY_PASSWORD, member.getPasswordHash())).isTrue();
        verify(refreshTokens).revokeAllOfUser(member.getId(), RevocationReason.ADMIN_ACTION, NOW);
        assertThatThrownBy(() -> service.resetPassword(admin.getId(), member.getId(), "short", true))
                .isInstanceOf(PasswordPolicyViolationException.class);
    }

    @Test
    void createsAccountsThroughRegistrationSoTheProfileIsCreatedToo() {
        UserAccount created = UserAccount.provision(EmailAddress.of("new@example.com"), "{bcrypt}hash", Role.USER,
                AccountStatus.ACTIVE, true, NOW);
        when(registrationService.createAccount(EmailAddress.of("new@example.com"), TEMPORARY_PASSWORD, "New User",
                Role.USER, AccountStatus.ACTIVE, true)).thenReturn(created);

        ManagedAccount account = service.create(admin.getId(), new NewAccount("new@example.com", "New User",
                Role.USER, AccountStatus.ACTIVE, TEMPORARY_PASSWORD, true));

        assertThat(account.passwordChangeRequired()).isTrue();
        assertThat(new NewAccount("a@b.c", "x", Role.USER, AccountStatus.ACTIVE, TEMPORARY_PASSWORD, true).toString())
                .doesNotContain(TEMPORARY_PASSWORD);
    }

    @Test
    void unknownAccountsAreNotFound() {
        when(accounts.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(UUID.randomUUID()))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void reportsWhetherAnAccountIsStillAnActiveAdministrator() {
        otherAdmin.disable(NOW);

        assertThat(service.isActiveAdministrator(admin.getId())).isTrue();
        assertThat(service.isActiveAdministrator(otherAdmin.getId())).isFalse();
        assertThat(service.isActiveAdministrator(member.getId())).isFalse();
    }

    private static UserAccount account(String email, Role role) {
        return UserAccount.register(EmailAddress.of(email), "{bcrypt}$2a$04$hash", role, NOW);
    }
}
