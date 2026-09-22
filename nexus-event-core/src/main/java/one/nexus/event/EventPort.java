package one.nexus.event;

import java.util.UUID;
import one.nexus.common.event.DomainEvent;

/**
 * Cross-module event entry. First-period implementation is local outbox + dispatcher.
 * Replacing the bus later must not change {@link DomainEvent}.
 */
public interface EventPort {
    UUID publish(DomainEvent event, String destination, String payload);
}
