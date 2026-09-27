package one.nexus.p3.completion;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.p3.domain.P3ErrorCodes;
import one.nexus.p3.domain.P3Exception;
import one.nexus.p3.flow.persist.JdbcProcessStore;
import one.nexus.p3.flow.ProcessRecords.ProcessInstanceView;
import one.nexus.p3.flow.ProcessRecords.TodoView;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FA4: execution records and manual retry of a failed run (re-appends one action through the outbox). */
@Service
public class CompletionRunService {
    private final CompletionActionRunStore runs;
    private final CompletionActionStore actions;
    private final CompletionActionTrigger trigger;
    private final ObjectProvider<JdbcProcessStore> processes;

    public CompletionRunService(CompletionActionRunStore runs, CompletionActionStore actions,
            CompletionActionTrigger trigger, ObjectProvider<JdbcProcessStore> processes) {
        this.runs = runs;
        this.actions = actions;
        this.trigger = trigger;
        this.processes = processes;
    }

    @Transactional(readOnly = true)
    public List<CompletionActionRun> list(UUID actionId, Integer limit) {
        return runs.list(tenantId(), actionId, limit == null ? 50 : limit);
    }

    @Transactional
    public CompletionActionRun retry(UUID runId) {
        UUID tenant = tenantId();
        CompletionActionRun run = runs.find(tenant, runId).orElseThrow(() -> new P3Exception(
                P3ErrorCodes.ACTION_NOT_FOUND, 404, "run not found", Map.of("id", String.valueOf(runId))));
        if (run.status() == CompletionActionRun.Status.DONE) {
            throw new P3Exception(P3ErrorCodes.ACTION_CONFLICT, 409, "run already done", Map.of("id", runId.toString()));
        }
        CompletionAction action = actions.find(tenant, run.actionId()).orElseThrow(() -> new P3Exception(
                P3ErrorCodes.ACTION_NOT_FOUND, 404, "completion action not found",
                Map.of("id", String.valueOf(run.actionId()))));
        if (!action.enabled()) {
            throw new P3Exception(P3ErrorCodes.ACTION_CONFLICT, 409, "action is disabled; enable it before retrying",
                    Map.of("id", action.id().toString()));
        }
        String initiator = null;
        String approver = null;
        JdbcProcessStore store = processes.getIfAvailable();
        if (store != null && run.processInstanceId() != null) {
            ProcessInstanceView instance = store.findInstance(tenant, run.processInstanceId()).orElse(null);
            if (instance != null && instance.initiatorPersonId() != null) {
                initiator = instance.initiatorPersonId().toString();
            }
            approver = store.todosForInstance(tenant, run.processInstanceId()).stream()
                    .filter(todo -> todo.actedByPersonId() != null && todo.actedAt() != null)
                    .max(java.util.Comparator.comparing(TodoView::actedAt))
                    .map(todo -> todo.actedByPersonId().toString())
                    .orElse(null);
        }
        trigger.replay(new CompletionEvent(run.sourceLedgerCode(), run.sourceRecordId(), run.processInstanceId(),
                action.processKey(), run.outcome(), initiator, approver, run.createdAt().toString(), action.id()));
        return run;
    }

    private static UUID tenantId() {
        String bound = AccessContextHolder.current().boundTenantId().orElseThrow(P3Exception::missingTenant);
        return UUID.fromString(bound);
    }
}
