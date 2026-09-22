package one.nexus.poc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import javax.sql.DataSource;

import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
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
}
