package one.nexus.event;

import java.util.Objects;
import java.util.UUID;

/**
 * Command written inside the business transaction. Tenant/trace come from the
 * bound M0-02 context, never from this command.
 */
public record OutboxAppendCommand(
        String destination,
        String eventType,
        int eventVersion,
        String payload,
        String aggregateType,
        UUID aggregateId,
        String correlationId,
        String causationId
) {
    public static final String NOTIFY_INBOX = "notify.inbox";

    public OutboxAppendCommand {
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("destination is required");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType is required");
        }
        Objects.requireNonNull(payload, "payload");
        if (eventVersion < 1) {
            throw new IllegalArgumentException("eventVersion must be >= 1");
        }
    }

    public static OutboxAppendCommand notifyInbox(String eventType, String payload) {
        return new OutboxAppendCommand(NOTIFY_INBOX, eventType, 1, payload, null, null, null, null);
    }
}
