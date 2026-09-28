package one.nexus.p3.infra.quota;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import one.nexus.p1.application.QuotaService;
import one.nexus.p1.domain.QuotaMetric;

class P1QuotaHookTest {
    @Test
    void missingRowsAreReconciledBeforeAppAndLedgerConsumption() {
        QuotaService quotas = mock(QuotaService.class);
        P3QuotaRowRecovery recovery = mock(P3QuotaRowRecovery.class);
        P1QuotaHook hook = new P1QuotaHook(quotas, recovery);
        UUID tenantId = UUID.randomUUID();

        hook.consumeApp(tenantId, "app");
        hook.consumeCustomLedger(tenantId, "ledger");

        InOrder order = inOrder(recovery, quotas);
        order.verify(recovery).ensure(tenantId, QuotaMetric.APP);
        order.verify(quotas).consume(tenantId, QuotaMetric.APP, 1, "app");
        order.verify(recovery).ensure(tenantId, QuotaMetric.CUSTOM_LEDGER);
        order.verify(quotas).consume(tenantId, QuotaMetric.CUSTOM_LEDGER, 1, "ledger");
    }
}
