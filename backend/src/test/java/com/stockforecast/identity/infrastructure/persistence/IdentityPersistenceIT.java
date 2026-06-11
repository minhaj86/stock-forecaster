package com.stockforecast.identity.infrastructure.persistence;

import com.stockforecast.identity.Role;
import com.stockforecast.identity.domain.EmailAddress;
import com.stockforecast.identity.domain.RefreshToken;
import com.stockforecast.identity.domain.RefreshTokenRepository;
import com.stockforecast.identity.domain.RevocationReason;
import com.stockforecast.identity.domain.UserAccount;
import com.stockforecast.identity.domain.UserAccountRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Repository behaviour and schema invariants against real PostgreSQL with the Flyway-managed schema.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainersConfiguration.class)
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

    private static UserAccount newAccount(String email, Role role) {
        return UserAccount.register(EmailAddress.of(email), "{bcrypt}$2a$04$hash", role, NOW);
    }

    private void insertUserRow(UUID id, String email) {
        jdbcTemplate.update("""
                insert into users (id, email, password_hash, role, enabled, created_at, updated_at)
                values (?, ?, '{bcrypt}x', 'USER', true, now(), now())""", id, email);
    }
}
