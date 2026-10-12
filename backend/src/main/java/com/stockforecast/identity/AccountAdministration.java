package com.stockforecast.identity;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Account administration for the admin console. Every mutating method takes the acting administrator's id and
 * enforces the safeguards: administrators cannot delete, disable, lock or demote themselves, and at least one active
 * administrator always remains. Callers must be administrators (enforced with method security).
 */
public interface AccountAdministration {

    AccountPage search(AccountSearch search);

    Optional<ManagedAccount> find(UUID accountId);

    /**
     * @throws com.stockforecast.shared.domain.DomainException {@code USER_NOT_FOUND} (404) if there is no such account
     */
    ManagedAccount get(UUID accountId);

    /** E-mail addresses of existing accounts; ids of deleted accounts are absent from the result. */
    Map<UUID, String> findEmails(Collection<UUID> accountIds);

    AccountStatistics statistics();

    /**
     * Creates an account; the user module receives {@link UserRegisteredEvent} and creates the profile.
     */
    ManagedAccount create(UUID actorId, NewAccount account);

    ManagedAccount update(UUID actorId, UUID accountId, AccountChanges changes);

    /** Sets a temporary password and signs the user out everywhere. */
    ManagedAccount resetPassword(UUID actorId, UUID accountId, String temporaryPassword, boolean requireChange);

    /**
     * Deletes the account and, through {@link UserAccountDeleted}, every module's data of that user.
     *
     * @return the account as it was before deletion
     */
    ManagedAccount delete(UUID actorId, UUID accountId);

    /** Current database state, so a demoted or disabled administrator's still-valid token is not enough. */
    boolean isActiveAdministrator(UUID accountId);

    /**
     * @param text            case-insensitive "contains" match on the e-mail address, or {@code null}
     * @param alsoMatchingIds accounts that match {@code text} through data owned by other modules (display names)
     * @param createdBefore   exclusive upper bound
     */
    record AccountSearch(String text, Set<UUID> alsoMatchingIds, Role role, AccountStatus status,
                         Instant createdFrom, Instant createdBefore, int page, int size) {

        public AccountSearch {
            alsoMatchingIds = alsoMatchingIds == null ? Set.of() : Set.copyOf(alsoMatchingIds);
        }
    }

    record AccountPage(List<ManagedAccount> content, int page, int size, long totalElements) {
    }

    record NewAccount(String email, String displayName, Role role, AccountStatus status, String temporaryPassword,
                      boolean requirePasswordChange) {

        @Override
        public String toString() {
            return "NewAccount[email=" + email + ", role=" + role + ", status=" + status + ", password=***]";
        }
    }

    /** {@code null} fields are left unchanged. */
    record AccountChanges(String email, Role role, AccountStatus status) {
    }
}
