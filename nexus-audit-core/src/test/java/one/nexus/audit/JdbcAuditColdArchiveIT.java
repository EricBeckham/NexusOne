package one.nexus.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import one.nexus.common.audit.AuditCategory;
import one.nexus.common.audit.AuditEvent;
import one.nexus.common.id.UuidV7;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import one.nexus.file.infra.CountingObjectStorage;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Object-store cold archive on Zonky PostgreSQL 15 + CountingObjectStorage Fake.
 * Hot {@code platform.audit_record} stays append-only.
 */
class JdbcAuditColdArchiveIT {
    private static EmbeddedPostgres postgres;
    private static DataSource dataSource;
    private static JdbcHashChainedAuditPort port;
    private static JdbcAuditArchivePort archive;

    @BeforeAll
    static void startPostgres() throws Exception {
        postgres = EmbeddedPostgres.builder().start();
        try (Connection connection = postgres.getPostgresDatabase().getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SHOW server_version")) {
            rs.next();
            String version = rs.getString(1);
            assertTrue(version.startsWith("15."), "expected PostgreSQL 15, got " + version);
        }
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL("jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/postgres");
        ds.setUser("postgres");
        ds.setPassword("postgres");
        dataSource = ds;
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .schemas("platform")
                .defaultSchema("platform")
                .createSchemas(true)
                .load()
                .migrate();
        port = new JdbcHashChainedAuditPort(dataSource, JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        archive = new JdbcAuditArchivePort(dataSource);
    }

    @BeforeEach
    void reset() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE platform.audit_record");
            statement.execute("TRUNCATE platform.audit_record_archive");
            statement.execute(
                    "UPDATE platform.audit_chain_meta SET next_index = 0, last_hash = decode('"
                            + "0000000000000000000000000000000000000000000000000000000000000000', 'hex'), "
                            + "cipher_suite = 'INTERNATIONAL'");
        }
    }

    @Test
    void copiesArchivedRowsToCountingStorageUnderTenantAndPlatformPrefix() throws Exception {
        Instant now = Instant.parse("2026-09-22T17:00:00Z");
        UUID tenant = UuidV7.generate();
        String tenantTrace = "0199a1c0-0000-7000-8000-00000000j091";
        String platformTrace = "0199a1c0-0000-7000-8000-00000000j0pl";
        String youngTrace = "0199a1c0-0000-7000-8000-00000000j010";
        port.append(agedExport(tenant, tenantTrace, now.minus(AuditRetentionPolicy.HOT_RETENTION).minusSeconds(120)));
        port.append(agedPlatform(platformTrace, now.minus(AuditRetentionPolicy.HOT_RETENTION).minusSeconds(90)));
        port.append(agedExport(tenant, youngTrace, now.minusSeconds(3600)));
        assertEquals(3, countHot());

        ArchiveRunResult copied = archive.copyHotOlderThan(AuditRetentionPolicy.HOT_RETENTION, now);
        assertEquals(2, copied.copied());
        assertEquals(3, countHot());

        CountingObjectStorage counting = new CountingObjectStorage();
        AuditColdArchive cold = AuditColdArchive.jdbc(archive, bind(counting));
        AuditColdArchiveJob job = new AuditColdArchiveJob(cold, Clock.fixed(now, ZoneOffset.UTC));
        ColdArchiveRunResult first = job.run();
        assertEquals(2, first.written());
        assertEquals(0, first.skipped());
        assertEquals(2, counting.puts());
        assertEquals(3, countHot());
        assertEquals(2, countArchive());

        List<String> tenantKeys = cold.listTenant(tenant);
        assertEquals(1, tenantKeys.size());
        assertTrue(tenantKeys.getFirst().startsWith("tenants/" + tenant + "/audit/cold/"));
        assertTrue(tenantKeys.getFirst().endsWith(".json"));
        List<String> platformKeys = cold.listPlatform();
        assertEquals(1, platformKeys.size());
        assertTrue(platformKeys.getFirst().startsWith("platform/audit/cold/"));
        assertFalse(cold.listTenant(UuidV7.generate()).stream().anyMatch(tenantKeys::contains));

        List<ChainedAuditEvent> restored = cold.restoreByTraceId(tenantTrace);
        assertEquals(1, restored.size());
        assertEquals(AuditCategory.EXPORT, restored.getFirst().event().category());
        assertEquals("submit", restored.getFirst().event().details().get("action"));
        assertEquals(0, cold.restoreByTraceId(youngTrace).size());
        assertEquals(platformTrace, cold.restore(platformKeys.getFirst()).event().traceId());
        assertTrue(port.verifyChain().intact());

        ColdArchiveRunResult second = job.run();
        assertEquals(0, second.written());
        assertEquals(2, second.skipped());
        assertEquals(2, counting.puts());
        assertEquals(3, countHot());
    }

    @Test
    void copiesPurgedArchiveEligibleHotAndLeavesHotAppendOnly() throws Exception {
        Instant now = Instant.parse("2026-09-22T17:00:00Z");
        UUID tenant = UuidV7.generate();
        String expiredTrace = "0199a1c0-0000-7000-8000-00000000j365";
        port.append(agedExport(
                tenant, expiredTrace, now.minus(AuditRetentionPolicy.ARCHIVE_RETENTION).minusSeconds(120)));
        archive.copyHotOlderThan(AuditRetentionPolicy.HOT_RETENTION, now);
        ArchiveRunResult purge = archive.purgeExpiredArchive(AuditRetentionPolicy.ARCHIVE_RETENTION, now);
        assertEquals(1, purge.purged());
        assertEquals(0, countArchive());
        assertEquals(1, countHot());

        CountingObjectStorage counting = new CountingObjectStorage();
        AuditColdArchive cold = AuditColdArchive.jdbc(archive, bind(counting));
        ColdArchiveRunResult result = cold.copyEligible(now);
        assertEquals(1, result.written());
        assertEquals(1, cold.listTenant(tenant).size());
        assertEquals(1, cold.restoreByTraceId(expiredTrace).size());
        assertEquals(1, countHot());
        assertTrue(port.verifyChain().intact());
    }

    private static AuditObjectStore bind(CountingObjectStorage storage) {
        return new ObjectStorageAuditStore(
                "nexus-files", storage::put, storage::get, storage::exists, storage::listKeys);
    }

    private static AuditEvent agedExport(UUID tenant, String trace, Instant occurredAt) {
        return new AuditEvent(
                UuidV7.generate(),
                AuditEvent.VERSION,
                occurredAt,
                AuditCategory.EXPORT,
                AuditCaptures.EXPORT_SUBMITTED,
                tenant,
                tenant,
                tenant,
                false,
                "aged export",
                trace,
                trace,
                null,
                AuditEvent.OUTCOME_ACCEPTED,
                "export",
                "ledger.export.csv",
                Map.of("action", "submit"));
    }

    private static AuditEvent agedPlatform(String trace, Instant occurredAt) {
        return new AuditEvent(
                UuidV7.generate(),
                AuditEvent.VERSION,
                occurredAt,
                AuditCategory.ADMIN,
                "p1.admin.init.accepted",
                null,
                UuidV7.generate(),
                null,
                true,
                "platform admin",
                trace,
                trace,
                null,
                AuditEvent.OUTCOME_ACCEPTED,
                "platform",
                "init",
                Map.of("action", "init"));
    }

    private static int countHot() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT count(*) FROM platform.audit_record")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static int countArchive() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT count(*) FROM platform.audit_record_archive")) {
            rs.next();
            return rs.getInt(1);
        }
    }

}
