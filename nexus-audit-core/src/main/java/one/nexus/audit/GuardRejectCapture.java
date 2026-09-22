package one.nexus.audit;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Isolation-kernel reject shape without depending on {@code isolation-kernel}.
 * M0-02 emits {@code IsolationAuditEvent}; M0-11 persists via {@link AuditPort}.
 */
public record GuardRejectCapture(
        UUID eventId,
        Instant at,
        String errorCode,
        String actorId,
        String tenantId,
        String platformPath,
        String reason,
        String traceId,
        Map<String, String> details) {

    public GuardRejectCapture {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(at, "at");
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("errorCode is required");
        }
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId is required");
        }
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
