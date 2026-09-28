package one.nexus.p3.infra.quota;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import one.nexus.p1.application.QuotaService;
import one.nexus.p1.domain.QuotaMetric;
import one.nexus.p3.port.QuotaHook;

@Component
@Primary
@ConditionalOnClass(QuotaService.class)
@ConditionalOnBean(QuotaService.class)
public class P1QuotaHook implements QuotaHook {
    private final QuotaService quotas;
    private final P3QuotaRowRecovery recovery;

    public P1QuotaHook(QuotaService quotas, P3QuotaRowRecovery recovery) {
        this.quotas = quotas;
        this.recovery = recovery;
    }

    @Override
    public void consumeApp(UUID tenantId, String idempotencyKey) {
        recovery.ensure(tenantId, QuotaMetric.APP);
        quotas.consume(tenantId, QuotaMetric.APP, 1, idempotencyKey);
    }

    @Override
    public void consumeCustomLedger(UUID tenantId, String idempotencyKey) {
        recovery.ensure(tenantId, QuotaMetric.CUSTOM_LEDGER);
        quotas.consume(tenantId, QuotaMetric.CUSTOM_LEDGER, 1, idempotencyKey);
    }

    @Override
    public void releaseCustomLedger(UUID tenantId) {
        quotas.release(tenantId, QuotaMetric.CUSTOM_LEDGER, 1);
    }
}
