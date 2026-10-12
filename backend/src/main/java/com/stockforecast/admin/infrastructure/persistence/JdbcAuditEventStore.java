package com.stockforecast.admin.infrastructure.persistence;

import com.stockforecast.admin.domain.AuditAction;
import com.stockforecast.admin.domain.AuditEntityType;
import com.stockforecast.admin.domain.AuditEvent;
import com.stockforecast.admin.domain.AuditEventStore;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only JDBC store; metadata is stored as JSONB.
 */
@Repository
class JdbcAuditEventStore implements AuditEventStore {

    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper jsonMapper;

    JdbcAuditEventStore(NamedParameterJdbcTemplate jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void append(AuditEvent event) {
        jdbc.update("""
                        INSERT INTO admin_audit_events (id, occurred_at, actor_user_id, actor_email, action, entity_type,
                                                        entity_id, summary, metadata, correlation_id)
                        VALUES (:id, :occurredAt, :actorUserId, :actorEmail, :action, :entityType, :entityId, :summary,
                                CAST(:metadata AS jsonb), :correlationId)""",
                new MapSqlParameterSource()
                        .addValue("id", event.id())
                        .addValue("occurredAt", Timestamp.from(event.occurredAt()))
                        .addValue("actorUserId", event.actorUserId())
                        .addValue("actorEmail", event.actorEmail())
                        .addValue("action", event.action().name())
                        .addValue("entityType", event.entityType().name())
                        .addValue("entityId", event.entityId())
                        .addValue("summary", event.summary())
                        .addValue("metadata", jsonMapper.writeValueAsString(event.metadata()))
                        .addValue("correlationId", event.correlationId()));
    }

    @Override
    public AuditPage search(Criteria criteria) {
        StringBuilder where = new StringBuilder(" WHERE TRUE");
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (criteria.action() != null) {
            where.append(" AND action = :action");
            params.addValue("action", criteria.action().name());
        }
        if (criteria.entityType() != null) {
            where.append(" AND entity_type = :entityType");
            params.addValue("entityType", criteria.entityType().name());
        }
        if (criteria.entityId() != null) {
            where.append(" AND entity_id = :entityId");
            params.addValue("entityId", criteria.entityId());
        }
        if (criteria.actorEmailFragment() != null) {
            where.append(" AND lower(actor_email) LIKE :actor ESCAPE '\\'");
            params.addValue("actor", "%" + escapeLike(criteria.actorEmailFragment()) + "%");
        }
        if (criteria.from() != null) {
            where.append(" AND occurred_at >= :from");
            params.addValue("from", Timestamp.from(criteria.from()));
        }
        if (criteria.before() != null) {
            where.append(" AND occurred_at < :before");
            params.addValue("before", Timestamp.from(criteria.before()));
        }
        Long total = jdbc.queryForObject("SELECT count(*) FROM admin_audit_events" + where, params, Long.class);
        params.addValue("limit", criteria.size()).addValue("offset", (long) criteria.page() * criteria.size());
        String select = "SELECT id, occurred_at, actor_user_id, actor_email, action, entity_type, entity_id, summary,"
                + " metadata::text AS metadata, correlation_id FROM admin_audit_events" + where
                + " ORDER BY occurred_at DESC, id DESC LIMIT :limit OFFSET :offset";
        List<AuditEvent> content = new ArrayList<>(jdbc.query(select, params, (rs, row) -> toEvent(rs)));
        return new AuditPage(content, total == null ? 0 : total);
    }

    private AuditEvent toEvent(ResultSet rs) throws SQLException {
        return new AuditEvent(rs.getObject("id", UUID.class), rs.getTimestamp("occurred_at").toInstant(),
                rs.getObject("actor_user_id", UUID.class), rs.getString("actor_email"),
                AuditAction.valueOf(rs.getString("action")), AuditEntityType.valueOf(rs.getString("entity_type")),
                rs.getString("entity_id"), rs.getString("summary"), metadata(rs.getString("metadata")),
                rs.getString("correlation_id"));
    }

    private Map<String, Object> metadata(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return jsonMapper.readValue(json, METADATA_TYPE);
        } catch (JacksonException e) {
            return Map.of("unreadable", true);
        }
    }

    private static String escapeLike(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
