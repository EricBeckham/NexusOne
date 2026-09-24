package one.nexus.bootstrap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import one.nexus.crypto.CryptoPort;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import one.nexus.crypto.JdbcEncryptedSecretAdapter;
import one.nexus.crypto.LocalThenEnvSecretPort;
import one.nexus.crypto.SecretPort;
import one.nexus.event.OutboxPort;
import one.nexus.event.jdbc.JdbcOutboxStore;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import one.nexus.notification.NotificationService;
import one.nexus.p4.market.MarketModels.AppVersion;
import one.nexus.p4.market.MarketModels.VersionStatus;
import one.nexus.p4.market.MarketService;
import one.nexus.p5.connect.ConnectModels.ConnectorRun;
import one.nexus.p5.connect.ConnectModels.IssuedClient;
import one.nexus.p5.connect.ConnectModels.WebhookSubscription;
import one.nexus.p5.connect.ConnectRepository;
import one.nexus.p5.connect.ConnectService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

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

    @Autowired
    ConnectRepository connectRepository;

    @Autowired
    MarketService marketService;

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
        assertTrue(versions.contains("004"), "M1 V004 durable secret storage missing, got " + versions);
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
    void productBootWiresDurableThenEnvironmentSecretPort() {
        assertTrue(secretPort instanceof LocalThenEnvSecretPort, () -> "expected LocalThenEnvSecretPort, got "
                + (secretPort == null ? "null" : secretPort.getClass().getName()));
        assertEquals("jdbc+env", secretPort.backend());
        assertTrue(((LocalThenEnvSecretPort) secretPort).local() instanceof JdbcEncryptedSecretAdapter);
        secretPort.put("s0.probe", "not-for-git".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("not-for-git", secretPort.getUtf8("s0.probe"));
        secretPort.delete("s0.probe");
    }

    @Test
    void durableSecretSurvivesAdapterAndCryptoPortReconstructionAndRotationOverwritesCiphertext() {
        byte[] masterKey = new byte[32];
        Arrays.fill(masterKey, (byte) 0x11);
        String name = "m2.probe." + UUID.randomUUID();
        byte[] original = "m2-original-secret-value".getBytes(StandardCharsets.UTF_8);
        byte[] rotated = "m2-rotated-secret-value".getBytes(StandardCharsets.UTF_8);
        try {
            CryptoPort firstCrypto = JcaCryptoPort.fromMasterKey(CryptoSuite.INTERNATIONAL, "m2-test", 1, masterKey);
            JdbcEncryptedSecretAdapter first = new JdbcEncryptedSecretAdapter(jdbc.getDataSource(), firstCrypto);
            var firstRef = first.put(name, original);
            byte[] oldCiphertext = jdbc.queryForObject(
                    "select ciphertext from platform.secret_ciphertext where secret_name=?", byte[].class, name);
            assertFalse(Arrays.equals(original, oldCiphertext), "database stores an encrypted envelope, not plaintext");
            assertFalse(new String(oldCiphertext, StandardCharsets.UTF_8).contains(new String(original, StandardCharsets.UTF_8)));
            assertEquals(1, firstRef.secretVersion());
            assertFalse(firstRef.toString().contains(new String(original, StandardCharsets.UTF_8)));

            CryptoPort restartedCrypto = JcaCryptoPort.fromMasterKey(CryptoSuite.INTERNATIONAL, "m2-test", 1, masterKey);
            JdbcEncryptedSecretAdapter restarted = new JdbcEncryptedSecretAdapter(jdbc.getDataSource(), restartedCrypto);
            assertArrayEquals(original, restarted.get(name));
            assertEquals(1, restarted.find(name).orElseThrow().secretVersion());

            var rotatedRef = restarted.put(name, rotated);
            byte[] currentCiphertext = jdbc.queryForObject(
                    "select ciphertext from platform.secret_ciphertext where secret_name=?", byte[].class, name);
            assertEquals(2, rotatedRef.secretVersion());
            assertFalse(Arrays.equals(oldCiphertext, currentCiphertext), "rotation replaces the previous envelope");
            assertArrayEquals(rotated, first.get(name));
            assertFalse(Arrays.equals(original, first.get(name)), "the prior value is no longer readable by name");
        } finally {
            new JdbcEncryptedSecretAdapter(jdbc.getDataSource(),
                    JcaCryptoPort.fromMasterKey(CryptoSuite.INTERNATIONAL, "m2-test", 1, masterKey)).delete(name);
            Arrays.fill(masterKey, (byte) 0);
        }
    }

    @Test
    void p5SelfUseSlicePersistsIdentityIdempotencyWebhookAndConnectorRun() {
        IsolationKernel testIsolation = IsolationKernel.create(
                new TableRegistry().freeze(), Set.of(), new CollectingAuditSink());
        UUID tenantId = UUID.fromString("01900000-0000-7000-8000-000000000001");
        var context = testIsolation.tenants().issue(
                new AuthenticatedMembership(
                        Actor.person("boot-test-person"),
                        tenantId.toString(),
                        "boot-test-membership",
                        Set.of(tenantId.toString())),
                "boot-test-trace");
        AccessContextHolder.call(context, () -> {
            IssuedClient issued = connectService.issueClient("product-boot", List.of("integration:call"), 2);
            var first = connectService.invoke(
                    issued.client().id(), issued.secret(), "integration:call", "boot-1", java.util.Map.of("probe", true));
            var replay = connectService.invoke(
                    issued.client().id(), issued.secret(), "integration:call", "boot-1", java.util.Map.of("probe", true));
            assertTrue(first.response().containsKey("accepted"));
            assertTrue(replay.replayed());

            connectService.subscribe("boot sink", "https://hooks.example.test/nexus", List.of("boot.ready"));
            assertEquals(1, connectService.publish("boot-event-1", "boot.ready", java.util.Map.of("ready", true)));
            assertEquals(1, connectRepository.dueDeliveries(Instant.now().plusSeconds(60), 20).size());
            ConnectorRun run = connectService.runConnector(ConnectService.SAMPLE_CONNECTOR);
            assertEquals("SUCCEEDED", run.status());

            assertEquals(1, jdbc.queryForObject(
                    "select count(*) from integration.api_client where id=?", Integer.class, issued.client().id()));
            assertEquals(1, jdbc.queryForObject(
                    "select count(*) from integration.api_idempotency where client_id=? and idempotency_key=?",
                    Integer.class, issued.client().id(), "boot-1"));
            assertEquals(2, jdbc.queryForObject(
                    "select count(*) from integration.api_call_log where client_id=?",
                    Integer.class, issued.client().id()));
            assertEquals(1, jdbc.queryForObject(
                    "select count(*) from integration.webhook_delivery where event_id=?",
                    Integer.class, "boot-event-1"));
            assertEquals(1, jdbc.queryForObject(
                    "select count(*) from integration.connector_run where id=?", Integer.class, run.id()));
            return null;
        });
    }

    @Test
    void p4AndP5ReadModelsStayInsideTheIssuedTenant() {
        UUID alpha = UUID.fromString("01900000-0000-7000-8000-000000000011");
        UUID beta = UUID.fromString("01900000-0000-7000-8000-000000000012");
        createBusinessTenant(alpha, "b1_alpha");
        createBusinessTenant(beta, "b1_beta");
        Instant now = Instant.parse("2026-09-24T00:00:00Z");
        var app = marketService.createApp("b1_tenant_read", "B1 Tenant Read", "tenant-read fixture");
        AppVersion published = marketService.createVersion(app.id(), "1.0.0", "fixture");
        published = marketService.transition(published.id(), VersionStatus.IN_REVIEW);
        published = marketService.transition(published.id(), VersionStatus.APPROVED);
        published = marketService.transition(published.id(), VersionStatus.PUBLISHED);
        var sku = marketService.defineSku(
                "B1_TENANT_READ",
                app.id(),
                "Tenant Read",
                Map.of("apps", new one.nexus.p4.market.MarketModels.EntitlementSpec(true, 1, Map.of())));
        UUID appId = app.id();
        UUID versionId = published.id();
        UUID installationId = UUID.fromString("01900000-0000-7000-8000-000000000043");
        UUID subscriptionId = UUID.fromString("01900000-0000-7000-8000-000000000044");
        jdbc.update("""
                INSERT INTO market.subscription(id,tenant_id,sku_code,status,started_at,ended_at)
                VALUES(?,?,?,'ACTIVE',?,NULL)
                """,
                subscriptionId, alpha, sku.code(), Timestamp.from(now));
        jdbc.update("""
                INSERT INTO market.installation(id,tenant_id,app_id,version_id,status,installed_at,updated_at)
                VALUES(?,?,?,?,'INSTALLED',?,?)
                """,
                installationId, alpha, appId, versionId, Timestamp.from(now), Timestamp.from(now));

        record TenantSeed(UUID installationId, UUID subscriptionId, IssuedClient client, WebhookSubscription webhook) {}
        var seed = AccessContextHolder.call(issuedTenantContext(alpha), () -> {
            IssuedClient client = connectService.issueClient("alpha-client", List.of("integration:call"), 10);
            WebhookSubscription webhook = connectService.subscribe(
                    "alpha-webhook", "https://hooks.example.test/alpha", List.of("b1.record.changed"));
            assertEquals(1, connectService.publish(
                    "b1-alpha-event", "b1.record.changed", Map.of("record", "alpha")));
            connectService.invoke(
                    client.client().id(),
                    client.secret(),
                    "integration:call",
                    "alpha-call-1",
                    Map.of("record", "alpha"));
            connectService.runConnector(ConnectService.SAMPLE_CONNECTOR);
            assertTrue(marketService.apps().stream().anyMatch(item -> item.id().equals(appId)));
            assertEquals(appId, marketService.getApp(appId).id());
            assertEquals(versionId, marketService.versions(appId).getFirst().id());
            assertEquals(versionId, marketService.getVersion(versionId).id());
            assertTrue(marketService.skus().stream().anyMatch(item -> item.code().equals(sku.code())));
            assertEquals(sku.code(), marketService.getSku(sku.code()).code());
            assertEquals(1, marketService.installations().size());
            assertEquals(1, marketService.subscriptions().size());
            assertEquals(1, connectService.clients().size());
            assertEquals(1, connectService.subscriptions().size());
            assertEquals(1, connectService.deliveries(webhook.id()).size());
            assertEquals(1, connectService.callLogs().size());
            assertEquals(1, connectService.runs().size());
            return new TenantSeed(installationId, subscriptionId, client, webhook);
        });

        AccessContextHolder.call(issuedTenantContext(beta), () -> {
            assertTrue(marketService.installations().isEmpty());
            assertTrue(marketService.subscriptions().isEmpty());
            assertEquals(404, assertThrows(
                    ResponseStatusException.class,
                    () -> marketService.getInstallation(seed.installationId())).getStatusCode().value());
            assertEquals(404, assertThrows(
                    ResponseStatusException.class,
                    () -> marketService.getSubscription(seed.subscriptionId())).getStatusCode().value());
            assertTrue(connectService.clients().isEmpty());
            assertTrue(connectService.subscriptions().isEmpty());
            assertTrue(connectService.callLogs().isEmpty());
            assertTrue(connectService.runs().isEmpty());
            assertEquals(404, assertThrows(
                    ResponseStatusException.class,
                    () -> connectService.client(seed.client().client().id())).getStatusCode().value());
            assertEquals(404, assertThrows(
                    ResponseStatusException.class,
                    () -> connectService.subscription(seed.webhook().id())).getStatusCode().value());
            return null;
        });
    }

    private void createBusinessTenant(UUID tenantId, String code) {
        Instant now = Instant.parse("2026-09-24T00:00:00Z");
        jdbc.update("""
                INSERT INTO platform.tenant(
                    id,code,name,kind,status,legal_name,created_at,created_by,updated_at,updated_by,deleted_at,version)
                VALUES(?,?,?,'BUSINESS','ENABLED',NULL,?,?,?, ?,NULL,1)
                """,
                tenantId,
                code,
                code,
                Timestamp.from(now),
                tenantId,
                Timestamp.from(now),
                tenantId);
    }

    private static one.nexus.isolation.api.TenantContext issuedTenantContext(UUID tenantId) {
        IsolationKernel testIsolation = IsolationKernel.create(
                new TableRegistry().freeze(), Set.of(), new CollectingAuditSink());
        return testIsolation.tenants().issue(
                new AuthenticatedMembership(
                        Actor.person("boot-tenant-reader"),
                        tenantId.toString(),
                        tenantId.toString(),
                        Set.of(tenantId.toString())),
                "boot-tenant-read-trace");
    }
}
