package com.stockforecast.identity.domain;

import com.stockforecast.identity.AccountStatus;
import com.stockforecast.identity.AuthenticatedUser;
import com.stockforecast.identity.ManagedAccount;
import com.stockforecast.identity.Role;
import com.stockforecast.shared.domain.Identifiers;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A user account: login identity, credential hash, role and status. Profile data (display name,
 * preferences) is owned by the user module. Only {@link AccountStatus#ACTIVE} accounts may sign in.
 *
 * <p>Trade-off: JPA mapping annotations live on the domain type to avoid a parallel persistence model; all
 * state changes still go through intention-revealing methods and the type never leaves the module.
 */
@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    private UUID id;

    @Column(nullable = false, length = EmailAddress.MAX_LENGTH)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    /** Set for temporary passwords chosen by an administrator; cleared when the user picks their own password. */
    @Column(name = "password_change_required", nullable = false)
    private boolean passwordChangeRequired;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected UserAccount() {
        // required by JPA
    }

    private UserAccount(UUID id, EmailAddress email, String passwordHash, Role role, AccountStatus status,
                        boolean passwordChangeRequired, Instant now) {
        this.id = Objects.requireNonNull(id, "id");
        this.email = Objects.requireNonNull(email, "email").value();
        this.passwordHash = requireHash(passwordHash);
        this.role = Objects.requireNonNull(role, "role");
        this.status = Objects.requireNonNull(status, "status");
        this.passwordChangeRequired = passwordChangeRequired;
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    /** Self-service sign-up: active, with a password the user chose. */
    public static UserAccount register(EmailAddress email, String passwordHash, Role role, Instant now) {
        return new UserAccount(Identifiers.newId(), email, passwordHash, role, AccountStatus.ACTIVE, false, now);
    }

    /** Created by an administrator (or bootstrapping), typically with a temporary password. */
    public static UserAccount provision(EmailAddress email, String passwordHash, Role role, AccountStatus status,
                                        boolean passwordChangeRequired, Instant now) {
        return new UserAccount(Identifiers.newId(), email, passwordHash, role, status, passwordChangeRequired, now);
    }

    /** Transparent re-hash (e.g. stronger work factor); the password itself is unchanged. */
    public void changePasswordHash(String newPasswordHash, Instant now) {
        this.passwordHash = requireHash(newPasswordHash);
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    /** The user chose a new password: any pending forced change is fulfilled. */
    public void changePassword(String newPasswordHash, Instant now) {
        changePasswordHash(newPasswordHash, now);
        this.passwordChangeRequired = false;
    }

    /** An administrator set a temporary password. */
    public void resetPassword(String temporaryPasswordHash, boolean requireChange, Instant now) {
        changePasswordHash(temporaryPasswordHash, now);
        this.passwordChangeRequired = requireChange;
    }

    public void changeEmail(EmailAddress newEmail, Instant now) {
        String normalized = Objects.requireNonNull(newEmail, "newEmail").value();
        if (!normalized.equals(email)) {
            this.email = normalized;
            this.updatedAt = Objects.requireNonNull(now, "now");
        }
    }

    public void changeRole(Role newRole, Instant now) {
        if (Objects.requireNonNull(newRole, "newRole") != role) {
            this.role = newRole;
            this.updatedAt = Objects.requireNonNull(now, "now");
        }
    }

    public void changeStatus(AccountStatus newStatus, Instant now) {
        if (Objects.requireNonNull(newStatus, "newStatus") != status) {
            this.status = newStatus;
            this.updatedAt = Objects.requireNonNull(now, "now");
        }
    }

    public void disable(Instant now) {
        changeStatus(AccountStatus.DISABLED, now);
    }

    /** Not an edit of the account, so {@code updatedAt} stays unchanged. */
    public void recordLogin(Instant now) {
        this.lastLoginAt = Objects.requireNonNull(now, "now");
    }

    public AuthenticatedUser toAuthenticatedUser() {
        return new AuthenticatedUser(id, email, role);
    }

    public ManagedAccount toManagedAccount() {
        return new ManagedAccount(id, email, role, status, passwordChangeRequired, createdAt, updatedAt, lastLoginAt);
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public boolean isActiveAdministrator() {
        return isActive() && role == Role.ADMIN;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public boolean isPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static String requireHash(String hash) {
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("password hash must not be blank");
        }
        return hash;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof UserAccount account && id.equals(account.getId());
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "UserAccount[id=" + id + ", role=" + role + ", status=" + status + "]";
    }
}
