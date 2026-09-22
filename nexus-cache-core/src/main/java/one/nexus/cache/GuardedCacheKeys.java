package one.nexus.cache;

import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.boundary.CacheKeyGuard;
import one.nexus.isolation.context.AccessContextHolder;

/** Every Redis/memory access builds the key through {@link CacheKeyGuard}. */
public final class GuardedCacheKeys {
    private GuardedCacheKeys() {}

    public static String require(String env, String module, String key) {
        AccessContext context = AccessContextHolder.current();
        String cacheKey = CacheKeyGuard.key(env, context, module, key);
        CacheKeyGuard.assertMatches(context, cacheKey);
        return cacheKey;
    }
}
