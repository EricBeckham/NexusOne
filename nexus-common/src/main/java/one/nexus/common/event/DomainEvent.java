package one.nexus.common.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import one.nexus.common.time.Utc;

/**
 * Domain event envelope. Outbox persistence and dispatch are M0-09.
 */
public record DomainEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        UUID tenantId,
        String aggregateType,
        UUID aggregateId,
        Instant occurredAt,
        String producer,
        UUID initiatorSubjectId,
        String traceId,
        String correlationId,
        String causationId) {
    public DomainEvent {
        Objects.requireNonNull(eventId, "eventId");
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType is required");
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId is required");
        }
    }

    public static Instant now() {
        return Utc.now();
    }
}
