package one.nexus.p3.infra.quota;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import one.nexus.p3.port.QuotaHook;

@Component
@ConditionalOnMissingBean(QuotaHook.class)
public class NoopQuotaHook implements QuotaHook {
    @Override
    public void consumeApp(UUID tenantId, String idempotencyKey) {}

    @Override
    public void consumeCustomLedger(UUID tenantId, String idempotencyKey) {}

    @Override
    public void releaseCustomLedger(UUID tenantId) {}
}
