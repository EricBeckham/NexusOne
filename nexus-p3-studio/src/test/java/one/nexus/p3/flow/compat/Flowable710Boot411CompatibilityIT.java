package one.nexus.p3.flow.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.SpringVersion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hard gate: Flowable 7.1.0 must run in the same JVM as Spring Boot 4.1.1.
 * If this class fails to start a ProcessEngine, M0-08 must stop.
 */
@SpringBootTest(classes = {
        FlowableCompatibilityApplication.class,
        Flowable710Boot411CompatibilityIT.SpikeDataSourceConfig.class
})
class Flowable710Boot411CompatibilityIT {
    private static EmbeddedPostgres postgres;

    @Autowired
    ProcessEngine processEngine;
    @Autowired
    RuntimeService runtimeService;
    @Autowired
    TaskService taskService;
    @Autowired
    SpringProcessEngineConfiguration engineConfiguration;
    @Autowired
    DataSource dataSource;
    @Autowired
    PlatformTransactionManager transactionManager;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    SpikeTx spikeTx;

    @BeforeAll
    static void startPostgres() throws Exception {
        postgres = EmbeddedPostgres.builder().start();
        try (Connection connection = postgres.getPostgresDatabase().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS flowable");
            statement.execute("CREATE TABLE IF NOT EXISTS public.business_marker (id text PRIMARY KEY)");
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
                "jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/postgres?currentSchema=flowable,public");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("nexus.flow.engine", () -> "flowable-7.1.0-in-process");
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("spring.autoconfigure.exclude", () -> String.join(",",
                "org.flowable.spring.boot.ProcessEngineAutoConfiguration",
                "org.flowable.spring.boot.ProcessEngineServicesAutoConfiguration",
                "org.flowable.spring.boot.eventregistry.EventRegistryAutoConfiguration",
                "org.flowable.spring.boot.eventregistry.EventRegistryServicesAutoConfiguration",
                "org.flowable.spring.boot.idm.IdmEngineAutoConfiguration",
                "org.flowable.spring.boot.idm.IdmEngineServicesAutoConfiguration",
                "org.flowable.spring.boot.app.AppEngineAutoConfiguration",
                "org.flowable.spring.boot.app.AppEngineServicesAutoConfiguration",
                "org.flowable.spring.boot.RestApiAutoConfiguration",
                "org.flowable.spring.boot.FlowableSecurityAutoConfiguration"));
    }

    @Test
    void versionsAreTheFrozenPins() throws Exception {
        assertEquals("4.1.1", SpringBootVersion.getVersion());
        assertTrue(SpringVersion.getVersion() != null && SpringVersion.getVersion().startsWith("7."),
                "Boot 4.1.1 must be Spring Framework 7, got " + SpringVersion.getVersion());
        assertTrue(processEngine.getName() != null);
        assertTrue(org.flowable.engine.ProcessEngine.VERSION.startsWith("7.1.0"),
                "expected Flowable 7.1.0.x, got " + org.flowable.engine.ProcessEngine.VERSION);
        try (Connection connection = dataSource.getConnection();
             var rs = connection.createStatement().executeQuery("SHOW server_version")) {
            rs.next();
            assertTrue(rs.getString(1).startsWith("15."), "expected PostgreSQL 15, got " + rs.getString(1));
            assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
        }
    }

    @Test
    void officialStarterTargetsBoot3DatasourceAutoConfiguration() {
        ClassNotFoundException boot3 = null;
        try {
            Class.forName("org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration");
        } catch (ClassNotFoundException ex) {
            boot3 = ex;
        }
        Class<?> boot4 = null;
        try {
            boot4 = Class.forName("org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration");
        } catch (ClassNotFoundException ex) {
            throw new AssertionError("Boot 4 DataSourceAutoConfiguration missing", ex);
        }
        assertTrue(boot3 != null, "Boot 3 jdbc auto-config class unexpectedly present");
        assertTrue(boot4.getName().startsWith("org.springframework.boot.jdbc.autoconfigure"));

        boolean starterPresent = true;
        try {
            Class.forName("org.flowable.spring.boot.ProcessEngineAutoConfiguration");
        } catch (ClassNotFoundException ex) {
            try {
                Class.forName("org.flowable.spring.boot.process.ProcessEngineAutoConfiguration");
            } catch (ClassNotFoundException nested) {
                starterPresent = false;
            }
        }
        assertTrue(starterPresent, "flowable-spring-boot-starter-process 7.1.0 should be on the test classpath");
    }

    @Test
    void sameJvmSharedTransactionAndFlowableSchema() {
        assertSame(transactionManager, engineConfiguration.getTransactionManager());
        assertEquals("flowable", engineConfiguration.getDatabaseSchema());
        Integer flowableTables = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'flowable' and table_name like 'act_%'",
                Integer.class);
        Integer publicEngineTables = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' and table_name like 'act_%'",
                Integer.class);
        assertTrue(flowableTables != null && flowableTables > 0, "engine must create act_* in schema flowable");
        assertEquals(0, publicEngineTables);
    }

    @Test
    void sequentialUserTaskCompletesOnSameEngine() {
        String businessKey = UUID.randomUUID().toString();
        var instance = runtimeService.createProcessInstanceBuilder()
                .processDefinitionKey("nexusSequentialApproval")
                .businessKey(businessKey)
                .variable("assignee", "reviewer")
                .variable("outcome", "")
                .start();
        var task = taskService.createTaskQuery().processInstanceId(instance.getId()).singleResult();
        assertEquals("Approve", task.getName());
        taskService.complete(task.getId(), Map.of("outcome", "APPROVE"));
        assertEquals(0, runtimeService.createProcessInstanceQuery().processInstanceId(instance.getId()).count());
    }

    @Test
    void businessWriteAndProcessInstanceRollBackTogether() {
        String businessKey = UUID.randomUUID().toString();
        assertThrows(IllegalStateException.class, () -> spikeTx.saveAndStart(businessKey, true));
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from public.business_marker where id = ?", Integer.class, businessKey));
        assertEquals(0, runtimeService.createProcessInstanceQuery().processInstanceBusinessKey(businessKey).count());
    }

    @TestConfiguration
    static class SpikeDataSourceConfig {
        @Bean
        SpikeTx spikeTx(JdbcTemplate jdbc, RuntimeService runtimeService) {
            return new SpikeTx(jdbc, runtimeService);
        }
    }

    static class SpikeTx {
        private final JdbcTemplate jdbc;
        private final RuntimeService runtimeService;

        SpikeTx(JdbcTemplate jdbc, RuntimeService runtimeService) {
            this.jdbc = jdbc;
            this.runtimeService = runtimeService;
        }

        @Transactional
        public String saveAndStart(String businessId, boolean fail) {
            jdbc.update("insert into public.business_marker(id) values (?)", businessId);
            String instanceId = runtimeService.createProcessInstanceBuilder()
                    .processDefinitionKey("nexusSequentialApproval")
                    .businessKey(businessId)
                    .variable("assignee", "reviewer")
                    .variable("outcome", "")
                    .start()
                    .getId();
            if (fail) {
                throw new IllegalStateException("force rollback");
            }
            return instanceId;
        }
    }
}
