package one.nexus.bootstrap.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import one.nexus.bootstrap.NexusOneApplication;
import one.nexus.bootstrap.content.ContentBootstrapService.BootstrapRequest;
import one.nexus.bootstrap.content.ContentBootstrapService.BootstrapResult;
import one.nexus.bootstrap.content.ContentBootstrapService.StepResult;
import one.nexus.p1.domain.Actor;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p1.port.PlatformContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * DL-L1: with the demo switch OFF and only the bootstrap window open, one call creates the minimal content
 * (tenant, catalog, deployed process) and a second call creates nothing (idempotent). No upgrade step is touched.
 */
@SpringBootTest(classes = NexusOneApplication.class)
class ContentBootstrapPersistenceTest {
    private static EmbeddedPostgres postgres;

    @Autowired ContentBootstrapService bootstrap;
    @Autowired JdbcClient jdbc;
    @Autowired Environment env;
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

    private int count(String sql) {
        return jdbc.sql(sql).query(Integer.class).single();
    }

    @Test
    void createsTheMinimalContentWithoutTheDemoSwitchAndIsIdempotent() {
        assertFalse("true".equals(env.getProperty("nexus.demo.enabled")), "the demo switch stays off");
        when(isolation.requirePlatformContext(anyString(), eq(PlatformDuty.SYSTEM_ADMIN)))
                .thenReturn(new PlatformContext(true, "prepare demo", new Actor(UUID.randomUUID(), null, "ops",
                        Set.of(PlatformDuty.SYSTEM_ADMIN)), "trace", null));
        int upgradeRecords = count("SELECT count(*) FROM market.upgrade_step_record");

        BootstrapResult first = bootstrap.bootstrap(new BootstrapRequest("prepare demo host"));
        assertEquals(List.of("tenant", "catalog", "process"), first.steps().stream().map(StepResult::step).toList());
        assertTrue(first.steps().stream().allMatch(step -> step.outcome().equals(StepResult.CREATED)), first::toString);
        assertEquals(1, count("SELECT count(*) FROM platform.tenant WHERE id = '01900000-0000-7000-8000-000000000101'"));
        assertEquals(1, count("SELECT count(*) FROM market.sku_product_binding WHERE sku_code = 'demo.simple-approval'"));
        assertEquals(1, count("SELECT count(*) FROM act_re_procdef WHERE key_ = 'demoSimpleApproval'"));

        BootstrapResult second = bootstrap.bootstrap(new BootstrapRequest("again"));
        assertTrue(second.steps().stream().allMatch(step -> step.outcome().equals(StepResult.ALREADY_PRESENT)), second::toString);
        assertEquals(1, count("SELECT count(*) FROM act_re_procdef WHERE key_ = 'demoSimpleApproval'"), "no second deployment");
        assertEquals(upgradeRecords, count("SELECT count(*) FROM market.upgrade_step_record"), "no upgrade step touched");
        assertEquals(2, count("SELECT count(*) FROM platform.audit_event WHERE event_type = 'platform.content_bootstrap.applied'"));
    }
}
