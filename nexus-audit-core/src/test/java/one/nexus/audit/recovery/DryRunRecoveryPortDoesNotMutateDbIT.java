package one.nexus.audit.recovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Dry-run must not mutate a PostgreSQL 15 stand-in database. Dedicated Zonky
 * cluster only — never port {@code 5432}, never {@code haopan_platform}.
 */
class DryRunRecoveryPortDoesNotMutateDbIT {
    private static final String TOKEN = "g6-recovery-port-dry-run-sentinel";

    private static EmbeddedPostgres postgres;

    @BeforeAll
    static void startPostgres() throws Exception {
        postgres = EmbeddedPostgres.builder().start();
        try (Connection connection = postgres.getPostgresDatabase().getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SHOW server_version")) {
            rs.next();
            assertTrue(rs.getString(1).startsWith("15."), "expected PostgreSQL 15, got " + rs.getString(1));
        }
        assertNotEquals(5432, postgres.getPort(), "Zonky must not bind the product port 5432");
        try (Connection connection = postgres.getPostgresDatabase().getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA g6_recovery_port_witness");
            statement.execute(
                    "CREATE TABLE g6_recovery_port_witness.sentinel (token text PRIMARY KEY, n int NOT NULL)");
            statement.execute(
                    "INSERT INTO g6_recovery_port_witness.sentinel(token, n) VALUES ('" + TOKEN + "', 1)");
        }
    }

    @AfterAll
    static void stopPostgres() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    @Test
    void dryRunDoesNotMutateWitnessOrCreateHaopan() throws Exception {
        Witness before = snapshot();
        RecoveryPort port = new DryRunRecoveryPort();
        RecoveryDryRunResult pg = port.dryRunPostgres();
        RecoveryDryRunResult objects = port.dryRunObjectStore();
        Witness after = snapshot();

        assertEquals(before, after, "dry-run must not mutate the stand-in database");
        assertFalse(pg.executed());
        assertFalse(pg.profileAccepted());
        assertFalse(objects.executed());
        assertFalse(objects.profileAccepted());
        assertFalse(after.databases().stream().anyMatch(name -> name.toLowerCase().contains("haopan")));
        assertEquals(1, after.sentinelCount());
        assertEquals(1, after.sentinelN());
    }

    private static Witness snapshot() throws Exception {
        try (Connection connection = postgres.getPostgresDatabase().getConnection();
                Statement statement = connection.createStatement()) {
            int sentinelCount;
            int sentinelN;
            try (ResultSet rs = statement.executeQuery(
                    "SELECT count(*), coalesce(max(n), 0) FROM g6_recovery_port_witness.sentinel WHERE token = '"
                            + TOKEN
                            + "'")) {
                rs.next();
                sentinelCount = rs.getInt(1);
                sentinelN = rs.getInt(2);
            }
            List<String> databases = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery("SELECT datname FROM pg_database ORDER BY 1")) {
                while (rs.next()) {
                    databases.add(rs.getString(1));
                }
            }
            return new Witness(sentinelCount, sentinelN, List.copyOf(databases));
        }
    }

    private record Witness(int sentinelCount, int sentinelN, List<String> databases) {}
}
