package one.nexus.common.task;

import java.util.Objects;
import java.util.UUID;

/**
 * Outbox / task-queue identity. Persistence and dispatcher are M0-09.
 */
public record OutboxMessage(UUID id, UUID tenantId, String messageType, String traceId) {
    public OutboxMessage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        if (messageType == null || messageType.isBlank()) {
            throw new IllegalArgumentException("messageType is required");
        }
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId is required");
        }
    }
}
