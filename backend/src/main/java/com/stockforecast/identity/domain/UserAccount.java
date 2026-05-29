package com.stockforecast.identity.domain;

import com.stockforecast.identity.AuthenticatedUser;
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
 * preferences) is owned by the user module.
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

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected UserAccount() {
        // required by JPA
    }

    private UserAccount(UUID id, EmailAddress email, String passwordHash, Role role, Instant now) {
        this.id = Objects.requireNonNull(id, "id");
        this.email = Objects.requireNonNull(email, "email").value();
        this.passwordHash = requireHash(passwordHash);
        this.role = Objects.requireNonNull(role, "role");
        this.enabled = true;
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public static UserAccount register(EmailAddress email, String passwordHash, Role role, Instant now) {
        return new UserAccount(Identifiers.newId(), email, passwordHash, role, now);
    }

    public void changePasswordHash(String newPasswordHash, Instant now) {
        this.passwordHash = requireHash(newPasswordHash);
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void disable(Instant now) {
        if (enabled) {
            this.enabled = false;
            this.updatedAt = Objects.requireNonNull(now, "now");
        }
    }

    public AuthenticatedUser toAuthenticatedUser() {
        return new AuthenticatedUser(id, email, role);
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

    public boolean isEnabled() {
        return enabled;
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
        return "UserAccount[id=" + id + ", role=" + role + ", enabled=" + enabled + "]";
    }
}
