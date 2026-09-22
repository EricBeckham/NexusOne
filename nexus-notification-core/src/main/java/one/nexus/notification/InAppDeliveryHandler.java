package one.nexus.notification;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import one.nexus.event.IdempotencyStore;
import one.nexus.event.OutboxAppendCommand;
import one.nexus.event.OutboxHandler;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.isolation.boundary.SideEffectGuard;
import one.nexus.isolation.context.AccessContextHolder;

/**
 * M0-09 dispatcher target for {@link OutboxAppendCommand#NOTIFY_INBOX}.
 * Marks the inbox row delivered. Context is already restored by the dispatcher.
 */
public final class InAppDeliveryHandler implements OutboxHandler {
    private static final Pattern ID = Pattern.compile("\"notification_id\":\"([0-9a-fA-F-]{36})\"");
    private final NotificationRepository repository;
    private final IdempotencyStore idempotency;

    public InAppDeliveryHandler(NotificationRepository repository) {
        this(repository, null);
    }

    public InAppDeliveryHandler(NotificationRepository repository, IdempotencyStore idempotency) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.idempotency = idempotency;
    }

    @Override
    public boolean supports(String destination) {
        return OutboxAppendCommand.NOTIFY_INBOX.equals(destination);
    }

    @Override
    public void onDispatch(IsolatedEnvelope envelope) {
        SideEffectGuard.assertSameTenant(AccessContextHolder.current(), envelope.tenantId());
        Matcher matcher = ID.matcher(envelope.payload());
        if (!matcher.find()) {
            throw new IllegalArgumentException("notification_id missing from outbox payload");
        }
        UUID id = UUID.fromString(matcher.group(1));
        if (idempotency != null
                && idempotency.claim(
                        OutboxAppendCommand.NOTIFY_INBOX,
                        envelope.traceId() + ":" + id,
                        envelope.tenantId(),
                        envelope.traceId()) == IdempotencyStore.Claim.DUPLICATE) {
            return;
        }
        Notification notification = repository.find(id)
                .orElseThrow(() -> new IllegalStateException("notification missing: " + id));
        repository.update(notification.delivered());
    }
}
