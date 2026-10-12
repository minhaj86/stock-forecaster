package com.stockforecast.identity.application;

import com.stockforecast.identity.AccountAdministration;
import com.stockforecast.identity.AccountStatistics;
import com.stockforecast.identity.AccountStatus;
import com.stockforecast.identity.ManagedAccount;
import com.stockforecast.identity.Role;
import com.stockforecast.identity.UserAccountDeleted;
import com.stockforecast.identity.domain.AccountAdministrationExceptions.AccountNotFoundException;
import com.stockforecast.identity.domain.AccountAdministrationExceptions.LastAdministratorException;
import com.stockforecast.identity.domain.AccountAdministrationExceptions.SelfAdministrationException;
import com.stockforecast.identity.domain.EmailAddress;
import com.stockforecast.identity.domain.EmailAlreadyRegisteredException;
import com.stockforecast.identity.domain.PasswordPolicy;
import com.stockforecast.identity.domain.RefreshTokenRepository;
import com.stockforecast.identity.domain.RevocationReason;
import com.stockforecast.identity.domain.UserAccount;
import com.stockforecast.identity.domain.UserAccountRepository;
import com.stockforecast.identity.domain.UserAccountSearch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Account administration with the safeguards that keep the platform administrable:
 * <ul>
 *     <li>administrators cannot delete, disable, lock or demote their own account;</li>
 *     <li>at least one active administrator must remain (checked under a row lock on all active administrators, so
 *     two administrators demoting each other concurrently cannot both succeed);</li>
 *     <li>disabling, locking or resetting the password signs the user out everywhere.</li>
 * </ul>
 */
@Service
public class AccountAdministrationService implements AccountAdministration {

    private static final Logger log = LoggerFactory.getLogger(AccountAdministrationService.class);

    private final UserAccountRepository accounts;
    private final UserAccountSearch accountSearch;
    private final RefreshTokenRepository refreshTokens;
    private final RegistrationService registrationService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AccountAdministrationService(UserAccountRepository accounts, UserAccountSearch accountSearch,
                                        RefreshTokenRepository refreshTokens, RegistrationService registrationService,
                                        PasswordEncoder passwordEncoder, ApplicationEventPublisher events,
                                        Clock clock) {
        this.accounts = accounts;
        this.accountSearch = accountSearch;
        this.refreshTokens = refreshTokens;
        this.registrationService = registrationService;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public AccountPage search(AccountSearch search) {
        String fragment = search.text() == null || search.text().isBlank() ? null
                : search.text().strip().toLowerCase(Locale.ROOT);
        UserAccountSearch.SearchPage result = accountSearch.search(new UserAccountSearch.Criteria(fragment,
                fragment == null ? Set.of() : search.alsoMatchingIds(), search.role(), search.status(),
                search.createdFrom(), search.createdBefore(), search.page(), search.size()));
        return new AccountPage(result.content().stream().map(UserAccount::toManagedAccount).toList(), search.page(),
                search.size(), result.totalElements());
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public Optional<ManagedAccount> find(UUID accountId) {
        return accounts.findById(accountId).map(UserAccount::toManagedAccount);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public ManagedAccount get(UUID accountId) {
        return load(accountId).toManagedAccount();
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public Map<UUID, String> findEmails(Collection<UUID> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        return accounts.findAllByIdIn(accountIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, UserAccount::getEmail));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public AccountStatistics statistics() {
        Instant now = clock.instant();
        return new AccountStatistics(
                accounts.count(),
                accounts.countByStatus(AccountStatus.ACTIVE),
                accounts.countByStatus(AccountStatus.DISABLED),
                accounts.countByStatus(AccountStatus.LOCKED),
                accounts.countByRole(Role.ADMIN),
                accounts.countByCreatedAtGreaterThanEqual(now.minus(Duration.ofDays(7))),
                accounts.countByCreatedAtGreaterThanEqual(now.minus(Duration.ofDays(30))));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ManagedAccount create(UUID actorId, NewAccount account) {
        UserAccount created = registrationService.createAccount(EmailAddress.of(account.email()),
                account.temporaryPassword(), account.displayName(), Objects.requireNonNull(account.role(), "role"),
                Objects.requireNonNull(account.status(), "status"), account.requirePasswordChange());
        log.info("Administrator {} created account {} with role {}", actorId, created.getId(), created.getRole());
        return created.toManagedAccount();
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ManagedAccount update(UUID actorId, UUID accountId, AccountChanges changes) {
        UserAccount account = load(accountId);
        Instant now = clock.instant();
        boolean self = accountId.equals(actorId);
        Role newRole = changes.role() == null ? account.getRole() : changes.role();
        AccountStatus newStatus = changes.status() == null ? account.getStatus() : changes.status();

        if (self && newRole != Role.ADMIN && account.getRole() == Role.ADMIN) {
            throw SelfAdministrationException.demotion();
        }
        if (self && newStatus != AccountStatus.ACTIVE && account.isActive()) {
            throw SelfAdministrationException.deactivation();
        }
        boolean losesActiveAdministrator = account.isActiveAdministrator()
                && (newRole != Role.ADMIN || newStatus != AccountStatus.ACTIVE);
        if (losesActiveAdministrator) {
            ensureAnotherActiveAdministrator(accountId);
        }
        if (changes.email() != null) {
            EmailAddress email = EmailAddress.of(changes.email());
            if (!email.value().equals(account.getEmail()) && accounts.existsByEmail(email.value())) {
                throw new EmailAlreadyRegisteredException();
            }
            account.changeEmail(email, now);
        }
        boolean signOut = account.isActive() && newStatus != AccountStatus.ACTIVE;
        account.changeRole(newRole, now);
        account.changeStatus(newStatus, now);
        UserAccount saved = accounts.save(account);
        if (signOut) {
            int revoked = refreshTokens.revokeAllOfUser(accountId, RevocationReason.ADMIN_ACTION, now);
            log.info("Administrator {} set account {} to {}; {} session token(s) revoked", actorId, accountId,
                    newStatus, revoked);
        }
        return saved.toManagedAccount();
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ManagedAccount resetPassword(UUID actorId, UUID accountId, String temporaryPassword,
                                       boolean requireChange) {
        UserAccount account = load(accountId);
        PasswordPolicy.DEFAULT.validate(temporaryPassword);
        Instant now = clock.instant();
        account.resetPassword(passwordEncoder.encode(temporaryPassword), requireChange, now);
        UserAccount saved = accounts.save(account);
        refreshTokens.revokeAllOfUser(accountId, RevocationReason.ADMIN_ACTION, now);
        log.info("Administrator {} reset the password of account {}", actorId, accountId);
        return saved.toManagedAccount();
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ManagedAccount delete(UUID actorId, UUID accountId) {
        UserAccount account = load(accountId);
        if (accountId.equals(actorId)) {
            throw SelfAdministrationException.deletion();
        }
        if (account.isActiveAdministrator()) {
            ensureAnotherActiveAdministrator(accountId);
        }
        ManagedAccount snapshot = account.toManagedAccount();
        Instant now = clock.instant();
        // Modules delete what they own first (synchronous listeners in this transaction), then the account itself.
        events.publishEvent(new UserAccountDeleted(accountId, account.getEmail(), now));
        refreshTokens.deleteAllByUserId(accountId);
        accounts.delete(account);
        log.info("Administrator {} deleted account {}", actorId, accountId);
        return snapshot;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActiveAdministrator(UUID accountId) {
        return accounts.findById(accountId).map(UserAccount::isActiveAdministrator).orElse(false);
    }

    private void ensureAnotherActiveAdministrator(UUID accountId) {
        Map<UUID, UserAccount> administrators = accounts.lockActiveAdministrators().stream()
                .collect(Collectors.toMap(UserAccount::getId, Function.identity()));
        administrators.remove(accountId);
        if (administrators.isEmpty()) {
            throw new LastAdministratorException();
        }
    }

    private UserAccount load(UUID accountId) {
        return accounts.findById(accountId).orElseThrow(AccountNotFoundException::new);
    }
}
