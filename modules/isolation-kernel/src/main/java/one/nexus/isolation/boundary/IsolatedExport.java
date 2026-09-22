package one.nexus.isolation.boundary;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.ExportFieldRule;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.RenderedSql;
import one.nexus.isolation.audit.IsolationAuditorHolder;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.QueryCompiler;

/**
 * Product export surface. Query compile, file write, cache status, and Task
 * submit all go through {@link ExportGuard} plus the existing M0-02/09/10
 * guards. FieldPolicy is required on {@link #compile} / {@link #writeProjected}.
 * Hidden Query AST columns are dropped before compile / file write;
 * second-auth without grant still rejects as {@code P1.M02.021}. Accepted
 * submit / writeFile / dispatch emit {@code EXPORT_ACCEPTED}. No second
 * tenant holder.
 */
public final class IsolatedExport {
    public static final String TASK_TYPE = "ledger.export.csv";
    public static final String CACHE_MODULE = "export";

    private IsolatedExport() {}

    public static RenderedSql compile(
            QueryCompiler compiler,
            QueryAst query,
            String resourceTenantId,
            Map<String, ExportFieldRule> policy,
            boolean secondAuthorizationGranted
    ) {
        Objects.requireNonNull(compiler, "compiler");
        Objects.requireNonNull(query, "query");
        AccessContext current = AccessContextHolder.currentOrNull();
        ExportGuard.assertExportTenant(current, resourceTenantId);
        ExportFieldPolicyGuard.Projection projected = ExportFieldPolicyGuard.dropHidden(
                current, query.projection(), policy, secondAuthorizationGranted);
        return compiler.compile(query.withProjection(projected.visible()), current);
    }

    public static byte[] writeProjected(
            String resourceTenantId,
            String relativePath,
            List<String> columns,
            List<Map<String, Object>> rows,
            Map<String, ExportFieldRule> policy,
            boolean secondAuthorizationGranted,
            BiConsumer<String, byte[]> sink
    ) {
        AccessContext current = AccessContextHolder.currentOrNull();
        ExportGuard.assertExportTenant(current, resourceTenantId);
        List<String> visible = ExportFieldPolicyGuard.dropHidden(
                current, columns, policy, secondAuthorizationGranted).visible();
        byte[] body = csv(visible, rows);
        FileObjectKeyGuard.putObject(relativePath, body, key -> sink.accept(key, body));
        return body;
    }

    public static IsolatedEnvelope submit(String resourceTenantId, String payload) {
        AccessContext current = AccessContextHolder.currentOrNull();
        ExportGuard.assertExportTenant(current, resourceTenantId);
        IsolatedEnvelope envelope = IsolatedEnvelope.fromCurrent(TASK_TYPE, payload);
        IsolatedExecutor.requirePayloadTenant(AccessContextHolder.current(), resourceTenantId);
        IsolationAuditorHolder.exportAccepted(AccessContextHolder.current(), "submit", resourceTenantId, TASK_TYPE);
        return envelope;
    }

    public static void dispatch(IsolatedEnvelope envelope, String resourceTenantId, Consumer<IsolatedEnvelope> body) {
        ExportGuard.assertExportTenant(AccessContextHolder.currentOrNull(), resourceTenantId);
        IsolatedExecutor.requirePayloadTenant(AccessContextHolder.current(), envelope.tenantId());
        envelope.dispatch(body);
        IsolationAuditorHolder.exportAccepted(
                AccessContextHolder.current(), "dispatch", resourceTenantId, envelope.payloadType());
    }

    public static void writeFile(String resourceTenantId, String relativePath, byte[] body, Consumer<String> sink) {
        ExportGuard.assertExportTenant(AccessContextHolder.currentOrNull(), resourceTenantId);
        FileObjectKeyGuard.putObject(relativePath, body, sink);
        IsolationAuditorHolder.exportAccepted(
                AccessContextHolder.current(), "writeFile", resourceTenantId, relativePath);
    }

    public static void cacheStatus(
            String resourceTenantId,
            String env,
            String key,
            String value,
            BiConsumer<String, String> sink) {
        ExportGuard.assertExportTenant(AccessContextHolder.currentOrNull(), resourceTenantId);
        CacheKeyGuard.put(env, CACHE_MODULE, key, value, sink);
    }

    private static byte[] csv(List<String> columns, List<Map<String, Object>> rows) {
        StringBuilder out = new StringBuilder();
        out.append(String.join(",", columns)).append('\n');
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                for (int i = 0; i < columns.size(); i++) {
                    if (i > 0) {
                        out.append(',');
                    }
                    Object value = row == null ? null : row.get(ExportFieldPolicyGuard.fieldName(columns.get(i)));
                    if (value != null) {
                        out.append(String.valueOf(value).replace("\"", "\"\""));
                    }
                }
                out.append('\n');
            }
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }
}
