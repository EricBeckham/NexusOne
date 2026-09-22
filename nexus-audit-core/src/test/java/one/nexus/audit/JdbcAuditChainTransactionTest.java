package one.nexus.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import one.nexus.common.id.UuidV7;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link JdbcHashChainedAuditPort#append} joins an ambient Spring transaction.
 * Runs on Zonky PostgreSQL 15.x so the agent VM does not need a live listener.
 */
class JdbcAuditChainTransactionTest {
    private static EmbeddedPostgres postgres;
    private static DataSource dataSource;
    private static JdbcHashChainedAuditPort port;
    private static TransactionTemplate transactions;

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
        transactions = new TransactionTemplate(new JdbcTransactionManager(dataSource));
    }

    @BeforeEach
    void resetChain() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE platform.audit_record");
            statement.execute(
                    "UPDATE platform.audit_chain_meta SET next_index = 0, last_hash = decode('"
                            + "0000000000000000000000000000000000000000000000000000000000000000', 'hex'), "
                            + "cipher_suite = 'INTERNATIONAL'");
        }
    }

    @Test
    void appendJoinsSpringTransactionAndRollsBackWithChainHead() {
        UUID tenant = UuidV7.generate();
        String trace = "0199a1c0-0000-7000-8000-00000000b001";
        assertThrows(IllegalStateException.class, () -> transactions.execute(status -> {
            port.append(AuditCaptures.data(
                    "p3.path.submitted",
                    true,
                    tenant,
                    tenant,
                    "ledger_record",
                    UuidV7.generate().toString(),
                    "form submit wrote physical row",
                    trace,
                    Map.of("form_id", "rollback")));
            throw new IllegalStateException("force rollback");
        }));
        assertEquals(0, countByTrace(trace));
        assertEquals(0, nextIndex());
        assertTrue(port.verifyChain().intact());
    }

    @Test
    void appendCommitLeavesRecordAndIntactChain() {
        UUID tenant = UuidV7.generate();
        String trace = "0199a1c0-0000-7000-8000-00000000b002";
        transactions.execute(status -> {
            port.append(AuditCaptures.data(
                    "p3.path.submitted", true, tenant, tenant, "ledger_record", "r1", "ok", trace, Map.of()));
            port.append(AuditCaptures.approval(
                    "p3.approval.started", true, tenant, tenant, "p1", "started", trace, Map.of()));
            return null;
        });
        assertEquals(2, countByTrace(trace));
        assertEquals(2, nextIndex());
        HashChainVerification verification = port.verifyChain();
        assertTrue(verification.intact(), verification.failure());
        assertEquals(2, verification.checked());
    }

    @Test
    void appendNewTransactionSurvivesBusinessRollback() {
        UUID tenant = UuidV7.generate();
        String trace = "0199a1c0-0000-7000-8000-00000000b003";
        assertThrows(IllegalStateException.class, () -> transactions.execute(status -> {
            port.appendNewTransaction(AuditCaptures.guardReject(new GuardRejectCapture(
                    UuidV7.generate(),
                    java.time.Instant.parse("2026-09-22T01:00:00Z"),
                    "P1.M02.001",
                    tenant.toString(),
                    tenant.toString(),
                    "tenant.create",
                    "missing context",
                    trace,
                    Map.of())));
            throw new IllegalStateException("force rollback");
        }));
        assertEquals(1, countByTrace(trace));
        assertEquals(1, nextIndex());
        assertTrue(port.verifyChain().intact());
    }

    @Test
    void committedAppendAfterRollbackContinuesGaplessChain() {
        UUID tenant = UuidV7.generate();
        String rolled = "0199a1c0-0000-7000-8000-00000000b004";
        String kept = "0199a1c0-0000-7000-8000-00000000b005";
        assertThrows(IllegalStateException.class, () -> transactions.execute(status -> {
            port.append(AuditCaptures.data(
                    "p3.path.submitted", true, tenant, tenant, "ledger_record", "r0", "boom", rolled, Map.of()));
            throw new IllegalStateException("force rollback");
        }));
        transactions.execute(status -> {
            port.append(AuditCaptures.data(
                    "p3.path.submitted", true, tenant, tenant, "ledger_record", "r1", "ok", kept, Map.of()));
            return null;
        });
        assertEquals(0, countByTrace(rolled));
        assertEquals(1, countByTrace(kept));
        assertEquals(0, port.findByTraceId(kept).getFirst().chainIndex());
        HashChainVerification verification = port.verifyChain();
        assertTrue(verification.intact(), verification.failure());
        assertEquals(1, verification.checked());
    }

    @Test
    void exportAppendJoinsSpringTransactionAndIsQueryableByTrace() {
        UUID tenant = UuidV7.generate();
        String rolled = "0199a1c0-0000-7000-8000-00000000e001";
        String kept = "0199a1c0-0000-7000-8000-00000000e002";
        assertThrows(IllegalStateException.class, () -> transactions.execute(status -> {
            port.append(AuditCaptures.export(
                    AuditCaptures.EXPORT_SUBMITTED,
                    true,
                    tenant,
                    tenant,
                    "export_task",
                    "ledger.export.csv",
                    "boom",
                    rolled,
                    Map.of("ledger_code", "leave")));
            throw new IllegalStateException("force rollback");
        }));
        assertEquals(0, countByTrace(rolled));
        transactions.execute(status -> {
            port.append(AuditCaptures.export(
                    AuditCaptures.EXPORT_SUBMITTED,
                    true,
                    tenant,
                    tenant,
                    "export_task",
                    "ledger.export.csv",
                    "ok",
                    kept,
                    Map.of("ledger_code", "leave")));
            port.append(AuditCaptures.export(
                    AuditCaptures.EXPORT_FILE_WRITTEN,
                    true,
                    tenant,
                    tenant,
                    "export_file",
                    "ledger.export.csv",
                    "ok",
                    kept,
                    Map.of("bytes", "6")));
            return null;
        });
        assertEquals(2, countByTrace(kept));
        assertEquals(AuditCaptures.EXPORT_SUBMITTED, port.findByTraceId(kept).get(0).event().eventType());
        assertEquals(AuditCaptures.EXPORT_FILE_WRITTEN, port.findByTraceId(kept).get(1).event().eventType());
        assertTrue(port.verifyChain().intact());

        AuditHotRetentionJob job = new AuditHotRetentionJob(port);
        AuditRetentionScan scan = job.scan();
        assertEquals(2, scan.hotCount());
        assertEquals(0, scan.pastHotCount());
        assertTrue(scan.archiveDeferred());
        assertEquals(2, countByTrace(kept));
        AuditRetentionScan aged = job.scan(java.time.Instant.now().plus(java.time.Duration.ofDays(91)));
        assertEquals(2, aged.pastHotCount());
        assertEquals(2, countByTrace(kept));
        assertTrue(port.verifyChain().intact());
    }

    private static int countByTrace(String traceId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT count(*) FROM platform.audit_record WHERE trace_id = ?")) {
            statement.setString(1, traceId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static long nextIndex() {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT next_index FROM platform.audit_chain_meta WHERE id = 1")) {
            rs.next();
            return rs.getLong(1);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
