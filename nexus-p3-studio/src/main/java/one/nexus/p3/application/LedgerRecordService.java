package one.nexus.p3.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import one.nexus.p3.application.LedgerDefinitionService.LedgerBundle;
import one.nexus.p3.domain.LedgerIdentifiers;
import one.nexus.p3.infra.isolation.P3RequestContext;
import one.nexus.p3.infra.jdbc.GuardedLedgerStore;
import one.nexus.p3.infra.jdbc.GuardedLedgerStore.Page;

@Service
public class LedgerRecordService {
    private final LedgerDefinitionService definitions;
    private final GuardedLedgerStore store;

    public LedgerRecordService(LedgerDefinitionService definitions, GuardedLedgerStore store) {
        this.definitions = definitions;
        this.store = store;
    }

    @Transactional
    public Map<String, Object> create(UUID ledgerId, Map<String, Object> values) {
        LedgerBundle bundle = ready(ledgerId);
        UUID id = store.insert(bundle.ledger(), bundle.fields(), values == null ? Map.of() : values);
        return store.detail(bundle.ledger(), bundle.fields(), id);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID ledgerId, UUID recordId) {
        LedgerBundle bundle = ready(ledgerId);
        return store.detail(bundle.ledger(), bundle.fields(), recordId);
    }

    @Transactional(readOnly = true)
    public Page list(UUID ledgerId, Map<String, String> query, int page, int size) {
        LedgerBundle bundle = ready(ledgerId);
        Map<String, Object> equals = new LinkedHashMap<>();
        if (query != null) {
            query.forEach((key, value) -> {
                if (value != null && !value.isBlank() && !"page".equals(key) && !"size".equals(key)) {
                    equals.put(LedgerIdentifiers.requireFieldCode(key), value);
                }
            });
        }
        return store.list(bundle.ledger(), bundle.fields(), equals, page, size);
    }

    @Transactional
    public Map<String, Object> update(UUID ledgerId, UUID recordId, Map<String, Object> values, Long version) {
        LedgerBundle bundle = ready(ledgerId);
        return store.update(bundle.ledger(), bundle.fields(), recordId, values == null ? Map.of() : values, version);
    }

    @Transactional
    public void delete(UUID ledgerId, UUID recordId) {
        LedgerBundle bundle = ready(ledgerId);
        store.softDelete(bundle.ledger(), recordId);
    }

    private LedgerBundle ready(UUID ledgerId) {
        P3RequestContext.Snapshot ctx = P3RequestContext.require();
        definitions.requireReady(ctx.tenantId(), ledgerId);
        return definitions.get(ledgerId);
    }
}
