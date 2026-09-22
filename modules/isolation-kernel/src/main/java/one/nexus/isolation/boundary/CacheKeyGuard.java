package one.nexus.isolation.boundary;

import java.util.function.BiConsumer;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.PlatformContext;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.audit.IsolationAuditorHolder;
import one.nexus.isolation.context.AccessContextHolder;

/**
 * Cache key contract: {@code nexus:{env}:{tenant_id}|platform:{module}:{key}}.
 * Writes are rejected before the sink is invoked on mismatch.
 */
public final class CacheKeyGuard {
    private CacheKeyGuard() {}

    public static String key(String env, AccessContext context, String module, String key) {
        if (context == null) {
            throw IsolationAuditorHolder.reject(
                    IsolationErrorCode.CONTEXT_PROPAGATION_MISSING, null, "cache key missing context");
        }
        String scope = context instanceof PlatformContext
                ? "platform"
                : ((TenantContext) context).tenantId();
        return "nexus:" + token(env, "env") + ":" + scope + ":" + token(module, "module") + ":" + token(key, "key");
    }

    public static void assertMatches(AccessContext context, String cacheKey) {
        if (context == null) {
            throw IsolationAuditorHolder.reject(
                    IsolationErrorCode.CONTEXT_PROPAGATION_MISSING, null, "cache assert missing context");
        }
        if (cacheKey == null || cacheKey.isBlank()) {
            throw IsolationAuditorHolder.reject(
                    IsolationErrorCode.CROSS_TENANT_CACHE, context, "blank cache key");
        }
        String[] parts = cacheKey.split(":", 5);
        if (parts.length != 5 || !"nexus".equals(parts[0])) {
            throw IsolationAuditorHolder.reject(
                    IsolationErrorCode.CROSS_TENANT_CACHE, context, "malformed cache key");
        }
        String scope = parts[2];
        if (context instanceof TenantContext tenant) {
            if (!tenant.tenantId().equals(scope)) {
                throw IsolationAuditorHolder.reject(
                        IsolationErrorCode.CROSS_TENANT_CACHE, context, "foreign cache tenant segment");
            }
        } else if (context instanceof PlatformContext) {
            if (!"platform".equals(scope)) {
                throw IsolationAuditorHolder.reject(
                        IsolationErrorCode.CROSS_TENANT_CACHE, context, "non-platform cache key");
            }
        } else {
            throw IsolationAuditorHolder.reject(
                    IsolationErrorCode.MISSING_CONTEXT, context, "cache requires tenant or platform context");
        }
    }

    public static void put(String env, String module, String key, String value, BiConsumer<String, String> sink) {
        AccessContext context = AccessContextHolder.current();
        String cacheKey = key(env, context, module, key);
        assertMatches(context, cacheKey);
        sink.accept(cacheKey, value);
    }

    private static String token(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " required");
        }
        if (value.contains(":")) {
            throw IsolationAuditorHolder.reject(
                    IsolationErrorCode.CROSS_TENANT_CACHE, "missing-trace", null, name + " must not contain ':'");
        }
        return value;
    }
}
