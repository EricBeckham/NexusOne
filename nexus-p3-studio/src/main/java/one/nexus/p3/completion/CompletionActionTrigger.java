package one.nexus.p3.completion;

import java.time.Instant;
import java.util.UUID;
import one.nexus.event.OutboxAppendCommand;
import one.nexus.event.OutboxPort;
import one.nexus.p3.domain.LedgerDefinition;
import one.nexus.p3.port.LedgerRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * FA4: called by {@code BusinessPathService} when a form's approval reaches its terminal outcome, inside the same
 * transaction as the status write-back. Appends one outbox event only when an enabled action matches, so the status
 * and "there is work to do" commit or roll back together. Execution is {@link CompletionActionHandler}.
 */
@Component
public class CompletionActionTrigger {
    private final CompletionActionStore actions;
    private final LedgerRepository ledgers;
    private final ObjectProvider<OutboxPort> outbox;

    public CompletionActionTrigger(CompletionActionStore actions, LedgerRepository ledgers,
            ObjectProvider<OutboxPort> outbox) {
        this.actions = actions;
        this.ledgers = ledgers;
        this.outbox = outbox;
    }

    /** @return true when an event was appended. */
    public boolean onCompleted(UUID tenantId, UUID ledgerId, UUID recordId, UUID processInstanceId, String processKey,
            String outcome, UUID initiator, UUID approver) {
        OutboxPort port = outbox.getIfAvailable();
        if (port == null || tenantId == null || ledgerId == null || recordId == null || processKey == null) {
            return false;
        }
        String ledgerCode = ledgers.findById(tenantId, ledgerId).map(LedgerDefinition::ledgerCode).orElse(null);
        if (ledgerCode == null) {
            return false;
        }
        boolean matches = actions.enabledFor(tenantId, ledgerCode, processKey).stream()
                .anyMatch(action -> action.trigger().matches(outcome));
        if (!matches) {
            return false;
        }
        CompletionEvent event = new CompletionEvent(ledgerCode, recordId, processInstanceId, processKey, outcome,
                initiator == null ? null : initiator.toString(), approver == null ? null : approver.toString(),
                Instant.now().toString(), null);
        append(port, event, recordId);
        return true;
    }

    /** Re-append one action for one source record (manual retry). */
    public void replay(CompletionEvent event) {
        OutboxPort port = outbox.getIfAvailable();
        if (port == null) {
            throw new IllegalStateException("outbox is not available on this host");
        }
        append(port, event, event.sourceRecordId());
    }

    private static void append(OutboxPort port, CompletionEvent event, UUID recordId) {
        port.append(new OutboxAppendCommand(CompletionEvent.DESTINATION, CompletionEvent.EVENT_TYPE, 1,
                event.toJson(), "ledger_record", recordId, null, null));
    }
}
