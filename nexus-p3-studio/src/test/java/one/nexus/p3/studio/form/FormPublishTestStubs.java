package one.nexus.p3.studio.form;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import one.nexus.p3.domain.FieldType;
import one.nexus.p3.domain.LedgerDefinition;
import one.nexus.p3.domain.LedgerField;
import one.nexus.p3.domain.LedgerScope;
import one.nexus.p3.domain.LedgerStatus;
import one.nexus.p3.domain.SchemaAst;
import one.nexus.p3.port.FieldRepository;
import one.nexus.p3.port.LedgerRepository;
import one.nexus.p3.schema.SchemaService;

/** Shared fakes for G5 publish-gate tests that go through {@link SchemaServicePublishHook}. */
public final class FormPublishTestStubs {
    public static final UUID LEDGER_ID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    public static final UUID APP_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");

    private FormPublishTestStubs() {}

    public static LedgerDefinition leaveLedger(UUID tenantId) {
        Instant now = Instant.parse("2026-09-22T00:00:00Z");
        return new LedgerDefinition(
                LEDGER_ID,
                tenantId,
                "g5a",
                APP_ID,
                "leave_request",
                "请假台账",
                LedgerScope.TENANT,
                "l_g5a_leave_request",
                1,
                LedgerStatus.READY,
                now,
                "designer",
                now,
                "designer",
                null,
                1L);
    }

    public static LedgerRepository leaveLedgers(UUID tenantId) {
        LedgerDefinition row = leaveLedger(tenantId);
        return new LedgerRepository() {
            @Override
            public void insert(LedgerDefinition ledger) {}

            @Override
            public void update(LedgerDefinition ledger) {}

            @Override
            public Optional<LedgerDefinition> findById(UUID idTenant, UUID id) {
                return row.tenantId().equals(idTenant) && row.id().equals(id) ? Optional.of(row) : Optional.empty();
            }

            @Override
            public Optional<LedgerDefinition> findByCode(UUID idTenant, String ledgerCode) {
                return row.tenantId().equals(idTenant) && row.ledgerCode().equals(ledgerCode)
                        ? Optional.of(row)
                        : Optional.empty();
            }

            @Override
            public Optional<LedgerDefinition> findByPhysicalTable(String physicalTable) {
                return Optional.empty();
            }

            @Override
            public List<LedgerDefinition> list(UUID idTenant) {
                return List.of(row);
            }

            @Override
            public List<LedgerDefinition> listReady() {
                return List.of(row);
            }
        };
    }

    public static FieldRepository leaveFields(UUID tenantId) {
        Instant now = Instant.parse("2026-09-22T00:00:00Z");
        LedgerField title = new LedgerField(
                UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc"),
                tenantId,
                LEDGER_ID,
                "title",
                "标题",
                FieldType.TEXT,
                true,
                false,
                0,
                now,
                "designer",
                now,
                "designer",
                null,
                1L);
        return new FieldRepository() {
            @Override
            public void insert(LedgerField field) {}

            @Override
            public List<LedgerField> listByLedger(UUID idTenant, UUID ledgerId) {
                return List.of(title);
            }

            @Override
            public Optional<LedgerField> findByCode(UUID idTenant, UUID ledgerId, String fieldCode) {
                return "title".equals(fieldCode) ? Optional.of(title) : Optional.empty();
            }
        };
    }

    /**
     * SchemaService stand-in: first {@code n-1} confirms succeed; the Nth throws the same
     * {@link IllegalStateException} shape {@code JdbcSchemaService} uses on a PostgreSQL
     * DDL abort ({@code 42701} duplicate_column).
     */
    public static SchemaService abortOnNthConfirm(int n) {
        AtomicInteger confirms = new AtomicInteger();
        return new SchemaService() {
            @Override
            public LedgerDefinition createPhysicalTable(LedgerDefinition ledger, List<LedgerField> fields) {
                return ledger;
            }

            @Override
            public LedgerDefinition evolvePhysicalTable(
                    LedgerDefinition ledger, List<LedgerField> currentFields, LedgerField added) {
                return ledger;
            }

            @Override
            public SchemaAst astOf(LedgerDefinition ledger, List<LedgerField> fields) {
                return new SchemaAst(ledger.physicalTable(), ledger.scope(), List.of());
            }

            @Override
            public void registerRuntime(LedgerDefinition ledger, List<LedgerField> fields) {}

            @Override
            public void confirmPhysicalTable(LedgerDefinition ledger, List<LedgerField> fields) {
                if (confirms.incrementAndGet() >= n) {
                    throw new IllegalStateException(
                            "SchemaService evolve failed for " + ledger.physicalTable(),
                            new SQLException(
                                    "column \"drift_col\" of relation \"" + ledger.physicalTable()
                                            + "\" already exists",
                                    "42701"));
                }
            }
        };
    }
}
