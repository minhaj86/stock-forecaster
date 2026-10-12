package com.stockforecast.admin.application;

import com.stockforecast.admin.domain.AuditAction;
import com.stockforecast.admin.domain.AuditEntityType;
import com.stockforecast.identity.AccountAdministration;
import com.stockforecast.identity.AccountAdministration.AccountChanges;
import com.stockforecast.identity.AccountAdministration.AccountPage;
import com.stockforecast.identity.AccountAdministration.AccountSearch;
import com.stockforecast.identity.AccountAdministration.NewAccount;
import com.stockforecast.identity.AccountStatistics;
import com.stockforecast.identity.AccountStatus;
import com.stockforecast.identity.AuthenticatedUser;
import com.stockforecast.identity.ManagedAccount;
import com.stockforecast.identity.Role;
import com.stockforecast.user.UserProfiles;
import com.stockforecast.user.UserProfiles.ProfileSummary;
import com.stockforecast.watchlist.WatchlistQueries;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * User administration: combines accounts (identity) with profiles (user) and watchlist counts, and audits every
 * change. Account rules and safeguards are enforced by the identity module.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserService {

    /** Display-name matches considered by a text search (refine the search beyond this). */
    static final int NAME_MATCH_LIMIT = 1000;

    private final AccountAdministration accounts;
    private final UserProfiles profiles;
    private final WatchlistQueries watchlists;
    private final AuditLog audit;

    public AdminUserService(AccountAdministration accounts, UserProfiles profiles, WatchlistQueries watchlists,
                            AuditLog audit) {
        this.accounts = accounts;
        this.profiles = profiles;
        this.watchlists = watchlists;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public UserPage search(UserQuery query) {
        String text = query.text() == null || query.text().isBlank() ? null : query.text().strip();
        Set<UUID> nameMatches = text == null ? Set.of()
                : new HashSet<>(profiles.findUserIdsByDisplayName(text, NAME_MATCH_LIMIT));
        Instant createdFrom = query.createdFrom() == null ? null
                : query.createdFrom().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant createdBefore = query.createdTo() == null ? null
                : query.createdTo().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        AccountPage page = accounts.search(new AccountSearch(text, nameMatches, query.role(), query.status(),
                createdFrom, createdBefore, query.page(), query.size()));
        Map<UUID, String> names = profiles.findDisplayNames(page.content().stream().map(ManagedAccount::id).toList());
        return new UserPage(page.content().stream().map(account -> new UserView(account, names.get(account.id())))
                .toList(), page.page(), page.size(), page.totalElements());
    }

    @Transactional(readOnly = true)
    public AccountStatistics statistics() {
        return accounts.statistics();
    }

    @Transactional(readOnly = true)
    public UserDetails get(UUID userId) {
        return details(accounts.get(userId));
    }

    @Transactional
    public UserDetails create(AuthenticatedUser actor, CreateUser command) {
        ManagedAccount created = accounts.create(actor.id(), new NewAccount(command.email(), command.displayName(),
                command.role(), command.status(), command.temporaryPassword(), command.requirePasswordChange()));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("email", created.email());
        metadata.put("role", created.role().name());
        metadata.put("status", created.status().name());
        metadata.put("requirePasswordChange", command.requirePasswordChange());
        audit.record(actor, AuditAction.USER_CREATED, AuditEntityType.USER, created.id().toString(),
                "Created %s (%s)".formatted(created.email(), created.role()), metadata);
        return details(created);
    }

    @Transactional
    public UserDetails update(AuthenticatedUser actor, UUID userId, UpdateUser command) {
        ManagedAccount before = accounts.get(userId);
        String nameBefore = profiles.find(userId).map(ProfileSummary::displayName).orElse(null);
        String nameAfter = nameBefore;
        if (command.displayName() != null && !command.displayName().strip().equals(nameBefore)) {
            nameAfter = profiles.changeDisplayName(userId, command.displayName()).displayName();
        }
        ManagedAccount after = accounts.update(actor.id(), userId,
                new AccountChanges(command.email(), command.role(), command.status()));
        auditChanges(actor, before, after, nameBefore, nameAfter);
        return details(after);
    }

    @Transactional
    public UserDetails changeStatus(AuthenticatedUser actor, UUID userId, AccountStatus status) {
        return update(actor, userId, new UpdateUser(null, null, null, status));
    }

    @Transactional
    public UserDetails resetPassword(AuthenticatedUser actor, UUID userId, String temporaryPassword,
                                     boolean requireChange) {
        ManagedAccount account = accounts.resetPassword(actor.id(), userId, temporaryPassword, requireChange);
        audit.record(actor, AuditAction.USER_PASSWORD_RESET, AuditEntityType.USER, userId.toString(),
                "Reset the password of " + account.email(), Map.of("requirePasswordChange", requireChange,
                        "sessionsRevoked", true));
        return details(account);
    }

    @Transactional
    public void delete(AuthenticatedUser actor, UUID userId) {
        int watchlistCount = watchlists.countWatchlists(userId);
        ManagedAccount deleted = accounts.delete(actor.id(), userId);
        audit.record(actor, AuditAction.USER_DELETED, AuditEntityType.USER, userId.toString(),
                "Deleted " + deleted.email(), Map.of("email", deleted.email(), "role", deleted.role().name(),
                        "watchlistsDeleted", watchlistCount));
    }

    private void auditChanges(AuthenticatedUser actor, ManagedAccount before, ManagedAccount after, String nameBefore,
                              String nameAfter) {
        String entityId = after.id().toString();
        if (before.role() != after.role()) {
            audit.record(actor, AuditAction.USER_ROLE_CHANGED, AuditEntityType.USER, entityId,
                    "Changed the role of %s from %s to %s".formatted(after.email(), before.role(), after.role()),
                    Map.of("previousRole", before.role().name(), "newRole", after.role().name()));
        }
        if (before.status() != after.status()) {
            AuditAction action = switch (after.status()) {
                case ACTIVE -> AuditAction.USER_ENABLED;
                case DISABLED -> AuditAction.USER_DISABLED;
                case LOCKED -> AuditAction.USER_LOCKED;
            };
            String verb = switch (after.status()) {
                case ACTIVE -> "Enabled";
                case DISABLED -> "Disabled";
                case LOCKED -> "Locked";
            };
            audit.record(actor, action, AuditEntityType.USER, entityId, verb + " " + after.email(),
                    Map.of("previousStatus", before.status().name(), "newStatus", after.status().name()));
        }
        Map<String, Object> changes = new LinkedHashMap<>();
        if (!before.email().equals(after.email())) {
            changes.put("email", Map.of("from", before.email(), "to", after.email()));
        }
        if (!Objects.equals(nameBefore, nameAfter)) {
            Map<String, Object> name = new LinkedHashMap<>();
            name.put("from", nameBefore);
            name.put("to", nameAfter);
            changes.put("displayName", name);
        }
        if (!changes.isEmpty()) {
            audit.record(actor, AuditAction.USER_UPDATED, AuditEntityType.USER, entityId,
                    "Updated " + String.join(" and ", changes.keySet()) + " of " + after.email(), changes);
        }
    }

    private UserDetails details(ManagedAccount account) {
        ProfileSummary profile = profiles.find(account.id()).orElse(null);
        return new UserDetails(new UserView(account, profile == null ? null : profile.displayName()),
                profile == null ? null : profile.timeZone(), watchlists.countWatchlists(account.id()));
    }

    /**
     * @param text      case-insensitive "contains" match on e-mail or display name
     * @param createdTo inclusive
     */
    public record UserQuery(String text, Role role, AccountStatus status, LocalDate createdFrom, LocalDate createdTo,
                            int page, int size) {
    }

    public record UserView(ManagedAccount account, String displayName) {
    }

    public record UserDetails(UserView user, String timeZone, int watchlistCount) {
    }

    public record UserPage(List<UserView> content, int page, int size, long totalElements) {
    }

    public record CreateUser(String displayName, String email, Role role, AccountStatus status,
                             String temporaryPassword, boolean requirePasswordChange) {

        @Override
        public String toString() {
            return "CreateUser[email=" + email + ", role=" + role + ", status=" + status + ", password=***]";
        }
    }

    /** {@code null} fields stay unchanged. */
    public record UpdateUser(String displayName, String email, Role role, AccountStatus status) {
    }
}
