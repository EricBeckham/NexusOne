package one.nexus.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
 * 90-day hot-prefix purge on Zonky PostgreSQL 15. Does not DROP the hot table.
 * {@link JdbcHashChainedAuditPort#append} is not modified here.
 */
class JdbcAuditHotPurgeIT {
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
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT version FROM platform.flyway_schema_history WHERE success AND version IS NOT NULL")) {
            boolean v504 = false;
            while (rs.next()) {
                if ("504".equals(rs.getString(1))) {
                    v504 = true;
                }
            }
            assertTrue(v504, "expected Flyway V504 hot purge grant");
        }
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT to_regclass('platform.audit_record'), "
                                + "has_table_privilege(current_user, 'platform.audit_record', 'DELETE')")) {
            rs.next();
            assertNotNull(rs.getString(1), "hot table must still exist after V504");
            assertTrue(rs.getBoolean(2), "migrating role must be able to DELETE hot rows");
        }
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
    void purgesArchived91DayPrefixAndLeavesYoungAndTable() throws Exception {
        Instant now = Instant.parse("2026-09-22T18:00:00Z");
        UUID tenant = UuidV7.generate();
        String oldTrace = "0199a1c0-0000-7000-8000-00000000j091";
        String youngTrace = "0199a1c0-0000-7000-8000-00000000j010";
        port.append(agedExport(tenant, oldTrace, now.minus(AuditRetentionPolicy.HOT_RETENTION).minusSeconds(120)));
        port.append(agedExport(tenant, youngTrace, now.minusSeconds(3600)));
        assertEquals(2, countHot());
        archive.copyHotOlderThan(AuditRetentionPolicy.HOT_RETENTION, now);
        assertEquals(1, countArchive());

        AuditHotPurgeJob job = new AuditHotPurgeJob(
                AuditHotPurge.jdbc(port, archive, new InMemoryAuditObjectStore(), dataSource),
                Clock.fixed(now, ZoneOffset.UTC));
        HotPurgeRunResult first = job.run();
        assertEquals(1, first.purged());
        assertEquals(1, first.remainingHot());
        assertEquals(0, first.skippedUncopied());
        assertEquals(1, countHot());
        assertEquals(1, countArchive());
        assertEquals(0, port.findByTraceId(oldTrace).size());
        assertEquals(1, port.findByTraceId(youngTrace).size());
        assertEquals(1, archive.findArchivedByTraceId(oldTrace).size());
        assertTrue(port.verifyChain().intact());
        assertHotTablePresent();

        HotPurgeRunResult second = job.run();
        assertEquals(0, second.purged());
        assertEquals(1, second.remainingHot());
        assertEquals(1, countHot());
        assertTrue(port.verifyChain().intact());

        port.append(agedExport(tenant, "0199a1c0-0000-7000-8000-00000000j0nx", now));
        assertEquals(2, countHot());
        assertEquals(2, port.chain().getLast().chainIndex());
        assertTrue(port.verifyChain().intact());
        assertHotTablePresent();
    }

    @Test
    void doesNotPurgeUncopiedOr366DayArchiveOnlyUntilColdExists() throws Exception {
        Instant now = Instant.parse("2026-09-22T18:00:00Z");
        UUID tenant = UuidV7.generate();
        String uncopied = "0199a1c0-0000-7000-8000-00000000j0nc";
        String expired = "0199a1c0-0000-7000-8000-00000000j365";
        port.append(agedExport(tenant, uncopied, now.minus(AuditRetentionPolicy.HOT_RETENTION).minusSeconds(90)));
        AuditHotPurgeJob emptyArchive = new AuditHotPurgeJob(
                AuditHotPurge.jdbc(port, archive, new InMemoryAuditObjectStore(), dataSource),
                Clock.fixed(now, ZoneOffset.UTC));
        HotPurgeRunResult skipped = emptyArchive.run();
        assertEquals(0, skipped.purged());
        assertEquals(1, skipped.skippedUncopied());
        assertEquals(1, countHot());

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE platform.audit_record");
            statement.execute(
                    "UPDATE platform.audit_chain_meta SET next_index = 0, last_hash = decode('"
                            + "0000000000000000000000000000000000000000000000000000000000000000', 'hex'), "
                            + "cipher_suite = 'INTERNATIONAL'");
        }
        port.append(agedExport(
                tenant, expired, now.minus(AuditRetentionPolicy.ARCHIVE_RETENTION).minusSeconds(120)));
        archive.copyHotOlderThan(AuditRetentionPolicy.HOT_RETENTION, now);
        CountingObjectStorage counting = new CountingObjectStorage();
        AuditObjectStore store = new ObjectStorageAuditStore(
                "nexus-files", counting::put, counting::get, counting::exists, counting::listKeys);
        AuditHotPurgeJob job = new AuditHotPurgeJob(
                AuditHotPurge.jdbc(port, archive, store, dataSource), Clock.fixed(now, ZoneOffset.UTC));
        HotPurgeRunResult blocked = job.run();
        assertEquals(0, blocked.purged());
        assertEquals(1, countHot());
        assertEquals(1, countArchive());

        AuditColdArchive.jdbc(archive, store).copyEligible(now);
        HotPurgeRunResult afterCold = job.run();
        assertEquals(1, afterCold.purged());
        assertEquals(0, afterCold.remainingHot());
        assertEquals(0, countHot());
        assertEquals(1, countArchive());
        assertEquals(1, counting.puts());
        assertTrue(port.verifyChain().intact());
        assertHotTablePresent();
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

    private static void assertHotTablePresent() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT to_regclass('platform.audit_record')")) {
            rs.next();
            assertNotNull(rs.getString(1), "must not DROP platform.audit_record");
        }
    }
}
