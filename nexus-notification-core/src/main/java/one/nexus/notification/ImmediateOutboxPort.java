package one.nexus.notification;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import one.nexus.common.id.UuidV7;
import one.nexus.event.OutboxAppendCommand;
import one.nexus.event.OutboxHandler;
import one.nexus.event.OutboxPort;
import one.nexus.isolation.boundary.IsolatedEnvelope;

/**
 * Test-only in-process delivery. Invokes {@link OutboxHandler} on the calling
 * thread. Production uses M0-09 {@code OutboxDispatcher}.
 */
public final class ImmediateOutboxPort implements OutboxPort {
    private final List<OutboxHandler> handlers;

    public ImmediateOutboxPort(List<OutboxHandler> handlers) {
        this.handlers = List.copyOf(Objects.requireNonNull(handlers, "handlers"));
    }

    @Override
    public UUID append(OutboxAppendCommand command) {
        IsolatedEnvelope envelope = IsolatedEnvelope.fromCurrent(command.destination(), command.payload());
        OutboxHandler handler = handlers.stream()
                .filter(candidate -> candidate.supports(command.destination()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no outbox handler for " + command.destination()));
        handler.onDispatch(envelope);
        return UuidV7.generate();
    }
}
