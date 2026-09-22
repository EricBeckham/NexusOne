package one.nexus.isolation.api;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Structured isolation audit. M0-11 persists; this kernel only emits. */
public record IsolationAuditEvent(
        UUID eventId,
        Instant at,
        String type,
        IsolationErrorCode error,
        String actorId,
        String tenantId,
        String platformPath,
        String reason,
        String traceId,
        Map<String, String> details
) {
    public IsolationAuditEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(at, "at");
        type = Actor.requireToken(type, "type");
        traceId = Actor.requireToken(traceId, "traceId");
        details = Map.copyOf(details == null ? Map.of() : details);
    }

    public static final String PLATFORM_CONTEXT_ISSUED = "PLATFORM_CONTEXT_ISSUED";
    public static final String TENANT_CONTEXT_ISSUED = "TENANT_CONTEXT_ISSUED";
    public static final String ISOLATION_REJECT = "ISOLATION_REJECT";
    public static final String EXPORT_ACCEPTED = "EXPORT_ACCEPTED";
}
