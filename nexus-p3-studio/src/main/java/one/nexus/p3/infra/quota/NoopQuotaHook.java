package one.nexus.p3.infra.quota;

import java.util.UUID;
import one.nexus.p3.port.QuotaHook;

/** Default QuotaHook when P1 QuotaService is not a bean. Registered from {@code P3Configuration}. */
public class NoopQuotaHook implements QuotaHook {
    @Override
    public void consumeApp(UUID tenantId, String idempotencyKey) {}

    @Override
    public void consumeCustomLedger(UUID tenantId, String idempotencyKey) {}

    @Override
    public void releaseCustomLedger(UUID tenantId) {}
}
