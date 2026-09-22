package one.nexus.audit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import one.nexus.common.audit.AuditEvent;

/**
 * Append-only audit store. Reject-path writes must use
 * {@link #appendNewTransaction(AuditEvent)} so they survive a business rollback.
 *
 * <p>P1 still has a staging {@code platform.audit_event} table. Business modules
 * should switch to this port; until they do, end-to-end hash verification of a
 * real login→permission→data path is deferred.
 */
public interface AuditPort {
    ChainedAuditEvent append(AuditEvent event);

    ChainedAuditEvent appendNewTransaction(AuditEvent event);

    List<ChainedAuditEvent> findByTraceId(String traceId);

    List<ChainedAuditEvent> findByCorrelationId(String correlationId);

    Optional<ChainedAuditEvent> findByEventId(UUID eventId);

    List<ChainedAuditEvent> chain();

    HashChainVerification verifyChain();
}
