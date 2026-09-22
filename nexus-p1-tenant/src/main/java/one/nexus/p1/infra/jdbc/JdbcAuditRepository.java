package one.nexus.p1.infra.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import one.nexus.p1.domain.AuditEvent;
import one.nexus.p1.port.AuditRepository;
import one.nexus.p1.port.TableAccessPlan;

@Repository
@DependsOn("flyway")
public class JdbcAuditRepository implements AuditRepository {

    static final TableAccessPlan PLAN = TableAccessPlan.platform("platform.audit_event");

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public JdbcAuditRepository(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public void insert(AuditEvent event) {
        jdbc.sql("""
                        INSERT INTO platform.audit_event (
                          event_id, event_type, event_version, occurred_at, tenant_id,
                          actor_subject_id, actor_tenant_id, platform_context, reason,
                          trace_id, correlation_id, causation_id, outcome, resource_type, resource_id, details)
                        VALUES (
                          :event_id, :event_type, :event_version, :occurred_at, :tenant_id,
                          :actor_subject_id, :actor_tenant_id, :platform_context, :reason,
                          :trace_id, :correlation_id, :causation_id, :outcome, :resource_type, :resource_id,
                          CAST(:details AS jsonb))
                        """)
                .param("event_id", event.eventId())
                .param("event_type", event.eventType())
                .param("event_version", event.eventVersion())
                .param("occurred_at", JdbcValues.timestamp(event.occurredAt()))
                .param("tenant_id", event.tenantId())
                .param("actor_subject_id", event.actorSubjectId())
                .param("actor_tenant_id", event.actorTenantId())
                .param("platform_context", event.platformContext())
                .param("reason", event.reason())
                .param("trace_id", event.traceId())
                .param("correlation_id", event.correlationId())
                .param("causation_id", event.causationId())
                .param("outcome", event.outcome())
                .param("resource_type", event.resourceType())
                .param("resource_id", event.resourceId())
                .param("details", json(event.details()))
                .update();
    }

    @Override
    public List<AuditEvent> listByTenant(UUID tenantId, int limit) {
        return jdbc.sql("""
                        SELECT * FROM platform.audit_event
                        WHERE tenant_id = :tenant_id
                        ORDER BY occurred_at DESC
                        LIMIT :limit
                        """)
                .param("tenant_id", tenantId)
                .param("limit", limit)
                .query(this::map)
                .list();
    }

    @Override
    public List<AuditEvent> listByType(UUID tenantId, String eventType, int limit) {
        return jdbc.sql("""
                        SELECT * FROM platform.audit_event
                        WHERE tenant_id = :tenant_id AND event_type = :event_type
                        ORDER BY occurred_at DESC
                        LIMIT :limit
                        """)
                .param("tenant_id", tenantId)
                .param("event_type", eventType)
                .param("limit", limit)
                .query(this::map)
                .list();
    }

    private AuditEvent map(ResultSet rs, int rowNum) throws SQLException {
        return new AuditEvent(
                JdbcValues.uuid(rs, "event_id"),
                rs.getString("event_type"),
                rs.getInt("event_version"),
                JdbcValues.instant(rs, "occurred_at"),
                JdbcValues.uuid(rs, "tenant_id"),
                JdbcValues.uuid(rs, "actor_subject_id"),
                JdbcValues.uuid(rs, "actor_tenant_id"),
                rs.getBoolean("platform_context"),
                rs.getString("reason"),
                rs.getString("trace_id"),
                rs.getString("correlation_id"),
                rs.getString("causation_id"),
                rs.getString("outcome"),
                rs.getString("resource_type"),
                rs.getString("resource_id"),
                readMap(rs.getString("details")));
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("json write failed", ex);
        }
    }

    private java.util.Map<String, Object> readMap(String json) {
        try {
            if (json == null || json.isBlank()) {
                return java.util.Map.of();
            }
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (JacksonException ex) {
            throw new IllegalStateException("json read failed", ex);
        }
    }
}
