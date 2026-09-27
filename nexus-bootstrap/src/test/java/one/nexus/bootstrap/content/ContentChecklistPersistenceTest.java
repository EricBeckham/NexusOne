package one.nexus.bootstrap.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import one.nexus.bootstrap.NexusOneApplication;
import one.nexus.bootstrap.content.ContentBootstrapService.StepResult;
import one.nexus.bootstrap.content.ContentChecklistService.Checklist;
import one.nexus.bootstrap.content.ContentChecklistService.ChecklistItem;
import one.nexus.bootstrap.content.ContentChecklistService.StepRequest;
import one.nexus.p1.domain.Actor;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p1.port.PlatformContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

/** DL-L4: presence checklist in DL-X2 order; one step at a time, in order, idempotent; manual items stay manual. */
@SpringBootTest(classes = NexusOneApplication.class)
class ContentChecklistPersistenceTest {
    private static EmbeddedPostgres postgres;

    @Autowired ContentChecklistService checklist;
    @Autowired JdbcClient jdbc;
    @MockitoBean IsolationGuard isolation;

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
        registry.add("nexus.demo.enabled", () -> "false");
        registry.add("nexus.content-bootstrap.enabled", () -> "true");
    }

    private static Map<String, Boolean> presence(Checklist list) {
        return list.items().stream().collect(Collectors.toMap(ChecklistItem::step, ChecklistItem::present));
    }

    private int count(String sql) {
        return jdbc.sql(sql).query(Integer.class).single();
    }

    @Test
    void walksTheHandbookOneStepAtATime() {
        when(isolation.requirePlatformContext(anyString(), eq(PlatformDuty.SYSTEM_ADMIN)))
                .thenReturn(new PlatformContext(true, "prepare", new Actor(UUID.randomUUID(), null, "ops",
                        Set.of(PlatformDuty.SYSTEM_ADMIN)), "trace", null));
        int upgradeRecords = count("SELECT count(*) FROM market.upgrade_step_record");

        Checklist empty = checklist.checklist();
        assertEquals(List.of("tenant", "identities", "form", "flow", "product", "catalog", "activation", "application"),
                empty.items().stream().map(ChecklistItem::step).toList());
        assertTrue(empty.items().stream().noneMatch(ChecklistItem::present));
        assertEquals("tenant", empty.nextMissing());
        assertEquals(List.of("identities", "activation", "application"), empty.items().stream()
                .filter(item -> item.applyKey() == null).map(ChecklistItem::step).toList());
        assertTrue(empty.items().stream().filter(item -> item.applyKey() == null)
                .allMatch(item -> item.manualNote() != null && !item.manualNote().isBlank()));

        ResponseStatusException outOfOrder = assertThrows(ResponseStatusException.class,
                () -> checklist.apply("catalog", new StepRequest("prepare catalog")));
        assertEquals(409, outOfOrder.getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> checklist.apply("identities", new StepRequest("x"))).getStatusCode().value(), "manual steps cannot be applied");

        assertEquals(StepResult.CREATED, checklist.apply("tenant", new StepRequest("prepare tenant")).outcome());
        assertEquals(StepResult.ALREADY_PRESENT, checklist.apply("tenant", new StepRequest("again")).outcome());
        assertEquals(StepResult.CREATED, checklist.apply("catalog", new StepRequest("prepare catalog")).outcome());
        assertEquals(StepResult.CREATED, checklist.apply("process", new StepRequest("deploy flow")).outcome());
        assertEquals(StepResult.ALREADY_PRESENT, checklist.apply("process", new StepRequest("again")).outcome());

        Map<String, Boolean> ready = presence(checklist.checklist());
        assertTrue(ready.get("tenant") && ready.get("form") && ready.get("flow") && ready.get("product") && ready.get("catalog"), ready.toString());
        assertFalse(ready.get("identities") || ready.get("activation") || ready.get("application"), ready.toString());
        assertEquals("identities", checklist.checklist().nextMissing());
        assertEquals(1, count("SELECT count(*) FROM act_re_procdef WHERE key_ = 'demoSimpleApproval'"));
        assertEquals(upgradeRecords, count("SELECT count(*) FROM market.upgrade_step_record"), "no upgrade step touched");
        assertEquals(5, count("SELECT count(*) FROM platform.audit_event WHERE event_type = 'platform.content_bootstrap.step_applied'"));
        assertNull(checklist.checklist().items().stream().filter(item -> item.step().equals("activation"))
                .findFirst().orElseThrow().applyKey());
        assertTrue(checklist.checklist().items().stream().allMatch(item ->
                !(item.label() + item.step()).contains("支付") && !(item.label() + item.step()).contains("payment")));
    }
}
