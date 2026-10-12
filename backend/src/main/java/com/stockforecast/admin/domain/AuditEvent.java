package com.stockforecast.admin.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One audited administrative action. Immutable; the actor's e-mail is a snapshot taken when the action happened.
 *
 * @param metadata client-safe details (never passwords, tokens or keys)
 */
public record AuditEvent(UUID id, Instant occurredAt, UUID actorUserId, String actorEmail, AuditAction action,
                         AuditEntityType entityType, String entityId, String summary, Map<String, Object> metadata,
                         String correlationId) {

    public static final int MAX_SUMMARY_LENGTH = 300;

    public AuditEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(actorUserId, "actorUserId");
        Objects.requireNonNull(actorEmail, "actorEmail");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(summary, "summary");
        if (summary.length() > MAX_SUMMARY_LENGTH) {
            summary = summary.substring(0, MAX_SUMMARY_LENGTH - 1) + "…";
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
