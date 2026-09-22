package one.nexus.task;

import java.time.Instant;
import java.util.UUID;
import one.nexus.event.isolation.StoredMembership;
import one.nexus.isolation.boundary.IsolatedEnvelope;

public record TaskRecord(
        UUID id,
        StoredMembership membership,
        String traceId,
        String taskType,
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
        return new IsolatedEnvelope(membership.tenantId(), traceId, taskType, payload);
    }

    public String tenantId() {
        return membership.tenantId();
    }
}
