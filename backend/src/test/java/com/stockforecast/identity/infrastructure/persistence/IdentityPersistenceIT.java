package com.stockforecast.identity.infrastructure.persistence;

import com.stockforecast.identity.AccountStatus;
import com.stockforecast.identity.Role;
import com.stockforecast.identity.domain.EmailAddress;
import com.stockforecast.identity.domain.RefreshToken;
import com.stockforecast.identity.domain.RefreshTokenRepository;
import com.stockforecast.identity.domain.RevocationReason;
import com.stockforecast.identity.domain.UserAccount;
import com.stockforecast.identity.domain.UserAccountRepository;
import com.stockforecast.identity.domain.UserAccountSearch;
import com.stockforecast.support.PostgresTestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Repository behaviour and schema invariants against real PostgreSQL with the Flyway-managed schema.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainersConfiguration.class, JpaUserAccountSearch.class})
@ActiveProfiles("test")
class IdentityPersistenceIT {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private static final Duration TTL = Duration.ofDays(7);
    private static final Duration LIFETIME = Duration.ofDays(30);

    @Autowired
    private UserAccountRepository accounts;
    @Autowired
    private RefreshTokenRepository refreshTokens;
    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private UserAccountSearch accountSearch;

    @Test
    void persistsAndQueriesAccounts() {
        UserAccount account = accounts.save(newAccount("query@example.com", Role.ADMIN));
        entityManager.flush();
        entityManager.clear();

        assertThat(accounts.findByEmail("query@example.com")).get()
                .satisfies(found -> {
                    assertThat(found.getId()).isEqualTo(account.getId());
                    assertThat(found.getRole()).isEqualTo(Role.ADMIN);
                    assertThat(found.getCreatedAt()).isEqualTo(NOW);
                });
        assertThat(accounts.existsByEmail("query@example.com")).isTrue();
        assertThat(accounts.existsByEmail("missing@example.com")).isFalse();
        assertThat(accounts.countByRole(Role.ADMIN)).isGreaterThanOrEqualTo(1);
        assertThat(accounts.countByCreatedAtGreaterThanEqual(NOW.minusSeconds(1))).isGreaterThanOrEqualTo(1);
    }

    @Test
    void locksExactlyTheActiveAdministrators() {
        UserAccount activeAdmin = accounts.save(newAccount("admin-a@example.com", Role.ADMIN));
        UserAccount disabledAdmin = newAccount("admin-b@example.com", Role.ADMIN);
        disabledAdmin.disable(NOW);
        accounts.save(disabledAdmin);
        accounts.save(newAccount("member@example.com", Role.USER));
        entityManager.flush();

        assertThat(accounts.lockActiveAdministrators()).extracting(UserAccount::getId)
                .containsExactly(activeAdmin.getId());
        assertThat(accounts.countByStatus(AccountStatus.DISABLED)).isEqualTo(1);
        assertThat(accounts.findAllByIdIn(List.of(activeAdmin.getId(), disabledAdmin.getId()))).hasSize(2);
    }

    @Test
    void searchesByEmailOrExtraIdsAndFilters() {
        UserAccount alice = accounts.save(newAccount("alice.search@example.com", Role.USER));
        UserAccount bob = accounts.save(newAccount("bob.search@example.com", Role.ADMIN));
        UserAccount carol = newAccount("carol.search@example.com", Role.USER);
        carol.disable(NOW);
        accounts.save(carol);
        entityManager.flush();

        assertThat(accountSearch.search(criteria("alice", Set.of(), null, null)).content())
                .extracting(UserAccount::getId).containsExactly(alice.getId());
        // A display-name match found by the user module is OR-ed with the e-mail match.
        assertThat(accountSearch.search(criteria("alice", Set.of(bob.getId()), null, null)).content())
                .extracting(UserAccount::getId).containsExactlyInAnyOrder(alice.getId(), bob.getId());
        assertThat(accountSearch.search(criteria(".search@", Set.of(), Role.ADMIN, null)).content())
                .extracting(UserAccount::getId).containsExactly(bob.getId());
        assertThat(accountSearch.search(criteria(".search@", Set.of(), null, AccountStatus.DISABLED)).content())
                .extracting(UserAccount::getId).containsExactly(carol.getId());
        // LIKE wildcards in the search text are literals.
        assertThat(accountSearch.search(criteria("%", Set.of(), null, null)).totalElements()).isZero();
    }

    @Test
    void revokesEverySessionOfAUserAndDeletesTheirTokens() {
        UserAccount account = accounts.save(newAccount("sessions@example.com", Role.USER));
        UserAccount other = accounts.save(newAccount("other-sessions@example.com", Role.USER));
        refreshTokens.save(RefreshToken.startSession(account.getId(), "e".repeat(64), NOW, TTL, LIFETIME, true));
        refreshTokens.save(RefreshToken.startSession(account.getId(), "f".repeat(64), NOW, TTL, LIFETIME, false));
        refreshTokens.save(RefreshToken.startSession(other.getId(), "0".repeat(64), NOW, TTL, LIFETIME, true));
        entityManager.flush();

        assertThat(refreshTokens.revokeAllOfUser(account.getId(), RevocationReason.ADMIN_ACTION, NOW)).isEqualTo(2);
        assertThat(refreshTokens.findByTokenHashForUpdate("e".repeat(64))).get()
                .extracting(RefreshToken::getRevocationReason).isEqualTo(RevocationReason.ADMIN_ACTION);
        assertThat(refreshTokens.findByTokenHashForUpdate("0".repeat(64))).get()
                .extracting(RefreshToken::isRevoked).isEqualTo(false);

        refreshTokens.deleteAllByUserId(account.getId());

        assertThat(refreshTokens.findByTokenHashForUpdate("e".repeat(64))).isEmpty();
        assertThat(refreshTokens.findByTokenHashForUpdate("0".repeat(64))).isPresent();
    }

    @Test
    void databaseRejectsDuplicateEmails() {
        insertUserRow(UUID.randomUUID(), "duplicate@example.com");

        assertThatThrownBy(() -> insertUserRow(UUID.randomUUID(), "duplicate@example.com"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsEmailsThatAreNotNormalised() {
        assertThatThrownBy(() -> insertUserRow(UUID.randomUUID(), "Mixed.Case@Example.com"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsTokensByHashAndRevokesOnlyTheActiveTokensOfOneSession() {
        UserAccount account = accounts.save(newAccount("tokens@example.com", Role.USER));
        RefreshToken first = RefreshToken.startSession(account.getId(), "a".repeat(64), NOW, TTL, LIFETIME, true);
        refreshTokens.save(first);
        RefreshToken second = refreshTokens.save(first.rotate("b".repeat(64), NOW.plusSeconds(1), TTL));
        RefreshToken otherSession = refreshTokens.save(
                RefreshToken.startSession(account.getId(), "c".repeat(64), NOW, TTL, LIFETIME, true));
        entityManager.flush();

        int revoked = refreshTokens.revokeFamily(first.getFamilyId(), RevocationReason.LOGOUT, NOW.plusSeconds(2));

        assertThat(revoked).isEqualTo(1);
        assertThat(refreshTokens.findByTokenHashForUpdate("a".repeat(64))).get()
                .extracting(RefreshToken::getRevocationReason).isEqualTo(RevocationReason.ROTATED);
        assertThat(refreshTokens.findByTokenHashForUpdate("b".repeat(64))).get()
                .satisfies(token -> {
                    assertThat(token.getId()).isEqualTo(second.getId());
                    assertThat(token.getRevocationReason()).isEqualTo(RevocationReason.LOGOUT);
                });
        assertThat(refreshTokens.findByTokenHashForUpdate("c".repeat(64))).get()
                .satisfies(token -> {
                    assertThat(token.getId()).isEqualTo(otherSession.getId());
                    assertThat(token.isRevoked()).isFalse();
                });
        assertThat(refreshTokens.findByTokenHashForUpdate("d".repeat(64))).isEmpty();
    }

    private static UserAccountSearch.Criteria criteria(String fragment, Set<UUID> ids, Role role,
                                                       AccountStatus status) {
        return new UserAccountSearch.Criteria(fragment, ids, role, status, null, null, 0, 20);
    }

    private static UserAccount newAccount(String email, Role role) {
        return UserAccount.register(EmailAddress.of(email), "{bcrypt}$2a$04$hash", role, NOW);
    }

    private void insertUserRow(UUID id, String email) {
        jdbcTemplate.update("""
                insert into users (id, email, password_hash, role, status, created_at, updated_at)
                values (?, ?, '{bcrypt}x', 'USER', 'ACTIVE', now(), now())""", id, email);
    }
}
