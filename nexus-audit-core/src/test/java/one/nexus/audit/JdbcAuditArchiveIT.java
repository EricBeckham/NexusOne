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
 * 90-day archive copy on Zonky PostgreSQL 15. Hot table stays append-only.
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
        assertEquals(1, first.archiveSize());
        assertFalse(first.purgeImplemented());
        assertEquals(2, countHot());
        assertEquals(1, countArchive());
        assertEquals(1, archive.findArchivedByTraceId(oldTrace).size());
        assertEquals(0, archive.findArchivedByTraceId(hotTrace).size());
        assertEquals(AuditCategory.EXPORT, archive.findArchivedByTraceId(oldTrace).getFirst().event().category());
        assertTrue(port.verifyChain().intact());
        assertEquals(2, port.findByTraceId(oldTrace).size() + port.findByTraceId(hotTrace).size());

        ArchiveRunResult second = job.run();
        assertEquals(0, second.copied());
        assertEquals(1, second.archiveSize());
        assertEquals(2, countHot());
        assertEquals(1, countArchive());
        assertFalse(archive.purgeExpiredArchive(AuditRetentionPolicy.ARCHIVE_RETENTION, now).purgeImplemented());
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
