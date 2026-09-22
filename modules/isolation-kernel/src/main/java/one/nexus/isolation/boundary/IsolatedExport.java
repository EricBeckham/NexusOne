package one.nexus.isolation.boundary;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.RenderedSql;
import one.nexus.isolation.audit.IsolationAuditorHolder;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.jdbc.GuardedJdbc;
import one.nexus.isolation.query.QueryCompiler;
import one.nexus.isolation.spi.FieldPolicy;

/**
 * Product export surface. Query compile (including joins), FieldPolicy
 * projection, file write, cache status, and Task submit all go through
 * {@link ExportGuard} plus the existing M0-02/09/10 guards. No second tenant
 * holder. FieldPolicy is applied before any file / Task sink.
 */
public final class IsolatedExport {
    public static final String TASK_TYPE = "ledger.export.csv";
    public static final String CACHE_MODULE = "export";

    private IsolatedExport() {}

    public static IsolatedEnvelope submit(String resourceTenantId, String payload) {
        AccessContext current = AccessContextHolder.currentOrNull();
        ExportGuard.assertExportTenant(current, resourceTenantId);
        IsolatedEnvelope envelope = IsolatedEnvelope.fromCurrent(TASK_TYPE, payload);
        IsolatedExecutor.requirePayloadTenant(AccessContextHolder.current(), resourceTenantId);
        return envelope;
    }

    public static void dispatch(IsolatedEnvelope envelope, String resourceTenantId, Consumer<IsolatedEnvelope> body) {
        ExportGuard.assertExportTenant(AccessContextHolder.currentOrNull(), resourceTenantId);
        IsolatedExecutor.requirePayloadTenant(AccessContextHolder.current(), envelope.tenantId());
        envelope.dispatch(body);
    }

    public static RenderedSql compileQuery(String resourceTenantId, QueryAst query, QueryCompiler compiler) {
        AccessContext current = AccessContextHolder.currentOrNull();
        ExportGuard.assertExportTenant(current, resourceTenantId);
        return compiler.compile(query, current);
    }

    public static List<Map<String, Object>> query(
            String resourceTenantId,
            QueryAst query,
            GuardedJdbc jdbc,
            Connection connection
    ) throws SQLException {
        AccessContext current = AccessContextHolder.currentOrNull();
        ExportGuard.assertExportTenant(current, resourceTenantId);
        return jdbc.executeQuery(connection, query, current);
    }

    public static List<Map<String, Object>> queryAndProject(
            String resourceTenantId,
            QueryAst query,
            GuardedJdbc jdbc,
            Connection connection,
            FieldPolicy fieldPolicy
    ) throws SQLException {
        List<Map<String, Object>> rows = query(resourceTenantId, query, jdbc, connection);
        return applyFieldPolicy(resourceTenantId, rows, fieldPolicy);
    }

    public static List<Map<String, Object>> applyFieldPolicy(
            String resourceTenantId,
            List<Map<String, Object>> rows,
            FieldPolicy fieldPolicy
    ) {
        AccessContext current = AccessContextHolder.currentOrNull();
        ExportGuard.assertExportTenant(current, resourceTenantId);
        if (fieldPolicy == null) {
            throw IsolationAuditorHolder.reject(
                    IsolationErrorCode.FIELD_POLICY_REQUIRED, current, "export missing FieldPolicy");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows == null ? List.<Map<String, Object>>of() : rows) {
            Object tenant = row.get("tenant_id");
            if (tenant != null && !resourceTenantId.equals(String.valueOf(tenant))) {
                throw IsolationAuditorHolder.reject(
                        IsolationErrorCode.CROSS_TENANT_EXPORT, current, "fieldpolicy row tenant mismatch");
            }
            Map<String, Object> projected = fieldPolicy.project(row);
            out.add(Map.copyOf(projected == null ? Map.of() : projected));
        }
        return List.copyOf(out);
    }

    public static void writeProjected(
            String resourceTenantId,
            String relativePath,
            List<Map<String, Object>> rows,
            FieldPolicy fieldPolicy,
            Consumer<String> sink
    ) {
        List<Map<String, Object>> projected = applyFieldPolicy(resourceTenantId, rows, fieldPolicy);
        writeFile(resourceTenantId, relativePath, csv(projected), sink);
    }

    public static void writeFile(String resourceTenantId, String relativePath, byte[] body, Consumer<String> sink) {
        ExportGuard.assertExportTenant(AccessContextHolder.currentOrNull(), resourceTenantId);
        FileObjectKeyGuard.putObject(relativePath, body, sink);
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

    static byte[] csv(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return new byte[0];
        }
        Set<String> headers = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            headers.addAll(row.keySet());
        }
        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", headers)).append('\n');
        for (Map<String, Object> row : rows) {
            List<String> cells = new ArrayList<>();
            for (String header : headers) {
                Object value = row.get(header);
                cells.add(value == null ? "" : String.valueOf(value).replace('\n', ' ').replace(',', ' '));
            }
            csv.append(String.join(",", cells)).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }
}
