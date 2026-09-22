package one.nexus.isolation.boundary;

import java.util.function.Consumer;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;
import one.nexus.isolation.api.PlatformContext;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.context.AccessContextHolder;

/**
 * Object keys: {@code tenants/{tenantId}/...} or {@code platform/...}.
 * Cross-tenant keys are rejected before the storage sink runs.
 */
public final class FileObjectKeyGuard {
    private FileObjectKeyGuard() {}

    public static String objectKey(AccessContext context, String relativePath) {
        if (context == null) {
            throw new IsolationException(IsolationErrorCode.CONTEXT_PROPAGATION_MISSING, "missing-trace");
        }
        String relative = requireRelative(relativePath);
        if (context instanceof PlatformContext) {
            return "platform/" + relative;
        }
        if (context instanceof TenantContext tenant) {
            return "tenants/" + tenant.tenantId() + "/" + relative;
        }
        throw new IsolationException(IsolationErrorCode.MISSING_CONTEXT, context.traceId());
    }

    public static void assertOwned(AccessContext context, String objectKey) {
        if (context == null) {
            throw new IsolationException(IsolationErrorCode.CONTEXT_PROPAGATION_MISSING, "missing-trace");
        }
        if (objectKey == null || objectKey.isBlank() || objectKey.contains("..")) {
            throw new IsolationException(IsolationErrorCode.CROSS_TENANT_FILE, context.traceId());
        }
        if (context instanceof TenantContext tenant) {
            String prefix = "tenants/" + tenant.tenantId() + "/";
            if (!objectKey.startsWith(prefix)) {
                throw new IsolationException(IsolationErrorCode.CROSS_TENANT_FILE, context.traceId());
            }
            return;
        }
        if (context instanceof PlatformContext) {
            if (!(objectKey.startsWith("platform/") || objectKey.startsWith("tenants/"))) {
                throw new IsolationException(IsolationErrorCode.CROSS_TENANT_FILE, context.traceId());
            }
            return;
        }
        throw new IsolationException(IsolationErrorCode.MISSING_CONTEXT, context.traceId());
    }

    public static void putObject(String relativePath, byte[] body, Consumer<String> sink) {
        AccessContext context = AccessContextHolder.current();
        String key = objectKey(context, relativePath);
        assertOwned(context, key);
        sink.accept(key);
    }

    private static String requireRelative(String relativePath) {
        if (relativePath == null || relativePath.isBlank() || relativePath.startsWith("/") || relativePath.contains("..")) {
            throw new IsolationException(IsolationErrorCode.CROSS_TENANT_FILE, "missing-trace");
        }
        return relativePath;
    }
}
