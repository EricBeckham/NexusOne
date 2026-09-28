package one.nexus.p3.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import one.nexus.notification.ApprovalNotificationPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ProductApplicationNotifierTest {
    private final ApprovalNotificationPublisher publisher = mock(ApprovalNotificationPublisher.class);
    private final UUID recipient = UUID.fromString("01900000-0000-7000-8000-000000000203");
    private final UUID actor = UUID.fromString("01900000-0000-7000-8000-000000000204");

    @Test
    void productApplicationNotifiesAssigneeAndInitiator() {
        ProductApplicationNotifier notifier = notifier(publisher);
        notifier.assigned(recipient, "费用申请", actor);
        verify(publisher).taskAssigned(recipient.toString(), "费用申请", actor.toString());

        notifier.decided("product-application:01a0e57d-4c81-7cf5-b458-1d8dbfcd711e", "APPROVE", recipient, actor, "费用申请");
        verify(publisher).approved(recipient.toString(), "费用申请", actor.toString());

        notifier.decided("product-application:01a0e57d-4c81-7cf5-b458-1d8dbfcd711e", "REJECT", recipient, actor, "  ");
        verify(publisher).rejected(recipient.toString(), "审批", actor.toString());
    }

    @Test
    void ledgerDecisionsStayWithTheExistingNotifier() {
        ProductApplicationNotifier notifier = notifier(publisher);
        notifier.decided("ledger-record:01", "APPROVE", recipient, actor, "费用申请");
        notifier.decided("ledger-record:01", "REJECT", recipient, actor, "费用申请");
        verify(publisher, never()).approved(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(publisher, never()).rejected(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingPublisherDoesNotFailTheDecision() {
        ProductApplicationNotifier notifier = notifier(null);
        notifier.assigned(recipient, "费用申请", actor);
        notifier.decided("product-application:01", "APPROVE", recipient, actor, "费用申请");
    }

    @SuppressWarnings("unchecked")
    private static ProductApplicationNotifier notifier(ApprovalNotificationPublisher publisher) {
        ObjectProvider<ApprovalNotificationPublisher> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(publisher);
        return new ProductApplicationNotifier(provider);
    }
}
