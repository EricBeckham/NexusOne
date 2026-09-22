package one.nexus.permission.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * P0-02 SnapshotManager port. Database version is authoritative. Notification
 * (Redis PUBLISH or in-process) only asks for an early reload. Version read or
 * snapshot load failure fails closed on the request path. Channel loss cannot
 * leave a stale snapshot because every {@link #get} re-reads the DB version.
 * {@link #pollCached()} is the between-request poller used by
 * {@link PolicySnapshotPoller}; it must not take the process down.
 */
public final class SnapshotManager {
    private static final Logger log = LoggerFactory.getLogger(SnapshotManager.class);

    private final Function<String, Long> versionReader;
    private final Function<String, PolicySnapshot> snapshotLoader;
    private final int maxCachedTenants;
    private final Map<String, PolicySnapshot> cache;
    private final Set<String> invalidated = ConcurrentHashMap.newKeySet();

    public SnapshotManager(Function<String, Long> versionReader, Function<String, PolicySnapshot> snapshotLoader) {
        this(versionReader, snapshotLoader, 256);
    }

    public SnapshotManager(
            Function<String, Long> versionReader,
            Function<String, PolicySnapshot> snapshotLoader,
            int maxCachedTenants
    ) {
        this.versionReader = Objects.requireNonNull(versionReader, "versionReader");
        this.snapshotLoader = Objects.requireNonNull(snapshotLoader, "snapshotLoader");
        this.maxCachedTenants = Math.max(1, maxCachedTenants);
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, PolicySnapshot> eldest) {
                return size() > SnapshotManager.this.maxCachedTenants;
            }
        };
    }

    public void notifyInvalidated(String tenantId) {
        invalidated.add(tenantId);
    }

    public PolicySnapshot get(String tenantId) {
        return get(tenantId, false);
    }

    public PolicySnapshot get(String tenantId, boolean highRiskWrite) {
        Objects.requireNonNull(tenantId, "tenantId");
        long version;
        try {
            version = versionReader.apply(tenantId);
        } catch (RuntimeException ex) {
            throw new SnapshotUnavailableException("policy version unavailable", ex);
        }
        PolicySnapshot cached;
        synchronized (cache) {
            cached = cache.get(tenantId);
        }
        if (cached == null || cached.version() < version || invalidated.contains(tenantId)) {
            PolicySnapshot loaded;
            try {
                loaded = snapshotLoader.apply(tenantId);
            } catch (RuntimeException ex) {
                throw new SnapshotUnavailableException("policy snapshot unavailable", ex);
            }
            if (loaded == null || !tenantId.equals(loaded.tenantId()) || loaded.version() < version) {
                throw new SnapshotUnavailableException("policy snapshot is stale");
            }
            synchronized (cache) {
                cache.put(tenantId, loaded);
            }
            invalidated.remove(tenantId);
            cached = loaded;
        }
        if (cached.version() > version) {
            throw new SnapshotUnavailableException("policy version regressed");
        }
        if (highRiskWrite && cached.version() != version) {
            throw new SnapshotUnavailableException("policy version is not current for high-risk write");
        }
        return cached;
    }

    public void evict(String tenantId) {
        synchronized (cache) {
            cache.remove(tenantId);
        }
        invalidated.add(tenantId);
    }

    public int cachedTenantCount() {
        synchronized (cache) {
            return cache.size();
        }
    }

    /**
     * Cached snapshot version, or {@code null} when the tenant is not in the LRU.
     * Does not read the database.
     */
    public Long cachedVersion(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        synchronized (cache) {
            PolicySnapshot cached = cache.get(tenantId);
            return cached == null ? null : cached.version();
        }
    }

    public boolean isMarkedInvalidated(String tenantId) {
        return invalidated.contains(tenantId);
    }

    /**
     * Re-read {@code iam.perm_policy_version} (or the in-memory clock) for every
     * cached tenant and reload when the version moved or Redis asked for an
     * early refresh. Background failures stay in the cache; the next {@link #get}
     * still fail-closes. This is not Sentinel / replica failover.
     *
     * @return tenants successfully polled
     */
    public int pollCached() {
        List<String> tenants;
        synchronized (cache) {
            tenants = new ArrayList<>(cache.keySet());
        }
        int polled = 0;
        for (String tenantId : tenants) {
            try {
                get(tenantId);
                polled++;
            } catch (RuntimeException ex) {
                log.warn("policy snapshot poll skipped for tenant {}: {}", tenantId, ex.getMessage());
            }
        }
        return polled;
    }
}
