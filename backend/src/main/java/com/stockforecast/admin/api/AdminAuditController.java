package com.stockforecast.admin.api;

import com.stockforecast.admin.application.AuditLog;
import com.stockforecast.admin.application.AuditLog.AuditQuery;
import com.stockforecast.admin.domain.AuditAction;
import com.stockforecast.admin.domain.AuditEntityType;
import com.stockforecast.admin.domain.AuditEvent;
import com.stockforecast.admin.domain.AuditEventStore.AuditPage;
import com.stockforecast.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Administration")
@RestController
@RequestMapping("/api/v1/admin/audit-log")
class AdminAuditController {

    private final AuditLog auditLog;

    AdminAuditController(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    @Operation(summary = "Audit trail of administrative actions, newest first")
    @GetMapping
    PageResponse<AuditEventResponse> search(@RequestParam(required = false) AuditAction action,
                                            @RequestParam(required = false) AuditEntityType entityType,
                                            @RequestParam(required = false) @Size(max = 64) String entityId,
                                            @RequestParam(required = false) @Size(max = 320) String actor,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        AuditPage result = auditLog.search(new AuditQuery(action, entityType, entityId, actor, from, to, page, size));
        return PageResponse.of(result.content(), page, size, result.totalElements(), AuditEventResponse::from);
    }

    record AuditEventResponse(UUID id, Instant occurredAt, Actor actor, AuditAction action,
                              AuditEntityType entityType, String entityId, String summary,
                              Map<String, Object> metadata, String correlationId) {

        static AuditEventResponse from(AuditEvent event) {
            return new AuditEventResponse(event.id(), event.occurredAt(),
                    new Actor(event.actorUserId(), event.actorEmail()), event.action(), event.entityType(),
                    event.entityId(), event.summary(), event.metadata(), event.correlationId());
        }
    }

    record Actor(UUID id, String email) {
    }
}
