package one.nexus.event;

import one.nexus.isolation.boundary.IsolatedEnvelope;

/**
 * Consumer invoked by {@link OutboxDispatcher} after M0-02 context restore.
 * Handlers must be idempotent via {@link IdempotencyStore}.
 */
public interface OutboxHandler {
    boolean supports(String destination);

    void onDispatch(IsolatedEnvelope envelope);
}
