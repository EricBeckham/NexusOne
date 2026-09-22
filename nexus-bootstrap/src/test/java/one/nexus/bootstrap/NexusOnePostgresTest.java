package one.nexus.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import one.nexus.common.trace.TraceIds;
import one.nexus.event.OutboxPort;
import one.nexus.event.jdbc.JdbcOutboxStore;
import one.nexus.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(PostgresAvailableCondition.class)
class NexusOnePostgresTest {
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OutboxPort outboxPort;

    @Autowired
    NotificationService notificationService;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> PostgresAvailableCondition.env("NEXUS_DB_URL", "jdbc:postgresql://127.0.0.1:5432/nexusone"));
        registry.add("spring.datasource.username",
                () -> PostgresAvailableCondition.env("NEXUS_DB_USER", "nexus"));
        registry.add("spring.datasource.password",
                () -> PostgresAvailableCondition.env("NEXUS_DB_PASSWORD", ""));
    }

    @Test
    void flywayAppliedStaticSchemaOnly() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from platform.flyway_schema_history where success and version is not null",
                Integer.class);
        assertTrue(applied != null && applied >= 13,
                "expected V001-V003, V100-V101, V200, V300, V400, V500, V501, V510, V520, V530, V550; got " + applied);
        List<String> versions = jdbc.queryForList(
                "select version from platform.flyway_schema_history where success and version is not null",
                String.class);
        assertTrue(versions.contains("400"), "M0-06 V400 business_meta must be applied, got " + versions);
        Integer metaTables = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'business_meta' and table_name in ('application','ledger','ledger_field')",
                Integer.class);
        assertEquals(3, metaTables);
        assertTrue(versions.contains("500"), "M0-09 V500 execution channel must be applied, got " + versions);
        assertTrue(versions.contains("501"), "M0-11 V501 audit hash chain must be applied, got " + versions);
        assertTrue(versions.contains("502"), "G4 V502 audit export/archive must be applied, got " + versions);
        assertTrue(versions.contains("510"), "M0-10 V510 files must be applied, got " + versions);
        assertTrue(versions.contains("520"), "M0-10 V520 notify must be applied, got " + versions);
        assertTrue(versions.contains("530"), "M0-07 V530 form metadata must be applied, got " + versions);
        assertTrue(versions.contains("550"), "M0-08 V550 process metadata must be applied, got " + versions);
        Integer auditTables = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'platform' and table_name = 'audit_record'",
                Integer.class);
        assertEquals(1, auditTables);
        Integer iamTables = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'iam' and table_name = 'iam_global_account'",
                Integer.class);
        assertEquals(1, iamTables);
        Integer formTables = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'business_meta' and table_name = 'form'",
                Integer.class);
        assertEquals(1, formTables);
        Integer processTables = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'business_meta' and table_name = 'process_instance'",
                Integer.class);
        assertEquals(1, processTables);
        Integer ledgerTables = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'business_data'", Integer.class);
        assertEquals(0, ledgerTables);
        String manager = jdbc.queryForObject(
                "select meta_value from platform.runtime_meta where meta_key = 'schema_manager'", String.class);
        assertEquals("flyway_static_only", manager);
        List<String> schemas = jdbc.queryForList(
                "select schema_name from information_schema.schemata "
                        + "where schema_name in ('platform','iam','business_meta','business_data','quartz','flowable') "
                        + "order by 1",
                String.class);
        assertEquals(List.of("business_data", "business_meta", "flowable", "iam", "platform", "quartz"), schemas);
        Integer outbox = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'platform' and table_name = 'exec_outbox'",
                Integer.class);
        assertEquals(1, outbox);
    }

    @Test
    void notificationServiceUsesJdbcOutboxPort() {
        assertTrue(notificationService != null);
        assertTrue(outboxPort instanceof JdbcOutboxStore, () -> "expected JdbcOutboxStore, got "
                + (outboxPort == null ? "null" : outboxPort.getClass().getName()));
    }

    @Test
    void healthAndRuntimeExposeTraceId() throws Exception {
        HttpResponse<String> health = http.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, health.statusCode());
        assertTrue(health.body().contains("\"status\":\"UP\""));

        String trace = "0199a1c0-0000-7000-8000-000000000042";
        HttpResponse<String> runtime = http.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/meta/runtime"))
                        .header(TraceIds.HEADER, trace)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, runtime.statusCode());
        assertEquals(trace, runtime.headers().firstValue(TraceIds.HEADER).orElseThrow());
        assertTrue(runtime.body().contains("\"product\":\"NexusOne\""));
        assertTrue(runtime.body().contains("\"schema_manager\":\"flyway_static_only\""));
        assertTrue(runtime.body().contains("\"trace_id\":\"" + trace + "\""));
        assertTrue(runtime.body().contains("\"spring_boot\":\"4.1.1\""));
        assertTrue(runtime.body().contains("\"crypto_suite\":\"INTERNATIONAL\""));
        assertTrue(runtime.body().contains("\"secret_backend\":\"local+env\""));
        assertTrue(runtime.body().contains("\"secret_openbao\":false"));
        assertTrue(runtime.body().contains("\"secret_kms\":false"));
    }

    @Test
    void missingRouteUsesErrorEnvelope() throws Exception {
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/does-not-exist")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("\"code\":\"CORE.API.0002\""));
        assertTrue(response.body().contains("\"trace_id\""));
        assertTrue(response.headers().firstValue(TraceIds.HEADER).isPresent());
    }
}
