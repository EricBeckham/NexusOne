package one.nexus.bootstrap.audit;

import java.util.Objects;
import one.nexus.audit.AuditCaptures;
import one.nexus.p1.domain.AuditEvent;

/**
 * Mirrors P1 staging writes onto the existing M0-11 hash chain. Does not replace
 * {@code platform.audit_event}. {@link #append} and {@link #appendNewTransaction}
 * keep the same-TX rules already on {@link one.nexus.audit.AuditPort}.
 */
final class HashChainedP1AuditPort implements one.nexus.p1.port.AuditPort {
    private final one.nexus.p1.port.AuditPort staging;
    private final one.nexus.audit.AuditPort chain;

    HashChainedP1AuditPort(one.nexus.p1.port.AuditPort staging, one.nexus.audit.AuditPort chain) {
        this.staging = Objects.requireNonNull(staging, "staging");
        this.chain = Objects.requireNonNull(chain, "chain");
    }

    @Override
    public void append(AuditEvent event) {
        staging.append(event);
        chain.append(toAdmin(event));
    }

    @Override
    public void appendNewTransaction(AuditEvent event) {
        staging.appendNewTransaction(event);
        chain.appendNewTransaction(toAdmin(event));
    }

    static one.nexus.common.audit.AuditEvent toAdmin(AuditEvent event) {
        boolean accepted = AuditEvent.OUTCOME_ACCEPTED.equals(event.outcome());
        return AuditCaptures.admin(
                event.eventType(),
                accepted,
                event.tenantId(),
                event.actorSubjectId(),
                event.actorTenantId(),
                event.resourceType(),
                event.resourceId(),
                event.reason(),
                event.traceId(),
                event.details());
    }
}
