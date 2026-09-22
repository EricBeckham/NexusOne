package one.nexus.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.common.id.UuidV7;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Hash-chain INSERT on PostgreSQL 15.x. Skips when the database is unreachable.
 * Fails if a reachable server is not PostgreSQL 15.x.
 */
class JdbcAuditChainTest {
    private JdbcHashChainedAuditPort port;
    private String postgresVersion;

    @BeforeEach
    void setUp() {
        String url = System.getenv().getOrDefault(
                "NEXUS_AUDIT_TEST_JDBC_URL", "jdbc:postgresql://127.0.0.1:5432/nexus_m0_11_test");
        String user = System.getenv().getOrDefault("NEXUS_AUDIT_TEST_JDBC_USER", "ubuntu");
        String password = System.getenv().getOrDefault("NEXUS_AUDIT_TEST_JDBC_PASSWORD", "");
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(url);
        dataSource.setUser(user);
        dataSource.setPassword(password);
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement();
                var rs = statement.executeQuery("select version()")) {
            rs.next();
            postgresVersion = rs.getString(1);
        } catch (Exception ex) {
            Assumptions.abort("PostgreSQL is not available for audit JDBC: " + ex.getMessage());
            return;
        }
        org.junit.jupiter.api.Assertions.assertTrue(
                postgresVersion.startsWith("PostgreSQL 15."),
                "M0-11 JDBC audit requires PostgreSQL 15.x, got: " + postgresVersion);
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .schemas("platform")
                .defaultSchema("platform")
                .createSchemas(true)
                .load()
                .migrate();
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute("TRUNCATE platform.audit_record");
            statement.execute(
                    "UPDATE platform.audit_chain_meta SET next_index = 0, last_hash = decode('"
                            + "0000000000000000000000000000000000000000000000000000000000000000', 'hex'), "
                            + "cipher_suite = 'INTERNATIONAL'");
        } catch (Exception ex) {
            throw new IllegalStateException("failed to reset audit tables on " + postgresVersion, ex);
        }
        port = new JdbcHashChainedAuditPort(dataSource, JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
    }

    @Test
    void appendOnlyChainRoundTripOnPostgres15() {
        UUID tenant = UuidV7.generate();
        String trace = "0199a1c0-0000-7000-8000-000000000077";
        port.append(AuditCaptures.login(true, tenant, tenant, "s1", "ok", trace, Map.of("password", "nope")));
        port.appendNewTransaction(
                AuditCaptures.guardReject(new GuardRejectCapture(
                        UuidV7.generate(),
                        java.time.Instant.parse("2026-09-22T01:00:00Z"),
                        "P1.M02.001",
                        tenant.toString(),
                        tenant.toString(),
                        "tenant.create",
                        "missing context",
                        trace,
                        Map.of())));
        assertEquals(2, port.findByTraceId(trace).size());
        assertEquals(SensitiveRedactor.REDACTED, port.chain().getFirst().event().details().get("password"));
        HashChainVerification verification = port.verifyChain();
        assertTrue(verification.intact(), verification.failure());
        assertTrue(postgresVersion.startsWith("PostgreSQL 15."));
        assertEquals(List.of(), port.findByTraceId("missing"));
    }
}
