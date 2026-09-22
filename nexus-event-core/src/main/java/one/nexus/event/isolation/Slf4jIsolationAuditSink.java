package one.nexus.event.isolation;

import one.nexus.isolation.api.IsolationAuditEvent;
import one.nexus.isolation.api.IsolationAuditSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Temporary sink until M0-11 persists isolation audit. Never logs secrets. */
public final class Slf4jIsolationAuditSink implements IsolationAuditSink {
    private static final Logger LOG = LoggerFactory.getLogger(Slf4jIsolationAuditSink.class);

    @Override
    public void record(IsolationAuditEvent event) {
        String code = event.error() == null ? "-" : event.error().code();
        LOG.info(
                "isolation_audit type={} code={} actor={} tenant={} path={} trace={}",
                event.type(),
                code,
                event.actorId(),
                event.tenantId(),
                event.platformPath(),
                event.traceId());
    }
}
