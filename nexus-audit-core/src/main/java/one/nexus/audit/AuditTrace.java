package one.nexus.audit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import one.nexus.common.audit.AuditCategory;
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
}
