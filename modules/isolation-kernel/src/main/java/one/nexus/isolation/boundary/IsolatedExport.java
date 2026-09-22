package one.nexus.isolation.boundary;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.context.AccessContextHolder;

/**
 * Product export surface. Query compile, file write, cache status, and Task
 * submit all go through {@link ExportGuard} plus the existing M0-02/09/10
 * guards. No second tenant holder.
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
}
