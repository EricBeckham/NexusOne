package one.nexus.bootstrap.completion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import one.nexus.bootstrap.NexusOneApplication;
import one.nexus.p3.completion.CompletionAction;
import one.nexus.p3.completion.CompletionActionController;
import one.nexus.p3.completion.CompletionActionStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** FA3: V710 + the JDBC store + the controller wiring in the real application on PostgreSQL 15. */
@SpringBootTest(classes = NexusOneApplication.class)
class CompletionActionPostgresTest {
    private static EmbeddedPostgres postgres;

    @Autowired CompletionActionStore store;
    @Autowired CompletionActionController controller;
    @Autowired JdbcTemplate jdbc;

    @BeforeAll
    static void startPostgres() throws Exception {
        postgres = EmbeddedPostgres.builder().start();
    }

    @AfterAll
    static void stopPostgres() throws Exception {
        if (postgres != null) postgres.close();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/postgres");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("nexus.execution.dispatcher-enabled", () -> "false");
        registry.add("nexus.execution.worker-enabled", () -> "false");
        registry.add("nexus.execution.quartz-auto-startup", () -> "false");
        registry.add("nexus.cache.mode", () -> "memory");
    }

    static CompletionAction action(UUID tenant, String source, boolean enabled) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        return new CompletionAction(UUID.randomUUID(), tenant, "报销通过→付款", source, "expenseApproval",
                CompletionAction.Trigger.APPROVED, CompletionAction.ActionType.INSERT_ROW, "payment",
                List.of(new CompletionAction.FieldMapping("amount", CompletionAction.SourceKind.FIELD, "amount"),
                        new CompletionAction.FieldMapping("pay_status", CompletionAction.SourceKind.CONST, "待付款")),
                enabled, 1, now, "lin", now, "lin");
    }

    @Test
    void storesReadsUpdatesAndScopesByTenant() {
        UUID tenant = UUID.randomUUID();
        CompletionAction saved = action(tenant, "expense", true);
        store.insert(saved);
        store.insert(action(tenant, "expense", false));
        store.insert(action(UUID.randomUUID(), "expense", true));

        CompletionAction read = store.find(tenant, saved.id()).orElseThrow();
        assertEquals(saved.fieldMapping(), read.fieldMapping());
        assertEquals("待付款", read.fieldMapping().get(1).value());
        assertEquals(1, store.enabledFor(tenant, "expense", "expenseApproval").size());
        assertEquals(2, store.list(tenant).size());
        assertTrue(store.find(UUID.randomUUID(), saved.id()).isEmpty());

        CompletionAction next = new CompletionAction(saved.id(), tenant, saved.name(), saved.sourceLedgerCode(),
                saved.processKey(), CompletionAction.Trigger.ANY, saved.actionType(), saved.targetLedgerCode(),
                saved.fieldMapping(), false, 2, saved.createdAt(), saved.createdBy(), Instant.now(), "lin");
        assertTrue(store.update(next, 1));
        assertFalse(store.update(next, 1), "stale version");
        assertEquals(CompletionAction.Trigger.ANY, store.find(tenant, saved.id()).orElseThrow().trigger());
        assertTrue(store.enabledFor(tenant, "expense", "expenseApproval").isEmpty());
    }

    @Test
    void theSchemaRefusesSelfTargetsAndDuplicateRuns() {
        UUID tenant = UUID.randomUUID();
        assertThrows(RuntimeException.class, () -> store.insert(new CompletionAction(UUID.randomUUID(), tenant, "x",
                "same", "p", CompletionAction.Trigger.ANY, CompletionAction.ActionType.INSERT_ROW, "same", List.of(),
                true, 1, Instant.now(), "a", Instant.now(), "a")));
        CompletionAction saved = action(tenant, "expense", true);
        store.insert(saved);
        UUID record = UUID.randomUUID();
        String insertRun = """
                INSERT INTO business_meta.completion_action_run
                    (id, tenant_id, action_id, source_ledger_code, source_record_id, outcome, status, created_at, updated_at)
                VALUES (?, ?, ?, 'expense', ?, 'APPROVED', 'PENDING', now(), now())
                """;
        jdbc.update(insertRun, UUID.randomUUID(), tenant, saved.id(), record);
        assertThrows(RuntimeException.class, () -> jdbc.update(insertRun, UUID.randomUUID(), tenant, saved.id(), record));
    }

    @Test
    void theControllerIsWired() {
        assertTrue(controller != null);
    }
}
