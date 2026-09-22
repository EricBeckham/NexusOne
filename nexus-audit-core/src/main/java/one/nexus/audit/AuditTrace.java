package one.nexus.audit;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import one.nexus.common.audit.AuditCategory;
import one.nexus.common.audit.AuditEvent;
import one.nexus.common.trace.TraceIds;

/**
 * Trace-wide correlation helpers compatible with M0-01 {@link TraceIds}
 * ({@code X-Trace-Id}, MDC {@code traceId}/{@code trace_id}, W3C {@code traceparent}).
 *
 * <p>Full cross-module reconstruction of a live business request is deferred
 * until P1/P2/P3/isolation emit through {@link AuditPort}.
 */
public final class AuditTrace {
    private AuditTrace() {}

    public static Map<String, String> logFields(ChainedAuditEvent linked) {
        Map<String, String> fields = new LinkedHashMap<>(TraceIds.logFields(
                linked.event().traceId(), linked.event().correlationId()));
        fields.put("audit_category", linked.event().category().name());
        fields.put("audit_event_type", linked.event().eventType());
        fields.put("audit_chain_index", Long.toString(linked.chainIndex()));
        return Map.copyOf(fields);
    }

    public static List<ChainedAuditEvent> correlate(AuditPort port, String traceId) {
        Objects.requireNonNull(port, "port");
        if (traceId == null || traceId.isBlank()) {
            return List.of();
        }
        return port.findByTraceId(traceId);
    }

    public static List<AuditCategory> categories(List<ChainedAuditEvent> events) {
        List<AuditCategory> categories = new ArrayList<>();
        for (ChainedAuditEvent event : events) {
            categories.add(event.event().category());
        }
        return List.copyOf(categories);
    }

    /**
     * Read-only JSON for {@code GET /api/v1/audit/traces/{traceId}}. Events whose
     * {@code tenant_id} does not match the issued context are omitted (no leak).
     */
    public static Map<String, Object> jsonForTenant(String traceId, UUID tenantId, List<ChainedAuditEvent> events) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (tenantId != null && events != null) {
            HexFormat hex = HexFormat.of();
            for (ChainedAuditEvent linked : events) {
                AuditEvent event = linked.event();
                if (!tenantId.equals(event.tenantId())) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("chain_index", linked.chainIndex());
                row.put("event_id", event.eventId().toString());
                row.put("event_type", event.eventType());
                row.put("category", event.category().name());
                row.put("occurred_at", event.occurredAt().toString());
                row.put("outcome", event.outcome());
                row.put("resource_type", event.resourceType());
                row.put("resource_id", event.resourceId());
                row.put("reason", event.reason());
                row.put("details", event.details());
                row.put("event_hash", hex.formatHex(linked.eventHash()));
                row.put("previous_hash", hex.formatHex(linked.previousHash()));
                row.put("cipher_suite", linked.suite().name());
                items.add(row);
            }
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("trace_id", traceId);
        json.put("tenant_id", tenantId == null ? null : tenantId.toString());
        json.put("events", List.copyOf(items));
        return json;
    }
}
