package one.nexus.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import one.nexus.crypto.LocalThenEnvSecretPort;
import one.nexus.crypto.SecretPort;
import one.nexus.event.OutboxPort;
import one.nexus.event.jdbc.JdbcOutboxStore;
import one.nexus.notification.NotificationService;
import one.nexus.p5.connect.ConnectModels.IssuedClient;
import one.nexus.p5.connect.ConnectService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Product {@link NexusOneApplication} against empty PostgreSQL 15. Proves
 * {@code OutboxPort} is M0-09 {@link JdbcOutboxStore} after Flyway, not a
 * G18-only bean and not {@code nexus.notify.outbox=record}.
 * Dispatcher is off here so boot stays a wiring check; delivery SLO is
 * {@link NexusOneOutboxDispatcherTest} with the product default (on).
 */
@SpringBootTest(classes = NexusOneApplication.class)
class NexusOneProductBootTest {
    private static EmbeddedPostgres postgres;

    @Autowired
    OutboxPort outboxPort;

    @Autowired
    NotificationService notificationService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SecretPort secretPort;

    @Autowired
    ConnectService connectService;

    @BeforeAll
    static void startPostgres() throws Exception {
        postgres = EmbeddedPostgres.builder().start();
        try (Connection connection = postgres.getPostgresDatabase().getConnection();
                Statement statement = connection.createStatement();
                var rs = statement.executeQuery("SHOW server_version")) {
            rs.next();
            if (!rs.getString(1).startsWith("15.")) {
                throw new AssertionError("expected PostgreSQL 15, got " + rs.getString(1));
            }
        }
    }

    @AfterAll
    static void stopPostgres() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/postgres");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("nexus.notify.outbox", () -> "auto");
        registry.add("nexus.execution.dispatcher-enabled", () -> "false");
        registry.add("nexus.execution.worker-enabled", () -> "false");
        registry.add("nexus.execution.quartz-auto-startup", () -> "false");
        registry.add("nexus.cache.mode", () -> "memory");
    }

    @Test
    void productBootWiresJdbcOutboxAfterFlyway() {
        assertNotNull(notificationService);
        assertTrue(outboxPort instanceof JdbcOutboxStore, () -> "expected JdbcOutboxStore, got "
                + (outboxPort == null ? "null" : outboxPort.getClass().getName()));
        List<String> versions = jdbc.queryForList(
                "select version from platform.flyway_schema_history where success and version is not null",
                String.class);
        assertTrue(versions.contains("531"), "G18 V531 missing, got " + versions);
        assertTrue(versions.contains("550"), "Flyway head V550 missing, got " + versions);
        assertTrue(versions.contains("600"), "P4 market V600 missing, got " + versions);
        assertTrue(versions.contains("700"), "P5 connect V700 missing, got " + versions);
        Integer outboxTable = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'platform' and table_name = 'exec_outbox'",
                Integer.class);
        assertEquals(1, outboxTable);
        Integer marketTable = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'market' and table_name = 'app_package'",
                Integer.class);
        assertEquals(1, marketTable);
        Integer apiClientTable = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'integration' and table_name = 'api_client'",
                Integer.class);
        assertEquals(1, apiClientTable);
    }

    @Test
    void productBootWiresLocalThenEnvSecretPort() {
        assertTrue(secretPort instanceof LocalThenEnvSecretPort, () -> "expected LocalThenEnvSecretPort, got "
                + (secretPort == null ? "null" : secretPort.getClass().getName()));
        assertEquals("local+env", secretPort.backend());
        secretPort.put("s0.probe", "not-for-git".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("not-for-git", secretPort.getUtf8("s0.probe"));
        secretPort.delete("s0.probe");
    }

    @Test
    void p5SelfUseSlicePersistsIdentityIdempotencyWebhookAndConnectorRun() {
        IssuedClient issued = connectService.issueClient("product-boot", List.of("integration:call"), 2);
        var first = connectService.invoke(
                issued.client().id(), issued.secret(), "integration:call", "boot-1", java.util.Map.of("probe", true));
        var replay = connectService.invoke(
                issued.client().id(), issued.secret(), "integration:call", "boot-1", java.util.Map.of("probe", true));
        assertTrue(first.response().containsKey("accepted"));
        assertTrue(replay.replayed());

        connectService.subscribe("boot sink", "https://hooks.example.test/nexus", List.of("boot.ready"));
        assertEquals(1, connectService.publish("boot-event-1", "boot.ready", java.util.Map.of("ready", true)));
        assertEquals("SUCCEEDED", connectService.runConnector(ConnectService.SAMPLE_CONNECTOR).status());

        assertEquals(1, jdbc.queryForObject("select count(*) from integration.api_client", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from integration.api_idempotency", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from integration.webhook_delivery", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from integration.connector_run", Integer.class));
    }
}
