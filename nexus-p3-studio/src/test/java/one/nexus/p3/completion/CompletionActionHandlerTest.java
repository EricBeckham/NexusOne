package one.nexus.p3.completion;

import static one.nexus.p3.completion.CompletionTestLedgers.demoMapping;
import static one.nexus.p3.completion.CompletionTestLedgers.ledger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import one.nexus.event.OutboxAppendCommand;
import one.nexus.event.OutboxPort;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.completion.CompletionAction.Trigger;
import one.nexus.p3.domain.LedgerDefinition;
import one.nexus.p3.domain.LedgerStatus;
import one.nexus.p3.port.LedgerRecordSink;
import one.nexus.p3.port.LedgerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class CompletionActionHandlerTest {
    final UUID tenant = UUID.randomUUID();
    final LedgerDefinition expense = ledger(tenant, "expense", LedgerStatus.READY);
    final CompletionTestLedgers.MemoryStore actions = new CompletionTestLedgers.MemoryStore();
    final MemoryRuns runs = new MemoryRuns();
    final LedgerRepository ledgers = mock(LedgerRepository.class);
    final LedgerRecordSink sink = mock(LedgerRecordSink.class);
    final IsolationKernel kernel = IsolationKernel.create(new TableRegistry(), Set.of("p3.schema"), new CollectingAuditSink());
    final UUID record = UUID.randomUUID();
    final UUID initiator = UUID.randomUUID();
    final UUID approver = UUID.randomUUID();
    final List<String> insertedAs = new ArrayList<>();
    final List<Boolean> p2BoundDuringInsert = new ArrayList<>();
    CompletionActionHandler handler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(ledgers.findByCode(tenant, "expense")).thenReturn(Optional.of(expense));
        when(sink.get(expense.id(), record)).thenReturn(Map.of("title", "差旅", "amount", "128.50", "reason", "北京出差"));
        when(sink.insert(anyString(), any())).thenAnswer(invocation -> {
            insertedAs.add(AccessContextHolder.current().actor().actorId());
            p2BoundDuringInsert.add(TenantContextHolder.get() != null);
            return UUID.randomUUID();
        });
        ObjectProvider<LedgerRecordSink> sinks = mock(ObjectProvider.class);
        when(sinks.getIfAvailable()).thenReturn(sink);
        ObjectProvider<IsolationKernel> kernels = mock(ObjectProvider.class);
        when(kernels.getIfAvailable()).thenReturn(kernel);
        ObjectProvider<one.nexus.audit.AuditPort> audits = mock(ObjectProvider.class);
        handler = new CompletionActionHandler(actions, runs, ledgers, sinks, kernels, audits);
        // What the dispatcher restores: the tenant, with the actor that appended the event (the approver).
        String id = tenant.toString();
        AccessContextHolder.set(kernel.tenants().issue(
                new AuthenticatedMembership(Actor.person(approver.toString()), id, "mem-" + id, Set.of(id)), "trace-1"));
    }

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
        TenantContextHolder.clear();
    }

    CompletionAction action(Trigger trigger, String target) {
        Instant now = Instant.now();
        CompletionAction action = new CompletionAction(UUID.randomUUID(), tenant, "a", "expense", "expenseApproval",
                trigger, CompletionAction.ActionType.INSERT_ROW, target, demoMapping(), true, 1, now, "lin", now, "lin");
        actions.insert(action);
        return action;
    }

    IsolatedEnvelope envelope(String outcome, UUID actionId) {
        CompletionEvent event = new CompletionEvent("expense", record, UUID.randomUUID(), "expenseApproval", outcome,
                initiator.toString(), approver.toString(), "2026-09-27T08:00:00Z", actionId);
        return new IsolatedEnvelope(tenant.toString(), "trace-1", CompletionEvent.DESTINATION, event.toJson());
    }

    @Test
    void insertsOneRowAsTheInitiatorAndMarksTheRunDone() {
        CompletionAction action = action(Trigger.APPROVED, "payment");
        handler.onDispatch(envelope("APPROVED", null));

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Map<String, Object>> row = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(sink).insert(eq("payment"), row.capture());
        assertEquals(initiator.toString(), row.getValue().get("payee"));
        assertEquals("128.50", row.getValue().get("amount"));
        assertEquals("待付款", row.getValue().get("pay_status"));
        assertEquals(record.toString(), row.getValue().get("source_no"));
        assertEquals(initiator.toString(), row.getValue().get("owner_id"));
        assertEquals(List.of(initiator.toString()), insertedAs, "created_by is the initiator");
        assertEquals(List.of(false), p2BoundDuringInsert, "tenant-only: no P2 session during the write");
        assertEquals(approver.toString(), AccessContextHolder.current().actor().actorId(), "context restored");

        CompletionActionRun run = runs.only();
        assertEquals(action.id(), run.actionId());
        assertEquals(CompletionActionRun.Status.DONE, run.status());
        assertTrue(run.targetRecordId() != null);
    }

    @Test
    void aRedeliveredEventNeverInsertsTwice() {
        action(Trigger.APPROVED, "payment");
        handler.onDispatch(envelope("APPROVED", null));
        handler.onDispatch(envelope("APPROVED", null));
        verify(sink, times(1)).insert(anyString(), any());
    }

    @Test
    void onlyMatchingTriggersRun() {
        action(Trigger.APPROVED, "payment");
        CompletionAction any = action(Trigger.ANY, "archive");
        handler.onDispatch(envelope("REJECTED", null));
        verify(sink, never()).insert(eq("payment"), any());
        verify(sink).insert(eq("archive"), any());
        assertEquals(any.id(), runs.only().actionId());
    }

    @Test
    void aFailureIsRecordedRetriedAndThenSucceeds() {
        action(Trigger.APPROVED, "payment");
        AtomicInteger calls = new AtomicInteger();
        when(sink.insert(anyString(), any())).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("target ledger is not ready");
            }
            return UUID.randomUUID();
        });
        IllegalStateException failed = assertThrows(IllegalStateException.class,
                () -> handler.onDispatch(envelope("APPROVED", null)));
        assertFalse(failed.getMessage().contains("128.50"), "no field values in the outbox error");
        assertEquals(CompletionActionRun.Status.FAILED, runs.only().status());
        assertEquals("IllegalStateException", runs.only().errorCode());

        handler.onDispatch(envelope("APPROVED", null));
        assertEquals(CompletionActionRun.Status.DONE, runs.only().status());
        assertEquals(2, runs.only().attempts());
        assertNull(runs.only().errorCode());
    }

    @Test
    void oneFailingActionDoesNotUndoTheOthersAndOnlyItIsRetried() {
        action(Trigger.APPROVED, "payment");
        action(Trigger.APPROVED, "broken");
        when(sink.insert(eq("broken"), any())).thenThrow(new IllegalStateException("x"));
        assertThrows(IllegalStateException.class, () -> handler.onDispatch(envelope("APPROVED", null)));
        assertThrows(IllegalStateException.class, () -> handler.onDispatch(envelope("APPROVED", null)));
        verify(sink, times(1)).insert(eq("payment"), any());
        verify(sink, times(2)).insert(eq("broken"), any());
    }

    @Test
    void aManualRetryReplaysOnlyItsAction() {
        CompletionAction first = action(Trigger.APPROVED, "payment");
        action(Trigger.APPROVED, "archive");
        handler.onDispatch(envelope("APPROVED", first.id()));
        verify(sink).insert(eq("payment"), any());
        verify(sink, never()).insert(eq("archive"), any());
    }

    @Test
    void anotherTenantsEventIsRefused() {
        action(Trigger.APPROVED, "payment");
        IsolatedEnvelope foreign = new IsolatedEnvelope(UUID.randomUUID().toString(), "t", CompletionEvent.DESTINATION,
                envelope("APPROVED", null).payload());
        assertThrows(RuntimeException.class, () -> handler.onDispatch(foreign));
        verify(sink, never()).insert(anyString(), any());
    }

    @Test
    void theEventCarriesIdsOnly() {
        String json = envelope("APPROVED", null).payload();
        assertFalse(json.contains("128.50") || json.contains("北京出差"), json);
        CompletionEvent back = CompletionEvent.fromJson(json);
        assertEquals(record, back.sourceRecordId());
        assertEquals("APPROVED", back.outcome());
        assertNull(back.actionId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void theTriggerAppendsOnlyWhenAnEnabledActionMatches() {
        OutboxPort outbox = mock(OutboxPort.class);
        ObjectProvider<OutboxPort> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(outbox);
        when(ledgers.findById(tenant, expense.id())).thenReturn(Optional.of(expense));
        CompletionActionTrigger trigger = new CompletionActionTrigger(actions, ledgers, provider);

        assertFalse(trigger.onCompleted(tenant, expense.id(), record, UUID.randomUUID(), "expenseApproval",
                "APPROVED", initiator, approver), "no action configured");
        action(Trigger.REJECTED, "payment");
        assertFalse(trigger.onCompleted(tenant, expense.id(), record, UUID.randomUUID(), "expenseApproval",
                "APPROVED", initiator, approver), "trigger does not match");
        assertFalse(trigger.onCompleted(tenant, expense.id(), record, UUID.randomUUID(), "otherProcess",
                "REJECTED", initiator, approver), "other process");
        verify(outbox, never()).append(any());

        assertTrue(trigger.onCompleted(tenant, expense.id(), record, UUID.randomUUID(), "expenseApproval",
                "REJECTED", initiator, approver));
        org.mockito.ArgumentCaptor<OutboxAppendCommand> command = org.mockito.ArgumentCaptor.forClass(OutboxAppendCommand.class);
        verify(outbox).append(command.capture());
        assertEquals(CompletionEvent.DESTINATION, command.getValue().destination());
        assertEquals(CompletionEvent.EVENT_TYPE, command.getValue().eventType());
        assertEquals(record, command.getValue().aggregateId());
    }

    /** In-memory {@link CompletionActionRunStore} with the same claim semantics as the JDBC one. */
    static final class MemoryRuns implements CompletionActionRunStore {
        final Map<String, CompletionActionRun> byKey = new ConcurrentHashMap<>();

        @Override
        public synchronized CompletionActionRun claim(CompletionActionRun candidate) {
            String key = candidate.tenantId() + "|" + candidate.actionId() + "|" + candidate.sourceRecordId();
            CompletionActionRun current = byKey.getOrDefault(key, candidate);
            if (current.status() != CompletionActionRun.Status.DONE) {
                current = with(current, current.status(), current.targetRecordId(), current.attempts() + 1,
                        current.errorCode());
            }
            byKey.put(key, current);
            return current;
        }

        @Override
        public void markDone(UUID tenantId, UUID runId, UUID targetRecordId) {
            replace(runId, run -> with(run, CompletionActionRun.Status.DONE, targetRecordId, run.attempts(), null));
        }

        @Override
        public void markFailed(UUID tenantId, UUID runId, String errorCode) {
            replace(runId, run -> with(run, CompletionActionRun.Status.FAILED, null, run.attempts(), errorCode));
        }

        @Override
        public Optional<CompletionActionRun> find(UUID tenantId, UUID runId) {
            return byKey.values().stream().filter(run -> run.id().equals(runId)).findFirst();
        }

        @Override
        public List<CompletionActionRun> list(UUID tenantId, UUID actionId, int limit) {
            return byKey.values().stream().sorted(Comparator.comparing(CompletionActionRun::createdAt)).toList();
        }

        CompletionActionRun only() {
            assertEquals(1, byKey.size());
            return byKey.values().iterator().next();
        }

        private void replace(UUID runId, java.util.function.UnaryOperator<CompletionActionRun> change) {
            byKey.replaceAll((key, run) -> run.id().equals(runId) ? change.apply(run) : run);
        }

        private static CompletionActionRun with(CompletionActionRun run, CompletionActionRun.Status status,
                UUID target, int attempts, String error) {
            return new CompletionActionRun(run.id(), run.tenantId(), run.actionId(), run.sourceLedgerCode(),
                    run.sourceRecordId(), run.processInstanceId(), run.outcome(), status, target, attempts, error,
                    run.createdAt(), Instant.now());
        }
    }
}
