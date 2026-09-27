package one.nexus.bootstrap.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import one.nexus.bootstrap.NexusOneApplication;
import one.nexus.bootstrap.content.DemoRemokeService.RemokePlan;
import one.nexus.bootstrap.content.DemoRemokeService.RemokeRequest;
import one.nexus.bootstrap.content.DemoRemokeService.RemokeResult;
import one.nexus.p1.domain.Actor;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p1.port.PlatformContext;
import org.flowable.engine.RepositoryService;
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

/**
 * DL-L2: against real tables — plan shows the scope, execute needs the operator's own fresh token and an unchanged
 * scope, clears only the demo tenant's activity on the demo product, keeps catalog / tenant / other tenants, audits
 * without the token, and the token is single-use.
 */
@SpringBootTest(classes = NexusOneApplication.class)
class DemoRemokePersistenceTest {
    private static final String DEMO = "01900000-0000-7000-8000-000000000101";
    private static final String OTHER = "01900000-0000-7000-8000-000000000001";
    private static final String APP = "01900000-0000-7000-8000-000000000503";
    private static final String VERSION = "01900000-0000-7000-8000-000000000504";
    private static EmbeddedPostgres postgres;

    @Autowired DemoRemokeService remoke;
    @Autowired JdbcClient jdbc;
    @Autowired RepositoryService processes;
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
        registry.add("nexus.demo-host.allow", () -> "1");
    }

    private void as(UUID operator) {
        when(isolation.requirePlatformContext(anyString(), eq(PlatformDuty.SYSTEM_ADMIN)))
                .thenReturn(new PlatformContext(true, "remoke", new Actor(operator, null, "ops", Set.of(PlatformDuty.SYSTEM_ADMIN)), "trace", null));
    }

    private int count(String sql) {
        return jdbc.sql(sql).query(Integer.class).single();
    }

    private void activity(String tenant) {
        Timestamp now = Timestamp.from(Instant.parse("2026-09-27T10:00:00Z"));
        UUID installation = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        jdbc.sql("INSERT INTO market.subscription(id,tenant_id,sku_code,status,started_at) VALUES(:id,CAST(:t AS uuid),'demo.simple-approval','ACTIVE',:now)")
                .param("id", UUID.randomUUID()).param("t", tenant).param("now", now).update();
        jdbc.sql("INSERT INTO market.installation(id,tenant_id,app_id,version_id,status,installed_at,updated_at) VALUES(:id,CAST(:t AS uuid),CAST(:a AS uuid),CAST(:v AS uuid),'INSTALLED',:now,:now)")
                .param("id", installation).param("t", tenant).param("a", APP).param("v", VERSION).param("now", now).update();
        jdbc.sql("INSERT INTO market.upgrade_step_record(id,tenant_id,installation_id,version_id,step_key,status,recorded_at,recorded_by,reason) VALUES(:id,CAST(:t AS uuid),:i,CAST(:v AS uuid),'precheck','DONE',:now,:op,'ops')")
                .param("id", UUID.randomUUID()).param("t", tenant).param("i", installation).param("v", VERSION).param("now", now).param("op", UUID.randomUUID()).update();
        jdbc.sql("INSERT INTO market.activation_application(id,tenant_id,sku_code,version_id,status,created_at,created_by) VALUES(:id,CAST(:t AS uuid),'demo.simple-approval',CAST(:v AS uuid),'PENDING',:now,:by)")
                .param("id", UUID.randomUUID()).param("t", tenant).param("v", VERSION).param("now", now).param("by", UUID.randomUUID()).update();
        jdbc.sql("INSERT INTO business_meta.process_instance(id,tenant_id,process_key,business_key,initiator_person_id,assignee_type,assignee_ref,flowable_instance_id,status,started_at) VALUES(:id,CAST(:t AS uuid),'demoSimpleApproval',:bk,:p,'PERSON','x','no-engine-instance','APPROVED',:now)")
                .param("id", instance).param("t", tenant).param("bk", "bk-" + instance).param("p", UUID.randomUUID()).param("now", now).update();
        jdbc.sql("INSERT INTO business_meta.process_todo(id,tenant_id,instance_id,flowable_task_id,task_name,status,created_at) VALUES(:id,CAST(:t AS uuid),:i,:task,'审批','DONE',:now)")
                .param("id", UUID.randomUUID()).param("t", tenant).param("i", instance).param("task", "task-" + instance).param("now", now).update();
        jdbc.sql("INSERT INTO business_meta.process_action(id,tenant_id,instance_id,actor_person_id,action,idempotency_key,created_at) VALUES(:id,CAST(:t AS uuid),:i,:p,'START',:k,:now)")
                .param("id", UUID.randomUUID()).param("t", tenant).param("i", instance).param("p", UUID.randomUUID()).param("k", "k-" + instance).param("now", now).update();
        jdbc.sql("INSERT INTO business_meta.product_application(id,tenant_id,installation_id,product_definition_id,form_template_version_id,process_instance_id,values_json,status,initiator_person_id,created_at,updated_at) VALUES(:id,CAST(:t AS uuid),:i,'01900000-0000-7000-8000-000000000505','01900000-0000-7000-8000-000000000502',:pi,'{}'::jsonb,'APPROVED',:p,:now,:now)")
                .param("id", UUID.randomUUID()).param("t", tenant).param("i", installation).param("pi", instance).param("p", UUID.randomUUID()).param("now", now).update();
    }

    @Test
    void plannedRemokeClearsOnlyDemoActivityAndNeedsAFreshTokenOfTheSameOperator() {
        for (ContentSteps.Step step : ContentSteps.ORDER) step.apply(jdbc, processes);
        activity(DEMO);
        activity(OTHER);
        UUID operator = UUID.randomUUID();
        as(operator);

        RemokePlan plan = remoke.plan();
        assertEquals(DEMO, plan.tenantId());
        assertEquals(Map.of("product_applications", 1, "process_instances", 1, "activation_applications", 1,
                "installations", 1, "upgrade_step_records", 1, "subscriptions", 1), plan.scope());
        assertTrue(plan.confirmationToken().length() >= 30);

        as(UUID.randomUUID());
        assertThrows(ResponseStatusException.class, () -> remoke.execute(new RemokeRequest(plan.confirmationToken(), "other operator")));
        as(operator);
        assertThrows(ResponseStatusException.class, () -> remoke.execute(new RemokeRequest(plan.confirmationToken(), "reused")),
                "a token is consumed even by a refused attempt");

        RemokePlan stale = remoke.plan();
        // scope changes after the plan (one active installation per tenant/app is enforced, so add an application)
        jdbc.sql("INSERT INTO market.activation_application(id,tenant_id,sku_code,version_id,status,created_at,created_by) VALUES(:id,CAST(:t AS uuid),'demo.simple-approval',CAST(:v AS uuid),'REJECTED',now(),:by)")
                .param("id", UUID.randomUUID()).param("t", DEMO).param("v", VERSION).param("by", UUID.randomUUID()).update();
        ResponseStatusException changed = assertThrows(ResponseStatusException.class,
                () -> remoke.execute(new RemokeRequest(stale.confirmationToken(), "stale")));
        assertEquals(409, changed.getStatusCode().value());
        assertEquals(1, count("SELECT count(*) FROM market.installation WHERE tenant_id = '" + DEMO + "'"), "nothing deleted on 409");
        assertEquals(2, count("SELECT count(*) FROM market.activation_application WHERE tenant_id = '" + DEMO + "'"));

        RemokePlan fresh = remoke.plan();
        RemokeResult result = remoke.execute(new RemokeRequest(fresh.confirmationToken(), "rerun the demo"));
        assertEquals(1, result.removed().get("installations"));
        assertEquals(2, result.removed().get("activation_applications"));
        for (String table : new String[] {"market.subscription", "market.installation", "market.activation_application",
                "business_meta.product_application", "business_meta.process_instance", "business_meta.process_todo",
                "business_meta.process_action", "market.upgrade_step_record"}) {
            assertEquals(0, count("SELECT count(*) FROM " + table + " WHERE tenant_id = '" + DEMO + "'"), table);
            assertEquals(1, count("SELECT count(*) FROM " + table + " WHERE tenant_id = '" + OTHER + "'"), "other tenant kept: " + table);
        }
        assertEquals(0, count("SELECT count(*) FROM market.installation_product_pin p JOIN market.installation i ON i.id = p.installation_id WHERE i.tenant_id = '" + DEMO + "'"));
        assertTrue(ContentSteps.CATALOG.present(jdbc, processes), "catalog kept");
        assertTrue(ContentSteps.TENANT.present(jdbc, processes), "tenant kept");
        assertTrue(ContentSteps.PROCESS.present(jdbc, processes), "process deployment kept");

        assertThrows(ResponseStatusException.class, () -> remoke.execute(new RemokeRequest(fresh.confirmationToken(), "again")),
                "single use");
        String audit = jdbc.sql("SELECT details::text FROM platform.audit_event WHERE event_type = 'platform.demo_remoke.executed'")
                .query(String.class).single();
        assertTrue(audit.contains("rerun the demo"), audit);
        assertFalse(audit.contains(fresh.confirmationToken()), "token is never audited");
    }
}
