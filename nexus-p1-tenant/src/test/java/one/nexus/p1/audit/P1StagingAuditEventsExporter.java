package one.nexus.p1.audit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import one.nexus.p1.domain.AuditEvent;
import one.nexus.p1.port.AuditRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Harness export of P1 staging {@code GET /tenants/{id}/audit-events}.
 * Reads {@link AuditRepository} (staging {@code platform.audit_event} shape).
 * Does not switch the product GET onto M0-11 {@code AuditPort.findByTraceId}.
 */
public final class P1StagingAuditEventsExporter {

    public static final String SOURCE_TABLE = "platform.audit_event";
    public static final String QUERY_PATH = "GET /api/v1/p1/tenants/{id}/audit-events";
    public static final String LEFTOVER = "g4-p1-staging-audit-events";

    private final AuditRepository staging;
    private final ObjectMapper mapper;

    public P1StagingAuditEventsExporter(AuditRepository staging) {
        this(staging, new ObjectMapper());
    }

    public P1StagingAuditEventsExporter(AuditRepository staging, ObjectMapper mapper) {
        this.staging = Objects.requireNonNull(staging, "staging");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public List<AuditEvent> query(UUID tenantId, String eventType, int limit) {
        int capped = Math.min(Math.max(limit, 1), 200);
        if (eventType == null || eventType.isBlank()) {
            return staging.listByTenant(tenantId, capped);
        }
        return staging.listByType(tenantId, eventType, capped);
    }

    public Map<String, Object> exportBundle(UUID tenantId, String eventType, int limit) {
        List<AuditEvent> events = query(tenantId, eventType, limit);
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("leftover", LEFTOVER);
        bundle.put("source_table", SOURCE_TABLE);
        bundle.put("query_path", QUERY_PATH);
        bundle.put("switched_off_staging", false);
        bundle.put("product_g4_closed", false);
        bundle.put("http_export_wizard", false);
        bundle.put("tenant_id", tenantId.toString());
        bundle.put("event_type", eventType == null || eventType.isBlank() ? null : eventType);
        bundle.put("limit", Math.min(Math.max(limit, 1), 200));
        bundle.put("count", events.size());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AuditEvent event : events) {
            rows.add(row(event));
        }
        bundle.put("events", rows);
        return bundle;
    }

    public Path writeJson(Path destination, UUID tenantId, String eventType, int limit) throws IOException {
        Files.createDirectories(destination.getParent());
        String json = mapper.writeValueAsString(exportBundle(tenantId, eventType, limit));
        Files.writeString(destination, json + "\n", StandardCharsets.UTF_8);
        return destination;
    }

    static Map<String, Object> row(AuditEvent event) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("event_id", event.eventId().toString());
        row.put("event_type", event.eventType());
        row.put("event_version", event.eventVersion());
        row.put("occurred_at", event.occurredAt().toString());
        row.put("tenant_id", event.tenantId().toString());
        row.put("actor_subject_id", event.actorSubjectId() == null ? null : event.actorSubjectId().toString());
        row.put("actor_tenant_id", event.actorTenantId() == null ? null : event.actorTenantId().toString());
        row.put("platform_context", event.platformContext());
        row.put("reason", event.reason());
        row.put("trace_id", event.traceId());
        row.put("correlation_id", event.correlationId());
        row.put("causation_id", event.causationId());
        row.put("outcome", event.outcome());
        row.put("resource_type", event.resourceType());
        row.put("resource_id", event.resourceId());
        row.put("details", event.details());
        return row;
    }
}
