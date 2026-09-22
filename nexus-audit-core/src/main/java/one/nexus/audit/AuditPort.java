package one.nexus.audit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import one.nexus.common.audit.AuditEvent;

/**
 * Append-only audit store.
 *
 * <p>{@link #append(AuditEvent)} joins an ambient Spring / JDBC transaction when
 * the JDBC adapter shares the caller's DataSource. G18 submit/approve (and other
 * in-path writes) must use this method so a business rollback also rolls back
 * the hash-chain INSERT and {@code audit_chain_meta} head.
 *
 * <p>Reject-path writes must use {@link #appendNewTransaction(AuditEvent)} so
 * they survive a business rollback. That method commits privately and must not
 * be called from a transaction that already holds the chain lock via
 * {@link #append(AuditEvent)}.
 *
 * <p>P1 still has a staging {@code platform.audit_event} table. G4 coverage
 * writes login / permission-change / admin / guard-reject through this port
 * onto {@code platform.audit_record}. P1 staging stays as-is (M0-03 GET is
 * not switched). Do not add a second audit store.
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
