package one.nexus.permission.surface;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.AuditPort;
import one.nexus.common.trace.TraceIds;
import one.nexus.isolation.api.ExportFieldRule;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.RenderedSql;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.isolation.boundary.IsolatedExport;
import one.nexus.isolation.query.QueryCompiler;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.PermissionService;

/**
 * Product export path: {@link PermissionService#decide} on the export Task
 * type, then {@link IsolatedExport}. Isolation reject-before-sink is unchanged
 * and remains a distinct {@code P1.M02.019} from authorization {@code P2.PERM.*}.
 * Deny reuses {@link AuditPort#appendNewTransaction} when an audit port is
 * passed. Wizard begin / complete reuse {@link AuditPort#append} (same-TX join).
 * Same-TX {@code append} / {@code appendNewTransaction} rules are unchanged.
 */
public final class AuthorizedExport {
    public static final String WIZARD_SURFACE = "export.wizard";

    private AuthorizedExport() {}

    public static IsolatedEnvelope submit(PermissionService service, String resourceTenantId, String payload) {
        return submit(service, null, resourceTenantId, payload);
    }

    public static IsolatedEnvelope submit(
            PermissionService service, AuditPort audits, String resourceTenantId, String payload) {
        AuthorizedSubmit.export(service, audits);
        return IsolatedExport.submit(resourceTenantId, payload);
    }

    public static void dispatch(
            PermissionService service,
            IsolatedEnvelope envelope,
            String resourceTenantId,
            Consumer<IsolatedEnvelope> body
    ) {
        dispatch(service, null, envelope, resourceTenantId, body);
    }

    public static void dispatch(
            PermissionService service,
            AuditPort audits,
            IsolatedEnvelope envelope,
            String resourceTenantId,
            Consumer<IsolatedEnvelope> body
    ) {
        AuthorizedSubmit.export(service, audits);
        IsolatedExport.dispatch(envelope, resourceTenantId, body);
    }

    public static void writeFile(
            PermissionService service,
            String resourceTenantId,
            String relativePath,
            byte[] body,
            Consumer<String> sink
    ) {
        writeFile(service, null, resourceTenantId, relativePath, body, sink);
    }

    public static void writeFile(
            PermissionService service,
            AuditPort audits,
            String resourceTenantId,
            String relativePath,
            byte[] body,
            Consumer<String> sink
    ) {
        AuthorizedSubmit.export(service, audits);
        IsolatedExport.writeFile(resourceTenantId, relativePath, body, sink);
    }

    public static void cacheStatus(
            PermissionService service,
            String resourceTenantId,
            String env,
            String key,
            String value,
            BiConsumer<String, String> sink
    ) {
        cacheStatus(service, null, resourceTenantId, env, key, value, sink);
    }

    public static void cacheStatus(
            PermissionService service,
            AuditPort audits,
            String resourceTenantId,
            String env,
            String key,
            String value,
            BiConsumer<String, String> sink
    ) {
        AuthorizedSubmit.export(service, audits);
        IsolatedExport.cacheStatus(resourceTenantId, env, key, value, sink);
    }

    public static RenderedSql compile(
            PermissionService service,
            QueryCompiler compiler,
            QueryAst query,
            String resourceTenantId,
            Map<String, ExportFieldRule> policy,
            boolean secondAuthorizationGranted
    ) {
        AuthorizedSubmit.export(service, null);
        return IsolatedExport.compile(compiler, query, resourceTenantId, policy, secondAuthorizationGranted);
    }

    public static byte[] writeProjected(
            PermissionService service,
            String resourceTenantId,
            String relativePath,
            List<String> columns,
            List<Map<String, Object>> rows,
            Map<String, ExportFieldRule> policy,
            boolean secondAuthorizationGranted,
            BiConsumer<String, byte[]> sink
    ) {
        AuthorizedSubmit.export(service, null);
        return IsolatedExport.writeProjected(
                resourceTenantId, relativePath, columns, rows, policy, secondAuthorizationGranted, sink);
    }

    /**
     * HTTP export-wizard begin. Deny uses the existing SurfaceGuard
     * {@code appendNewTransaction} pair. Accepted begin uses {@code append}
     * so it joins an ambient Spring TX. Does not call IsolatedExport sinks.
     */
    public static void beginWizard(PermissionService service, AuditPort audits, String wizardId) {
        AuthorizedSubmit.export(service, audits);
        emitWizard(audits, AuditCaptures.EXPORT_BEGUN, wizardId, "begin");
    }

    /**
     * HTTP export-wizard complete. Guard + IsolatedExport.submit first, then
     * {@code p3.export.completed} via {@code append}. IsolatedExport already
     * emits {@code EXPORT_ACCEPTED} → {@code p3.export.submitted}.
     */
    public static IsolatedEnvelope completeWizard(
            PermissionService service, AuditPort audits, String resourceTenantId, String payload, String wizardId) {
        IsolatedEnvelope envelope = submit(service, audits, resourceTenantId, payload);
        emitWizard(audits, AuditCaptures.EXPORT_COMPLETED, wizardId, "complete");
        return envelope;
    }

    private static void emitWizard(AuditPort audits, String eventType, String wizardId, String step) {
        TenantContext context = TenantContextHolder.get();
        if (audits == null || context == null) {
            return;
        }
        String id = wizardId == null ? "" : wizardId;
        audits.append(AuditCaptures.export(
                eventType,
                true,
                context == null ? null : context.tenantId(),
                context == null ? null : context.personId(),
                IsolatedExport.TASK_TYPE,
                id,
                "wizard." + step,
                TraceIds.currentOrUnknown(),
                Map.of(
                        "step", step,
                        "wizard_id", id,
                        "surface", WIZARD_SURFACE)));
    }
}
