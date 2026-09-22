package one.nexus.p1.port;

import one.nexus.p1.domain.AuditEvent;

/**
 * M0-11 will own hash-chained append-only audit. P1 writes the shared {@link AuditEvent} shape
 * through this port so the adapter can be swapped without changing tenant use cases.
 */
public interface AuditPort {

    void append(AuditEvent event);

    /** Reject-path audits must survive the business transaction rolling back. */
    void appendNewTransaction(AuditEvent event);
}
