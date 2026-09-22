package one.nexus.p3.studio.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p3.domain.Application;
import one.nexus.p3.domain.ApplicationKind;
import one.nexus.p3.domain.ApplicationStatus;
import one.nexus.p3.domain.FieldType;
import one.nexus.p3.domain.LedgerDefinition;
import one.nexus.p3.domain.LedgerField;
import one.nexus.p3.domain.LedgerScope;
import one.nexus.p3.domain.LedgerStatus;
import one.nexus.p3.port.ApplicationRepository;
import one.nexus.p3.port.FieldRepository;
import one.nexus.p3.port.LedgerRepository;
import org.junit.jupiter.api.Test;

class JdbcLedgerMetadataPortTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID APP = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID LEDGER = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");

    @Test
    void mapsReadyLedgerAndFieldCodesWithoutPhysicalNames() {
        JdbcLedgerMetadataPort port = new JdbcLedgerMetadataPort(
                new StubLedgers(readyLedger()), new StubFields(titleField()), new StubApps());
        LedgerRef ledger = port.findLedger(TENANT, "leave_request").orElseThrow();
        assertEquals("demo", ledger.appCode());
        assertEquals("leave_request", ledger.ledgerCode());
        assertTrue(ledger.usable());
        assertFalse(ledger.toString().contains("l_"));
        FieldRef field = port.findField(TENANT, "leave_request", "title").orElseThrow();
        assertEquals("title", field.fieldCode());
        assertEquals("TEXT", field.valueType());
        assertFalse(field.toString().toLowerCase().contains("column"));
    }

    @Test
    void draftLedgerIsPresentButNotUsable() {
        LedgerDefinition draft = ledger(LedgerStatus.DRAFT);
        JdbcLedgerMetadataPort port =
                new JdbcLedgerMetadataPort(new StubLedgers(draft), new StubFields(), new StubApps());
        LedgerRef ledger = port.findLedger(TENANT, "leave_request").orElseThrow();
        assertFalse(ledger.usable());
        assertTrue(port.findField(TENANT, "leave_request", "title").isEmpty());
    }

    private static LedgerDefinition readyLedger() {
        return ledger(LedgerStatus.READY);
    }

    private static LedgerDefinition ledger(LedgerStatus status) {
        Instant now = Instant.parse("2026-09-22T00:00:00Z");
        return new LedgerDefinition(
                LEDGER,
                TENANT,
                "acme",
                APP,
                "leave_request",
                "请假台账",
                LedgerScope.TENANT,
                "l_acme_leave_request",
                1,
                status,
                now,
                "designer",
                now,
                "designer",
                null,
                1L);
    }

    private static LedgerField titleField() {
        Instant now = Instant.parse("2026-09-22T00:00:00Z");
        return new LedgerField(
                UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc"),
                TENANT,
                LEDGER,
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
    }

    private record StubLedgers(LedgerDefinition... rows) implements LedgerRepository {
        @Override
        public void insert(LedgerDefinition ledger) {}

        @Override
        public void update(LedgerDefinition ledger) {}

        @Override
        public Optional<LedgerDefinition> findById(UUID tenantId, UUID id) {
            return List.of(rows).stream()
                    .filter(row -> row.tenantId().equals(tenantId) && row.id().equals(id))
                    .findFirst();
        }

        @Override
        public Optional<LedgerDefinition> findByCode(UUID tenantId, String ledgerCode) {
            return List.of(rows).stream()
                    .filter(row -> row.tenantId().equals(tenantId) && row.ledgerCode().equals(ledgerCode))
                    .findFirst();
        }

        @Override
        public Optional<LedgerDefinition> findByPhysicalTable(String physicalTable) {
            return Optional.empty();
        }

        @Override
        public List<LedgerDefinition> list(UUID tenantId) {
            return List.of(rows);
        }

        @Override
        public List<LedgerDefinition> listReady() {
            return List.of(rows).stream().filter(LedgerDefinition::ready).toList();
        }
    }

    private record StubFields(LedgerField... rows) implements FieldRepository {
        @Override
        public void insert(LedgerField field) {}

        @Override
        public List<LedgerField> listByLedger(UUID tenantId, UUID ledgerId) {
            return List.of(rows);
        }

        @Override
        public Optional<LedgerField> findByCode(UUID tenantId, UUID ledgerId, String fieldCode) {
            return List.of(rows).stream()
                    .filter(row -> row.tenantId().equals(tenantId)
                            && row.ledgerId().equals(ledgerId)
                            && row.fieldCode().equals(fieldCode))
                    .findFirst();
        }
    }

    private static final class StubApps implements ApplicationRepository {
        @Override
        public void insert(Application application) {}

        @Override
        public Optional<Application> findById(UUID tenantId, UUID id) {
            Instant now = Instant.parse("2026-09-22T00:00:00Z");
            return Optional.of(new Application(
                    APP,
                    TENANT,
                    "acme",
                    "demo",
                    "Demo",
                    ApplicationKind.CUSTOM,
                    ApplicationStatus.ACTIVE,
                    now,
                    "designer",
                    now,
                    "designer",
                    null,
                    1L));
        }

        @Override
        public Optional<Application> findByCode(UUID tenantId, String appCode) {
            return Optional.empty();
        }

        @Override
        public List<Application> list(UUID tenantId) {
            return List.of();
        }
    }
}
