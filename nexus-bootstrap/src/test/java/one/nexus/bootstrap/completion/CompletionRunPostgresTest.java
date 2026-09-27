package one.nexus.bootstrap.completion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import one.nexus.bootstrap.NexusOneApplication;
import one.nexus.p3.completion.CompletionAction;
import one.nexus.p3.completion.CompletionActionHandler;
import one.nexus.p3.completion.CompletionActionRun;
import one.nexus.p3.completion.CompletionActionRunStore;
import one.nexus.p3.completion.CompletionActionStore;
import one.nexus.p3.completion.CompletionEvent;
import one.nexus.event.OutboxDispatcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** FA4: the run store's claim / done / failed semantics on PostgreSQL 15, and the handler's registration. */
@SpringBootTest(classes = NexusOneApplication.class)
class CompletionRunPostgresTest {
    private static EmbeddedPostgres postgres;

    @Autowired CompletionActionStore actions;
    @Autowired CompletionActionRunStore runs;
    @Autowired OutboxDispatcher dispatcher;

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

    CompletionActionRun candidate(UUID tenant, UUID action, UUID record) {
        return new CompletionActionRun(UUID.randomUUID(), tenant, action, "expense", record, UUID.randomUUID(),
                "APPROVED", CompletionActionRun.Status.PENDING, null, 0, null, Instant.now(), Instant.now());
    }

    @Test
    void claimIsIdempotentPerActionAndSourceRecord() {
        UUID tenant = UUID.randomUUID();
        Instant now = Instant.now();
        CompletionAction action = new CompletionAction(UUID.randomUUID(), tenant, "a", "expense", "p",
                CompletionAction.Trigger.APPROVED, CompletionAction.ActionType.INSERT_ROW, "payment", List.of(), true, 1,
                now, "lin", now, "lin");
        actions.insert(action);
        UUID record = UUID.randomUUID();

        CompletionActionRun first = runs.claim(candidate(tenant, action.id(), record));
        assertEquals(1, first.attempts());
        assertEquals(CompletionActionRun.Status.PENDING, first.status());

        runs.markFailed(tenant, first.id(), "P3.LEDGER.0003");
        CompletionActionRun second = runs.claim(candidate(tenant, action.id(), record));
        assertEquals(first.id(), second.id(), "same run, not a second one");
        assertEquals(2, second.attempts());
        assertEquals("P3.LEDGER.0003", second.errorCode());

        UUID target = UUID.randomUUID();
        runs.markDone(tenant, second.id(), target);
        CompletionActionRun done = runs.claim(candidate(tenant, action.id(), record));
        assertEquals(CompletionActionRun.Status.DONE, done.status());
        assertEquals(2, done.attempts(), "a DONE run is never re-attempted");
        assertEquals(target, done.targetRecordId());
        assertNull(done.errorCode());

        runs.markFailed(tenant, done.id(), "late");
        assertEquals(CompletionActionRun.Status.DONE, runs.find(tenant, done.id()).orElseThrow().status(),
                "a DONE run cannot fall back to FAILED");
        assertEquals(1, runs.list(tenant, action.id(), 10).size());
        assertTrue(runs.find(UUID.randomUUID(), done.id()).isEmpty(), "tenant scoped");
    }

    @Test
    void theHandlerIsRegisteredWithTheOutboxDispatcher() {
        assertTrue(dispatcher.handlers().stream()
                .anyMatch(handler -> handler instanceof CompletionActionHandler
                        && handler.supports(CompletionEvent.DESTINATION)));
    }
}
