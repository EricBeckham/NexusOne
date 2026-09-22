package one.nexus.poc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.Map;

import javax.sql.DataSource;

import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.dmn.api.DmnDecisionService;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest
class FlowablePostgresIT {
    @Autowired ApprovalService approval;
    @Autowired JdbcTemplate jdbc;
    @Autowired RuntimeService runtime;
    @Autowired TaskService tasks;
    @Autowired DmnDecisionService dmnRules;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired SpringProcessEngineConfiguration engineConfiguration;

    @Test
    void sameDatabaseSeparateSchemaAndCommittedTask() throws Exception {
        assertSame(transactionManager, engineConfiguration.getTransactionManager());
        try (var connection = dataSource.getConnection()) {
            assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
        }
        Integer flowableTables = jdbc.queryForObject(
            "select count(*) from information_schema.tables where table_schema = 'flowable' and table_name like 'act_%'",
            Integer.class);
        Integer publicEngineTables = jdbc.queryForObject(
            "select count(*) from information_schema.tables where table_schema = 'public' and table_name like 'act_%'",
            Integer.class);
        assertTrue(flowableTables != null && flowableTables > 0);
        assertEquals(0, publicEngineTables);

        String businessId = UUID.randomUUID().toString();
        String instanceId = approval.saveAndStart(businessId, false);
        assertEquals(1, jdbc.queryForObject(
            "select count(*) from public.business_marker where id = ?", Integer.class, businessId));
        var task = tasks.createTaskQuery().processInstanceId(instanceId).singleResult();
        assertNotNull(task);
        assertEquals("Review", task.getName());
        tasks.complete(task.getId());
        assertEquals(0, runtime.createProcessInstanceQuery().processInstanceId(instanceId).count());
    }

    @Test
    void applicationAndFlowableRollBackTogether() {
        String businessId = UUID.randomUUID().toString();
        assertThrows(IllegalStateException.class, () -> approval.saveAndStart(businessId, true));
        assertEquals(0, jdbc.queryForObject(
            "select count(*) from public.business_marker where id = ?", Integer.class, businessId));
        assertEquals(0, runtime.createProcessInstanceQuery().processInstanceBusinessKey(businessId).count());
    }

    @Test
    void dmnDecisionUsesEmbeddedEngine() {
        Map<String, Object> large = dmnRules.createExecuteDecisionBuilder()
            .decisionKey("approvalDecision").variables(Map.of("amount", 150))
            .executeWithSingleResult();
        Map<String, Object> small = dmnRules.createExecuteDecisionBuilder()
            .decisionKey("approvalDecision").variables(Map.of("amount", 50))
            .executeWithSingleResult();
        assertEquals("review", large.get("route"));
        assertEquals("auto", small.get("route"));
    }

    @Test
    void asyncExecutorRunsJobAndCommitsBusinessWrite() throws InterruptedException {
        String businessId = UUID.randomUUID().toString();
        runtime.startProcessInstanceByKey("asyncProbeProcess", businessId);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
        int count;
        do {
            count = jdbc.queryForObject("select count(*) from public.business_marker where id = ?",
                Integer.class, businessId);
            if (count == 1) break;
            Thread.sleep(200);
        } while (System.nanoTime() < deadline);
        assertEquals(1, count, "async executor did not run within 20 seconds");
    }
}
