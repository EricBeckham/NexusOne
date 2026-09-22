package one.nexus.p3.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import one.nexus.common.id.UuidV7;
import one.nexus.p3.domain.Application;
import one.nexus.p3.domain.ApplicationKind;
import one.nexus.p3.domain.FieldType;
import one.nexus.p3.domain.LedgerDefinition;
import one.nexus.p3.domain.LedgerField;
import one.nexus.p3.domain.LedgerIdentifiers;
import one.nexus.p3.domain.LedgerScope;
import one.nexus.p3.domain.LedgerStatus;
import one.nexus.p3.domain.P3Exception;
import one.nexus.p3.domain.TableNames;
import one.nexus.p3.infra.isolation.P3RequestContext;
import one.nexus.p3.port.ApplicationRepository;
import one.nexus.p3.port.FieldRepository;
import one.nexus.p3.port.LedgerRepository;
import one.nexus.p3.port.QuotaHook;
import one.nexus.p3.schema.SchemaService;

@Service
public class LedgerDefinitionService {
    private final ApplicationRepository applications;
    private final LedgerRepository ledgers;
    private final FieldRepository fields;
    private final SchemaService schemaService;
    private final QuotaHook quotas;

    public LedgerDefinitionService(
            ApplicationRepository applications,
            LedgerRepository ledgers,
            FieldRepository fields,
            SchemaService schemaService,
            QuotaHook quotas) {
        this.applications = applications;
        this.ledgers = ledgers;
        this.fields = fields;
        this.schemaService = schemaService;
        this.quotas = quotas;
    }

    @Transactional
    public LedgerBundle create(UUID applicationId, String ledgerCode, String displayName, LedgerScope scope, List<FieldDraft> drafts) {
        P3RequestContext.Snapshot ctx = P3RequestContext.require();
        Application application = applications.findById(ctx.tenantId(), applicationId)
                .orElseThrow(() -> P3Exception.appNotFound(applicationId));
        String code = LedgerIdentifiers.normalizeCode(ledgerCode);
        if (displayName == null || displayName.isBlank()) {
            throw P3Exception.queryInvalid("display_name is required");
        }
        if (ledgers.findByCode(ctx.tenantId(), code).isPresent()) {
            throw P3Exception.ledgerConflict(code);
        }
        LedgerScope resolved = scope == null ? LedgerScope.TENANT : scope;
        String physical = TableNames.of(resolved, ctx.tenantCode(), application.appCode(), code);
        Instant now = Instant.now();
        LedgerDefinition draft = new LedgerDefinition(
                UuidV7.generate(),
                ctx.tenantId(),
                ctx.tenantCode(),
                application.id(),
                code,
                displayName.trim(),
                resolved,
                physical,
                1,
                LedgerStatus.DRAFT,
                now,
                ctx.actorId(),
                now,
                ctx.actorId(),
                null,
                1);
        if (resolved == LedgerScope.TENANT && application.kind() == ApplicationKind.CUSTOM) {
            quotas.consumeCustomLedger(ctx.tenantId(), "p3.ledger:" + ctx.tenantId() + ":" + code);
        }
        ledgers.insert(draft);
        List<LedgerField> created = new ArrayList<>();
        int order = 0;
        for (FieldDraft fieldDraft : drafts == null ? List.<FieldDraft>of() : drafts) {
            created.add(insertField(ctx, draft, fieldDraft, order++));
        }
        LedgerDefinition ready = schemaService.createPhysicalTable(draft, created);
        return new LedgerBundle(ready, created);
    }

    @Transactional
    public LedgerBundle addField(UUID ledgerId, FieldDraft draft) {
        P3RequestContext.Snapshot ctx = P3RequestContext.require();
        LedgerDefinition ledger = require(ctx.tenantId(), ledgerId);
        List<LedgerField> current = fields.listByLedger(ctx.tenantId(), ledgerId);
        if (fields.findByCode(ctx.tenantId(), ledgerId, LedgerIdentifiers.normalizeCode(draft.fieldCode())).isPresent()) {
            throw P3Exception.illegalEvolution("field already exists", java.util.Map.of("field", draft.fieldCode()));
        }
        LedgerField added = insertField(ctx, ledger, draft, current.size());
        LedgerDefinition evolved = schemaService.evolvePhysicalTable(ledger, current, added);
        List<LedgerField> next = new ArrayList<>(current);
        next.add(added);
        return new LedgerBundle(evolved, next);
    }

    @Transactional(readOnly = true)
    public List<LedgerBundle> list() {
        P3RequestContext.Snapshot ctx = P3RequestContext.require();
        return ledgers.list(ctx.tenantId()).stream()
                .map(ledger -> new LedgerBundle(ledger, fields.listByLedger(ctx.tenantId(), ledger.id())))
                .toList();
    }

    @Transactional(readOnly = true)
    public LedgerBundle get(UUID id) {
        P3RequestContext.Snapshot ctx = P3RequestContext.require();
        LedgerDefinition ledger = require(ctx.tenantId(), id);
        return new LedgerBundle(ledger, fields.listByLedger(ctx.tenantId(), id));
    }

    LedgerDefinition requireReady(UUID tenantId, UUID ledgerId) {
        LedgerDefinition ledger = require(tenantId, ledgerId);
        if (!ledger.ready()) {
            throw P3Exception.ledgerNotReady(ledgerId);
        }
        return ledger;
    }

    private LedgerDefinition require(UUID tenantId, UUID id) {
        return ledgers.findById(tenantId, id).orElseThrow(() -> P3Exception.ledgerNotFound(id));
    }

    private LedgerField insertField(P3RequestContext.Snapshot ctx, LedgerDefinition ledger, FieldDraft draft, int order) {
        Instant now = Instant.now();
        String code = LedgerIdentifiers.requireFieldCode(LedgerIdentifiers.normalizeCode(draft.fieldCode()));
        if (draft.displayName() == null || draft.displayName().isBlank()) {
            throw P3Exception.queryInvalid("field display_name is required");
        }
        LedgerField field = new LedgerField(
                UuidV7.generate(),
                ctx.tenantId(),
                ledger.id(),
                code,
                draft.displayName().trim(),
                draft.fieldType() == null ? FieldType.TEXT : draft.fieldType(),
                draft.required(),
                draft.indexed(),
                order,
                now,
                ctx.actorId(),
                now,
                ctx.actorId(),
                null,
                1);
        fields.insert(field);
        return field;
    }

    public record FieldDraft(String fieldCode, String displayName, FieldType fieldType, boolean required, boolean indexed) {}

    public record LedgerBundle(LedgerDefinition ledger, List<LedgerField> fields) {}
}
