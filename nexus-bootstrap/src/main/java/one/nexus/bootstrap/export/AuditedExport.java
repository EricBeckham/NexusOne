package one.nexus.bootstrap.export;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.AuditPort;
import one.nexus.common.trace.TraceIds;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.isolation.boundary.IsolatedExport;
import one.nexus.isolation.context.AccessContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Product export surface: G1 {@link IsolatedExport} first, then
 * {@link AuditPort#append} so accepted export operations land on the existing
 * hash chain and are findable by trace. Cross-tenant rejects still go through
 * {@code IsolationAuditor} → {@code appendNewTransaction} (unchanged).
 *
 * <p>Details are ids / codes / byte counts only. Field values are not logged.
 */
@Service
public class AuditedExport {
    private final AuditPort audits;

    public AuditedExport(AuditPort audits) {
        this.audits = Objects.requireNonNull(audits, "audits");
    }

    @Transactional
    public IsolatedEnvelope submit(String resourceTenantId, String payload) {
        IsolatedEnvelope envelope = IsolatedExport.submit(resourceTenantId, payload);
        append(
                AuditCaptures.EXPORT_SUBMITTED,
                resourceTenantId,
                "export_task",
                IsolatedExport.TASK_TYPE,
                "export submitted",
                Map.of(
                        "task_type", IsolatedExport.TASK_TYPE,
                        "payload_bytes", String.valueOf(payload == null ? 0 : payload.length())));
        return envelope;
    }

    @Transactional
    public String writeFile(String resourceTenantId, String relativePath, byte[] body) {
        AtomicReference<String> key = new AtomicReference<>();
        IsolatedExport.writeFile(resourceTenantId, relativePath, body, key::set);
        append(
                AuditCaptures.EXPORT_FILE_WRITTEN,
                resourceTenantId,
                "export_file",
                IsolatedExport.TASK_TYPE,
                "export file written",
                Map.of(
                        "relative_path", relativePath == null ? "" : relativePath,
                        "object_key", key.get() == null ? "" : key.get(),
                        "bytes", String.valueOf(body == null ? 0 : body.length)));
        return key.get();
    }

    @Transactional
    public void dispatch(IsolatedEnvelope envelope, String resourceTenantId, Consumer<IsolatedEnvelope> body) {
        IsolatedExport.dispatch(envelope, resourceTenantId, body);
        append(
                AuditCaptures.EXPORT_DISPATCHED,
                resourceTenantId,
                "export_task",
                IsolatedExport.TASK_TYPE,
                "export dispatched",
                Map.of("task_type", IsolatedExport.TASK_TYPE));
    }

    private void append(
            String eventType,
            String resourceTenantId,
            String resourceType,
            String resourceId,
            String reason,
            Map<String, ?> details) {
        AccessContext context = AccessContextHolder.currentOrNull();
        String trace = context == null ? TraceIds.currentOrUnknown() : context.traceId();
        audits.append(AuditCaptures.export(
                eventType,
                true,
                parseUuid(resourceTenantId),
                context == null ? null : parseUuid(context.actor().actorId()),
                resourceType,
                resourceId,
                reason,
                trace,
                details));
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
