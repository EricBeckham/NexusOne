package one.nexus.p3.completion;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.common.id.UuidV7;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.p3.completion.CompletionAction.FieldMapping;
import one.nexus.p3.domain.LedgerDefinition;
import one.nexus.p3.domain.LedgerField;
import one.nexus.p3.domain.P3ErrorCodes;
import one.nexus.p3.domain.P3Exception;
import one.nexus.p3.port.FieldRepository;
import one.nexus.p3.port.LedgerRecordSink;
import one.nexus.p3.port.LedgerRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FA3: create / update / list post-completion actions and preview the row they would insert.
 * Tenant and actor come from the bound isolation context, never from the request.
 */
@Service
public class CompletionActionService {
    private final CompletionActionStore store;
    private final LedgerRepository ledgers;
    private final FieldRepository fields;
    private final ObjectProvider<LedgerRecordSink> records;

    public CompletionActionService(CompletionActionStore store, LedgerRepository ledgers, FieldRepository fields,
            ObjectProvider<LedgerRecordSink> records) {
        this.store = store;
        this.ledgers = ledgers;
        this.fields = fields;
        this.records = records;
    }

    /** What the caller may set; everything else is server-owned. */
    public record Draft(String name, String sourceLedgerCode, String processKey, CompletionAction.Trigger trigger,
            String targetLedgerCode, List<FieldMapping> fieldMapping, Boolean enabled) {}

    @Transactional(readOnly = true)
    public List<CompletionAction> list() {
        return store.list(tenantId());
    }

    @Transactional(readOnly = true)
    public CompletionAction get(UUID id) {
        return store.find(tenantId(), id).orElseThrow(() -> notFound(id));
    }

    @Transactional
    public CompletionAction create(Draft draft) {
        UUID tenant = tenantId();
        validate(tenant, draft);
        Instant now = Instant.now();
        String actor = actor();
        CompletionAction action = new CompletionAction(UuidV7.generate(), tenant, draft.name().trim(),
                draft.sourceLedgerCode(), draft.processKey().trim(), draft.trigger(),
                CompletionAction.ActionType.INSERT_ROW, draft.targetLedgerCode(), draft.fieldMapping(),
                draft.enabled() == null || draft.enabled(), 1, now, actor, now, actor);
        store.insert(action);
        return action;
    }

    @Transactional
    public CompletionAction update(UUID id, long expectedVersion, Draft draft) {
        UUID tenant = tenantId();
        CompletionAction current = store.find(tenant, id).orElseThrow(() -> notFound(id));
        if (current.version() != expectedVersion) {
            throw conflict(id);
        }
        validate(tenant, draft);
        CompletionAction next = new CompletionAction(current.id(), tenant, draft.name().trim(),
                draft.sourceLedgerCode(), draft.processKey().trim(), draft.trigger(), current.actionType(),
                draft.targetLedgerCode(), draft.fieldMapping(),
                draft.enabled() == null ? current.enabled() : draft.enabled(), current.version() + 1,
                current.createdAt(), current.createdBy(), Instant.now(), actor());
        if (!store.update(next, expectedVersion)) {
            throw conflict(id);
        }
        return next;
    }

    /** The row this action would insert for {@code sourceRecordId}; nothing is written. */
    @Transactional(readOnly = true)
    public Map<String, Object> preview(UUID id, UUID sourceRecordId, String outcome) {
        UUID tenant = tenantId();
        CompletionAction action = store.find(tenant, id).orElseThrow(() -> notFound(id));
        LedgerRecordSink sink = records.getIfAvailable();
        if (sink == null) {
            throw invalid("ledger records are not available on this host", Map.of());
        }
        LedgerDefinition source = ready(tenant, action.sourceLedgerCode());
        Map<String, Object> row = sink.get(source.id(), sourceRecordId);
        String initiator = row.get("owner_id") == null ? null : String.valueOf(row.get("owner_id"));
        String result = outcome == null || outcome.isBlank()
                ? (action.trigger() == CompletionAction.Trigger.ANY ? "APPROVED" : action.trigger().name())
                : outcome;
        return CompletionRowBuilder.build(action.fieldMapping(), row, new CompletionRowBuilder.Completion(
                sourceRecordId, initiator, null, Instant.now(), result));
    }

    private void validate(UUID tenant, Draft draft) {
        if (draft == null || blank(draft.name()) || blank(draft.sourceLedgerCode()) || blank(draft.processKey())
                || blank(draft.targetLedgerCode()) || draft.trigger() == null) {
            throw invalid("name, source ledger, process key, trigger and target ledger are required", Map.of());
        }
        if (draft.sourceLedgerCode().equals(draft.targetLedgerCode())) {
            throw invalid("target ledger must differ from the source ledger", Map.of());
        }
        LedgerDefinition source = ready(tenant, draft.sourceLedgerCode());
        LedgerDefinition target = ready(tenant, draft.targetLedgerCode());
        List<LedgerField> sourceFields = fields.listByLedger(tenant, source.id());
        List<LedgerField> targetFields = fields.listByLedger(tenant, target.id());
        List<String> problems = CompletionRowBuilder.validate(draft.fieldMapping(), sourceFields, targetFields);
        if (!problems.isEmpty()) {
            throw invalid("field mapping is invalid", Map.of("problems", problems));
        }
    }

    private LedgerDefinition ready(UUID tenant, String code) {
        LedgerDefinition ledger = ledgers.findByCode(tenant, code)
                .orElseThrow(() -> invalid("ledger does not exist in this tenant", Map.of("ledger_code", code)));
        if (!ledger.ready()) {
            throw invalid("ledger is not ready", Map.of("ledger_code", code));
        }
        return ledger;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static UUID tenantId() {
        String bound = AccessContextHolder.current().boundTenantId().orElseThrow(P3Exception::missingTenant);
        return UUID.fromString(bound);
    }

    private static String actor() {
        return AccessContextHolder.current().actor().actorId();
    }

    static P3Exception invalid(String message, Map<String, Object> details) {
        return new P3Exception(P3ErrorCodes.ACTION_INVALID, 400, message, details);
    }

    static P3Exception notFound(UUID id) {
        return new P3Exception(P3ErrorCodes.ACTION_NOT_FOUND, 404, "completion action not found",
                Map.of("id", String.valueOf(id)));
    }

    static P3Exception conflict(UUID id) {
        return new P3Exception(P3ErrorCodes.ACTION_CONFLICT, 409, "completion action was changed; reload and retry",
                Map.of("id", String.valueOf(id)));
    }
}
