package one.nexus.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import one.nexus.common.trace.TraceIds;
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
        assertEquals(6, applied);
        Integer iamTables = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'iam' and table_name = 'iam_global_account'",
                Integer.class);
        assertEquals(1, iamTables);
        Integer ledgerTables = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'business_data'", Integer.class);
        assertEquals(0, ledgerTables);
        String manager = jdbc.queryForObject(
                "select meta_value from platform.runtime_meta where meta_key = 'schema_manager'", String.class);
        assertEquals("flyway_static_only", manager);
        List<String> schemas = jdbc.queryForList(
                "select schema_name from information_schema.schemata "
                        + "where schema_name in ('platform','iam','business_meta','business_data') "
                        + "order by 1",
                String.class);
        assertEquals(List.of("business_data", "business_meta", "iam", "platform"), schemas);
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
