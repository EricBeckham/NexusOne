package one.nexus.event;

import java.time.Instant;
import java.util.UUID;
import one.nexus.event.isolation.StoredMembership;
import one.nexus.isolation.boundary.IsolatedEnvelope;

public record OutboxRecord(
        UUID id,
        StoredMembership membership,
        String traceId,
        String destination,
        String eventType,
        int eventVersion,
        String aggregateType,
        UUID aggregateId,
        String correlationId,
        String causationId,
        String payload,
        String status,
        int attempt,
        int maxAttempts,
        Instant availableAt,
        Instant leaseUntil,
        String lastError,
        Instant createdAt
) {
    public IsolatedEnvelope envelope() {
        return new IsolatedEnvelope(membership.tenantId(), traceId, destination, payload);
    }

    public String tenantId() {
        return membership.tenantId();
    }
}
