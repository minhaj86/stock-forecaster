package com.stockforecast.admin.domain;

import java.time.Instant;
import java.util.List;

/**
 * Append-only persistence port for the audit trail.
 */
public interface AuditEventStore {

    void append(AuditEvent event);

    AuditPage search(Criteria criteria);

    /**
     * @param actorEmailFragment lower-cased "contains" match on the actor's e-mail, or {@code null}
     * @param before             exclusive upper bound, or {@code null}
     */
    record Criteria(AuditAction action, AuditEntityType entityType, String entityId, String actorEmailFragment,
                    Instant from, Instant before, int page, int size) {
    }

    record AuditPage(List<AuditEvent> content, long totalElements) {
    }
}
