package one.nexus.p3.completion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.AuditPort;
import one.nexus.common.id.UuidV7;
import one.nexus.event.OutboxHandler;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.isolation.boundary.SideEffectGuard;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.domain.LedgerDefinition;
import one.nexus.p3.domain.SystemColumns;
import one.nexus.p3.port.LedgerRecordSink;
import one.nexus.p3.port.LedgerRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * FA4: executes post-completion actions for {@code p3.approval.completed} (destination {@code p3.completion}).
 * The dispatcher has already restored the tenant; the row is written as the form's initiator (created_by /
 * owner_id) with tenant-only isolation — this is an automated action authorized by whoever configured it.
 * Idempotent through the run's unique key: a DONE run is never repeated. Any failure marks the run FAILED
 * (error category only) and is rethrown so the outbox retries with backoff and dead-letters in the end.
 */
@Component
public class CompletionActionHandler implements OutboxHandler {
    private final CompletionActionStore actions;
    private final CompletionActionRunStore runs;
    private final LedgerRepository ledgers;
    private final ObjectProvider<LedgerRecordSink> records;
    private final ObjectProvider<IsolationKernel> kernel;
    private final ObjectProvider<AuditPort> audits;

    public CompletionActionHandler(CompletionActionStore actions, CompletionActionRunStore runs,
            LedgerRepository ledgers, ObjectProvider<LedgerRecordSink> records, ObjectProvider<IsolationKernel> kernel,
            ObjectProvider<AuditPort> audits) {
        this.actions = actions;
        this.runs = runs;
        this.ledgers = ledgers;
        this.records = records;
        this.kernel = kernel;
        this.audits = audits;
    }

    @Override
    public boolean supports(String destination) {
        return CompletionEvent.DESTINATION.equals(destination);
    }

    @Override
    public void onDispatch(IsolatedEnvelope envelope) {
        SideEffectGuard.assertSameTenant(AccessContextHolder.current(), envelope.tenantId());
        UUID tenant = UUID.fromString(envelope.tenantId());
        CompletionEvent event = CompletionEvent.fromJson(envelope.payload());
        List<CompletionAction> matching = actions.enabledFor(tenant, event.sourceLedgerCode(), event.processKey())
                .stream()
                .filter(action -> action.trigger().matches(event.outcome()))
                .filter(action -> event.actionId() == null || event.actionId().equals(action.id()))
                .toList();
        List<String> failures = new ArrayList<>();
        for (CompletionAction action : matching) {
            CompletionActionRun run = runs.claim(new CompletionActionRun(UuidV7.generate(), tenant, action.id(),
                    event.sourceLedgerCode(), event.sourceRecordId(), event.processInstanceId(), event.outcome(),
                    CompletionActionRun.Status.PENDING, null, 0, null, Instant.now(), Instant.now()));
            if (run.status() == CompletionActionRun.Status.DONE) {
                continue;
            }
            try {
                UUID target = execute(tenant, action, event, envelope.traceId());
                runs.markDone(tenant, run.id(), target);
                audit(tenant, action, event, target, envelope.traceId());
            } catch (RuntimeException ex) {
                String category = category(ex);
                runs.markFailed(tenant, run.id(), category);
                failures.add(action.id() + ":" + category);
            }
        }
        if (!failures.isEmpty()) {
            // Error categories only (no field values) — this text lands in the outbox log.
            throw new IllegalStateException("post-completion action failed " + failures);
        }
    }

    private UUID execute(UUID tenant, CompletionAction action, CompletionEvent event, String traceId) {
        LedgerRecordSink sink = records.getIfAvailable();
        if (sink == null) {
            throw new IllegalStateException("ledger records unavailable");
        }
        LedgerDefinition source = ledgers.findByCode(tenant, event.sourceLedgerCode())
                .orElseThrow(() -> new IllegalStateException("source ledger missing"));
        Map<String, Object> sourceRow = sink.get(source.id(), event.sourceRecordId());
        Map<String, Object> row = new LinkedHashMap<>(CompletionRowBuilder.build(action.fieldMapping(), sourceRow,
                new CompletionRowBuilder.Completion(event.sourceRecordId(), event.initiator(), event.approver(),
                        event.completedAt() == null ? Instant.now() : Instant.parse(event.completedAt()),
                        event.outcome())));
        String actor = event.initiator() == null ? "system" : event.initiator();
        row.put(SystemColumns.OWNER_ID, actor);
        return asInitiator(tenant, actor, traceId, () -> sink.insert(action.targetLedgerCode(), row));
    }

    /** Write as the initiator, tenant-only: no P2 session is bound, so the P2 data-scope check stays isolation-only. */
    private <T> T asInitiator(UUID tenant, String actor, String traceId, java.util.function.Supplier<T> write) {
        AccessContext previous = AccessContextHolder.currentOrNull();
        one.nexus.p2.kernel.TenantContext previousIam = TenantContextHolder.get();
        IsolationKernel isolation = kernel.getIfAvailable();
        try {
            TenantContextHolder.clear();
            if (isolation != null) {
                String id = tenant.toString();
                AccessContextHolder.set(isolation.tenants().issue(
                        new AuthenticatedMembership(Actor.person(actor), id, "mem-" + id, Set.of(id)), traceId));
            }
            return write.get();
        } finally {
            if (previous == null) {
                AccessContextHolder.clear();
            } else {
                AccessContextHolder.set(previous);
            }
            if (previousIam != null) {
                TenantContextHolder.set(previousIam);
            }
        }
    }

    private void audit(UUID tenant, CompletionAction action, CompletionEvent event, UUID target, String traceId) {
        AuditPort port = audits.getIfAvailable();
        if (port == null) {
            return;
        }
        port.append(AuditCaptures.data(
                "p3.completion_action.executed",
                true,
                tenant,
                uuidOrNull(event.initiator()),
                "ledger_record",
                target.toString(),
                "post-completion insert (system on behalf of the initiator)",
                traceId,
                Map.of(
                        "action_id", action.id().toString(),
                        "source_ledger_code", event.sourceLedgerCode(),
                        "source_record_id", String.valueOf(event.sourceRecordId()),
                        "target_ledger_code", action.targetLedgerCode(),
                        "outcome", String.valueOf(event.outcome()),
                        "approver", String.valueOf(event.approver()))));
    }

    static String category(RuntimeException ex) {
        if (ex instanceof one.nexus.common.error.NexusException nexus) {
            return nexus.errorCode().code();
        }
        return ex.getClass().getSimpleName();
    }

    private static UUID uuidOrNull(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException notUuid) {
            return null;
        }
    }
}
