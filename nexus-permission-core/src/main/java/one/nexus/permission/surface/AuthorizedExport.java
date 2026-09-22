package one.nexus.permission.surface;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import one.nexus.isolation.api.ExportFieldRule;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.RenderedSql;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.isolation.boundary.IsolatedExport;
import one.nexus.isolation.query.QueryCompiler;
import one.nexus.permission.PermissionService;

/**
 * Product export path: {@link PermissionService#decide} on the export Task
 * type, then {@link IsolatedExport}. Isolation reject-before-sink is unchanged
 * and remains a distinct {@code P1.M02.019} from authorization {@code P2.PERM.*}.
 * Does not change AuditPort.
 */
public final class AuthorizedExport {
    private AuthorizedExport() {}

    public static IsolatedEnvelope submit(PermissionService service, String resourceTenantId, String payload) {
        AuthorizedSubmit.export(service);
        return IsolatedExport.submit(resourceTenantId, payload);
    }

    public static void dispatch(
            PermissionService service,
            IsolatedEnvelope envelope,
            String resourceTenantId,
            Consumer<IsolatedEnvelope> body
    ) {
        AuthorizedSubmit.export(service);
        IsolatedExport.dispatch(envelope, resourceTenantId, body);
    }

    public static void writeFile(
            PermissionService service,
            String resourceTenantId,
            String relativePath,
            byte[] body,
            Consumer<String> sink
    ) {
        AuthorizedSubmit.export(service);
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
        AuthorizedSubmit.export(service);
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
        AuthorizedSubmit.export(service);
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
        AuthorizedSubmit.export(service);
        return IsolatedExport.writeProjected(
                resourceTenantId, relativePath, columns, rows, policy, secondAuthorizationGranted, sink);
    }
}
