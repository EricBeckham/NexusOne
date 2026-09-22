package one.nexus.p3.port;

import java.util.UUID;

/** Optional P1 quota seam. No-op when {@code QuotaService} is not on the classpath. */
public interface QuotaHook {
    void consumeApp(UUID tenantId, String idempotencyKey);

    void consumeCustomLedger(UUID tenantId, String idempotencyKey);

    void releaseCustomLedger(UUID tenantId);
}
