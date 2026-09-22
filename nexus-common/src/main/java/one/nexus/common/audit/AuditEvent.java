package one.nexus.common.audit;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Audit record shape. Hash-chain append-only store is M0-11.
 */
public record AuditEvent(
        UUID id,
        Instant occurredAt,
        String action,
        UUID tenantId,
        UUID actorSubjectId,
        String resourceType,
        String resourceId,
        String result,
        String traceId) {
    public AuditEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action is required");
        }
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId is required");
        }
    }
}
