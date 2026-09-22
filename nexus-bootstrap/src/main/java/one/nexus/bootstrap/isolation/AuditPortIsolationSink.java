package one.nexus.bootstrap.isolation;

import java.util.Objects;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.AuditPort;
import one.nexus.audit.GuardRejectCapture;
import one.nexus.event.isolation.Slf4jIsolationAuditSink;
import one.nexus.isolation.api.IsolationAuditEvent;
import one.nexus.isolation.api.IsolationAuditSink;

/**
 * Persists isolation rejects through existing {@link AuditPort#appendNewTransaction}.
 * Does not change same-TX {@code append} joining (G4).
 */
public final class AuditPortIsolationSink implements IsolationAuditSink {
    private final AuditPort port;
    private final IsolationAuditSink log;

    public AuditPortIsolationSink(AuditPort port) {
        this(port, new Slf4jIsolationAuditSink());
    }

    public AuditPortIsolationSink(AuditPort port, IsolationAuditSink log) {
        this.port = Objects.requireNonNull(port, "port");
        this.log = log == null ? new Slf4jIsolationAuditSink() : log;
    }

    @Override
    public void record(IsolationAuditEvent event) {
        log.record(event);
        if (!IsolationAuditEvent.ISOLATION_REJECT.equals(event.type()) || event.error() == null) {
            return;
        }
        GuardRejectCapture capture = new GuardRejectCapture(
                event.eventId(),
                event.at(),
                event.error().code(),
                event.actorId(),
                event.tenantId(),
                event.platformPath(),
                event.reason(),
                event.traceId(),
                event.details());
        port.appendNewTransaction(AuditCaptures.guardReject(capture));
    }
}
