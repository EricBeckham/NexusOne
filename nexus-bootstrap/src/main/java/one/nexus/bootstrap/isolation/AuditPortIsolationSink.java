package one.nexus.bootstrap.isolation;

import java.util.Objects;
import java.util.UUID;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.AuditPort;
import one.nexus.audit.GuardRejectCapture;
import one.nexus.event.isolation.Slf4jIsolationAuditSink;
import one.nexus.isolation.api.IsolationAuditEvent;
import one.nexus.isolation.api.IsolationAuditSink;
import one.nexus.isolation.boundary.IsolatedExport;

/**
 * Persists isolation rejects through existing {@link AuditPort#appendNewTransaction}
 * and accepted export actions through {@link AuditPort#append} (same-TX join).
 * Does not change those two methods' commit rules.
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
        if (IsolationAuditEvent.ISOLATION_REJECT.equals(event.type()) && event.error() != null) {
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
            return;
        }
        if (IsolationAuditEvent.EXPORT_ACCEPTED.equals(event.type())) {
            String action = event.details() == null ? "submit" : event.details().getOrDefault("action", "submit");
            String eventType = switch (action) {
                case "writeFile" -> AuditCaptures.EXPORT_FILE_WRITTEN;
                case "dispatch" -> AuditCaptures.EXPORT_DISPATCHED;
                default -> AuditCaptures.EXPORT_SUBMITTED;
            };
            String resourceId = event.details() == null ? IsolatedExport.TASK_TYPE
                    : event.details().getOrDefault("resourceId", IsolatedExport.TASK_TYPE);
            port.append(AuditCaptures.export(
                    eventType,
                    true,
                    parseUuid(event.tenantId()),
                    parseUuid(event.actorId()),
                    "export",
                    resourceId,
                    event.reason(),
                    event.traceId(),
                    event.details()));
        }
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
