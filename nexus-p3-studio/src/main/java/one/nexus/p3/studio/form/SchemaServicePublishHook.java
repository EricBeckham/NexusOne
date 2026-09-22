package one.nexus.p3.studio.form;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.p3.domain.LedgerDefinition;
import one.nexus.p3.port.FieldRepository;
import one.nexus.p3.port.LedgerRepository;
import one.nexus.p3.schema.SchemaService;

/**
 * Product publish gate: {@link SchemaService#confirmPhysicalTable} runs inside the
 * publish transaction after the PUBLISHING version row and before the Running
 * pointer switches. Not a second DDL path — SchemaService still owns CREATE/ALTER.
 */
public final class SchemaServicePublishHook implements PublishHook {
    private final SchemaService schemaService;
    private final LedgerRepository ledgers;
    private final FieldRepository fields;

    public SchemaServicePublishHook(
            SchemaService schemaService, LedgerRepository ledgers, FieldRepository fields) {
        this.schemaService = Objects.requireNonNull(schemaService, "schemaService");
        this.ledgers = Objects.requireNonNull(ledgers, "ledgers");
        this.fields = Objects.requireNonNull(fields, "fields");
    }

    @Override
    public void beforeActivate(FormSnapshot snapshot) {
        TenantContext tenant = requireTenant();
        UUID tenantId = UUID.fromString(tenant.tenantId());
        Set<String> ledgerCodes = new LinkedHashSet<>();
        for (FormBinding binding : snapshot.bindings()) {
            ledgerCodes.add(binding.ledgerCode());
        }
        for (String ledgerCode : ledgerCodes) {
            LedgerDefinition ledger = ledgers.findByCode(tenantId, ledgerCode)
                    .orElseThrow(() -> new IllegalStateException(
                            "SchemaService publish gate: ledger not found: " + ledgerCode));
            schemaService.confirmPhysicalTable(ledger, fields.listByLedger(tenantId, ledger.id()));
        }
    }

    private static TenantContext requireTenant() {
        AccessContext context = AccessContextHolder.currentOrNull();
        if (!(context instanceof TenantContext tenant)) {
            throw new NexusException(ErrorCode.CTX_MISSING_TENANT, 403, "TenantContext is required");
        }
        return tenant;
    }
}
