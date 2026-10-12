package com.stockforecast.admin.application;

import com.stockforecast.admin.domain.AuditAction;
import com.stockforecast.admin.domain.AuditEntityType;
import com.stockforecast.admin.domain.AuditEvent;
import com.stockforecast.admin.domain.AuditEventStore;
import com.stockforecast.identity.AuthenticatedUser;
import com.stockforecast.shared.domain.Identifiers;
import com.stockforecast.shared.web.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Records administrative actions in the caller's transaction (the change and its audit entry commit together) and
 * searches them. Metadata keys that look like credentials are dropped defensively.
 */
@Service
public class AuditLog {

    /** Keys naming a credential (e.g. temporaryPassword, accessToken, apiKey); flags such as requirePasswordChange stay. */
    private static final Pattern SECRET_KEY =
            Pattern.compile("(?i).*(password|secret|token|api[-_]?key|credentials?)");

    private final AuditEventStore store;
    private final Clock clock;

    public AuditLog(AuditEventStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Transactional
    public void record(AuthenticatedUser actor, AuditAction action, AuditEntityType entityType, String entityId,
                       String summary, Map<String, ?> metadata) {
        store.append(new AuditEvent(Identifiers.newId(), clock.instant(), actor.id(), actor.email(), action,
                entityType, entityId, summary, sanitize(metadata), MDC.get(CorrelationIdFilter.MDC_KEY)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public AuditEventStore.AuditPage search(AuditQuery query) {
        Instant from = query.from() == null ? null : query.from().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant before = query.to() == null ? null : query.to().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        String actor = query.actor() == null || query.actor().isBlank() ? null
                : query.actor().strip().toLowerCase(Locale.ROOT);
        String entityId = query.entityId() == null || query.entityId().isBlank() ? null : query.entityId().strip();
        return store.search(new AuditEventStore.Criteria(query.action(), query.entityType(), entityId, actor, from,
                before, query.page(), query.size()));
    }

    static Map<String, Object> sanitize(Map<String, ?> metadata) {
        Map<String, Object> clean = new LinkedHashMap<>();
        if (metadata == null) {
            return clean;
        }
        metadata.forEach((key, value) -> {
            if (key != null && value != null && !SECRET_KEY.matcher(key).matches()) {
                clean.put(key, sanitizeValue(value));
            }
        });
        return clean;
    }

    private static Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> nested) {
            Map<String, Object> converted = new LinkedHashMap<>();
            nested.forEach((key, inner) -> converted.put(String.valueOf(key), inner));
            return sanitize(converted);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(item -> item == null ? null : sanitizeValue(item)).toList();
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof String) {
            return value;
        }
        return value.toString();
    }

    /**
     * @param from inclusive (UTC day), {@code to} inclusive
     */
    public record AuditQuery(AuditAction action, AuditEntityType entityType, String entityId, String actor,
                             LocalDate from, LocalDate to, int page, int size) {
    }
}
