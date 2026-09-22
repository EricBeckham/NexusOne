package one.nexus.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * 90-day archive copy + 365-day archive expiry on Zonky PostgreSQL 15.
 * Hot table stays append-only.
 */
class JdbcAuditArchiveIT {
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
            boolean v503 = false;
            while (rs.next()) {
                if ("503".equals(rs.getString(1))) {
                    v503 = true;
                }
            }
            assertTrue(v503, "expected Flyway V503 archive expiry grant");
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
    void copiesAgedExportAndLeavesHotRows() throws Exception {
        Instant now = Instant.parse("2026-09-22T15:00:00Z");
        UUID tenant = UuidV7.generate();
        String oldTrace = "0199a1c0-0000-7000-8000-00000000c091";
        String hotTrace = "0199a1c0-0000-7000-8000-00000000c010";
        port.append(agedExport(tenant, oldTrace, now.minus(AuditRetentionPolicy.HOT_RETENTION).minusSeconds(120)));
        port.append(agedExport(tenant, hotTrace, now.minusSeconds(3600)));
        assertEquals(2, countHot());

        AuditArchiveJob job = new AuditArchiveJob(archive, Clock.fixed(now, ZoneOffset.UTC));
        ArchiveRunResult first = job.run();
        assertEquals(1, first.copied());
        assertEquals(0, first.purged());
        assertEquals(1, first.archiveSize());
        assertTrue(first.purgeImplemented());
        assertEquals(2, countHot());
        assertEquals(1, countArchive());
        assertEquals(1, archive.findArchivedByTraceId(oldTrace).size());
        assertEquals(0, archive.findArchivedByTraceId(hotTrace).size());
        assertEquals(AuditCategory.EXPORT, archive.findArchivedByTraceId(oldTrace).getFirst().event().category());
        assertTrue(port.verifyChain().intact());
        assertEquals(2, port.findByTraceId(oldTrace).size() + port.findByTraceId(hotTrace).size());

        ArchiveRunResult second = job.run();
        assertEquals(0, second.copied());
        assertEquals(0, second.purged());
        assertEquals(1, second.archiveSize());
        assertEquals(2, countHot());
        assertEquals(1, countArchive());
        ArchiveRunResult purge = archive.purgeExpiredArchive(AuditRetentionPolicy.ARCHIVE_RETENTION, now);
        assertTrue(purge.purgeImplemented());
        assertEquals(0, purge.purged());
        assertEquals(1, purge.archiveSize());
    }

    @Test
    void purgesArchiveOlderThan365DaysAndKeepsNewerAndHot() throws Exception {
        Instant now = Instant.parse("2026-09-22T15:00:00Z");
        UUID tenant = UuidV7.generate();
        String expiredTrace = "0199a1c0-0000-7000-8000-00000000c365";
        String keptTrace = "0199a1c0-0000-7000-8000-00000000c200";
        String hotTrace = "0199a1c0-0000-7000-8000-00000000c010";
        port.append(agedExport(
                tenant, expiredTrace, now.minus(AuditRetentionPolicy.ARCHIVE_RETENTION).minusSeconds(120)));
        port.append(agedExport(
                tenant, keptTrace, now.minus(AuditRetentionPolicy.ARCHIVE_RETENTION).plusSeconds(86_400)));
        port.append(agedExport(tenant, hotTrace, now.minusSeconds(3600)));
        assertEquals(3, countHot());

        ArchiveRunResult copy = archive.copyHotOlderThan(AuditRetentionPolicy.HOT_RETENTION, now);
        assertEquals(2, copy.copied());
        assertEquals(2, copy.archiveSize());
        assertEquals(3, countHot());
        assertEquals(2, countArchive());
        assertEquals(1, archive.findArchivedByTraceId(expiredTrace).size());
        assertEquals(1, archive.findArchivedByTraceId(keptTrace).size());

        ArchiveRunResult purge = archive.purgeExpiredArchive(AuditRetentionPolicy.ARCHIVE_RETENTION, now);
        assertTrue(purge.purgeImplemented());
        assertEquals(1, purge.purged());
        assertEquals(1, purge.archiveSize());
        assertEquals(now.minus(AuditRetentionPolicy.ARCHIVE_RETENTION), purge.cutoff());
        assertEquals(3, countHot());
        assertEquals(1, countArchive());
        assertEquals(0, archive.findArchivedByTraceId(expiredTrace).size());
        assertEquals(1, archive.findArchivedByTraceId(keptTrace).size());
        assertEquals(0, archive.findArchivedByTraceId(hotTrace).size());
        assertEquals(AuditCategory.EXPORT, archive.findArchivedByTraceId(keptTrace).getFirst().event().category());
        assertTrue(port.verifyChain().intact());
        assertEquals(3, countHot());

        ArchiveRunResult second = archive.purgeExpiredArchive(AuditRetentionPolicy.ARCHIVE_RETENTION, now);
        assertTrue(second.purgeImplemented());
        assertEquals(0, second.purged());
        assertEquals(1, second.archiveSize());
        assertEquals(3, countHot());
        assertEquals(1, countArchive());
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

}
