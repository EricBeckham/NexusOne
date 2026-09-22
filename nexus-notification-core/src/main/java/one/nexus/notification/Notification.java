package one.nexus.notification;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Notification(
        UUID id,
        UUID tenantId,
        String recipientSubject,
        String templateCode,
        String title,
        String body,
        String payloadJson,
        NotificationStatus status,
        UUID outboxMessageId,
        Instant createdAt,
        Instant readAt) {
    public Notification {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        if (recipientSubject == null || recipientSubject.isBlank()) {
            throw new IllegalArgumentException("recipientSubject is required");
        }
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public Notification withOutbox(UUID messageId) {
        return new Notification(
                id, tenantId, recipientSubject, templateCode, title, body, payloadJson,
                status, messageId, createdAt, readAt);
    }

    public Notification delivered() {
        return new Notification(
                id, tenantId, recipientSubject, templateCode, title, body, payloadJson,
                NotificationStatus.DELIVERED, outboxMessageId, createdAt, readAt);
    }

    public Notification read(Instant at) {
        return new Notification(
                id, tenantId, recipientSubject, templateCode, title, body, payloadJson,
                NotificationStatus.READ, outboxMessageId, createdAt, at);
    }
}
