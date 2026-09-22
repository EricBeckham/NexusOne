package one.nexus.event;

import java.util.UUID;

/**
 * Transactional outbox write API. M0-10 notifications call {@link #append} in the
 * same local transaction as the business write, then register an
 * {@link OutboxHandler} for {@link OutboxAppendCommand#NOTIFY_INBOX}.
 */
public interface OutboxPort {
    UUID append(OutboxAppendCommand command);
}
