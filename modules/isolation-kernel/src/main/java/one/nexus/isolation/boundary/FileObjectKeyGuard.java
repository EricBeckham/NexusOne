package one.nexus.isolation.boundary;

import java.util.function.Consumer;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.PlatformContext;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.audit.IsolationAuditorHolder;
import one.nexus.isolation.context.AccessContextHolder;

/**
 * Object keys: {@code tenants/{tenantId}/...} or {@code platform/...}.
 * Cross-tenant keys are rejected before the storage sink runs.
 */
public final class FileObjectKeyGuard {
    private FileObjectKeyGuard() {}

    public static String objectKey(AccessContext context, String relativePath) {
        if (context == null) {
            throw IsolationAuditorHolder.reject(
                    IsolationErrorCode.CONTEXT_PROPAGATION_MISSING, null, "file key missing context");
        }
        String relative = requireRelative(relativePath);
        if (context instanceof PlatformContext) {
            return "platform/" + relative;
        }
        if (context instanceof TenantContext tenant) {
            return "tenants/" + tenant.tenantId() + "/" + relative;
        }
        throw IsolationAuditorHolder.reject(
                IsolationErrorCode.MISSING_CONTEXT, context, "file requires tenant or platform context");
    }

    public static void assertOwned(AccessContext context, String objectKey) {
        if (context == null) {
            throw IsolationAuditorHolder.reject(
                    IsolationErrorCode.CONTEXT_PROPAGATION_MISSING, null, "file assert missing context");
        }
        if (objectKey == null || objectKey.isBlank() || objectKey.contains("..")) {
            throw IsolationAuditorHolder.reject(
                    IsolationErrorCode.CROSS_TENANT_FILE, context, "blank or traversal object key");
        }
        if (context instanceof TenantContext tenant) {
            String prefix = "tenants/" + tenant.tenantId() + "/";
            if (!objectKey.startsWith(prefix)) {
                throw IsolationAuditorHolder.reject(
                        IsolationErrorCode.CROSS_TENANT_FILE, context, "foreign file prefix");
            }
            return;
        }
        if (context instanceof PlatformContext) {
            if (!(objectKey.startsWith("platform/") || objectKey.startsWith("tenants/"))) {
                throw IsolationAuditorHolder.reject(
                        IsolationErrorCode.CROSS_TENANT_FILE, context, "platform file key not owned");
            }
            return;
        }
        throw IsolationAuditorHolder.reject(
                IsolationErrorCode.MISSING_CONTEXT, context, "file requires tenant or platform context");
    }

    public static void putObject(String relativePath, byte[] body, Consumer<String> sink) {
        AccessContext context = AccessContextHolder.current();
        String key = objectKey(context, relativePath);
        assertOwned(context, key);
        sink.accept(key);
    }

    private static String requireRelative(String relativePath) {
        if (relativePath == null || relativePath.isBlank() || relativePath.startsWith("/") || relativePath.contains("..")) {
            throw IsolationAuditorHolder.reject(
                    IsolationErrorCode.CROSS_TENANT_FILE, "missing-trace", null, "invalid relative object path");
        }
        return relativePath;
    }
}
