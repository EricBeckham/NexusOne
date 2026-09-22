package one.nexus.permission;

import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.jupiter.api.Assumptions;

public final class PostgresAvailable {
    private PostgresAvailable() {}

    public static Connection openOrAbort() {
        String url = System.getenv().getOrDefault(
                "NEXUS_PERM_TEST_JDBC_URL", "jdbc:postgresql://127.0.0.1:5432/nexus_p2_perm_test");
        String user = System.getenv().getOrDefault("NEXUS_PERM_TEST_JDBC_USER", "ubuntu");
        String password = System.getenv().getOrDefault("NEXUS_PERM_TEST_JDBC_PASSWORD", "");
        try {
            Connection connection = DriverManager.getConnection(url, user, password);
            String version = version(connection);
            if (version == null || !version.startsWith("PostgreSQL 15.")) {
                throw new AssertionError("M0-05 retest requires PostgreSQL 15.x, got: " + version);
            }
            return connection;
        } catch (AssertionError ex) {
            throw ex;
        } catch (Exception ex) {
            Assumptions.abort("PostgreSQL is not available: " + ex.getMessage());
            return null;
        }
    }

    public static String version(Connection connection) {
        try (var rs = connection.createStatement().executeQuery("select version()")) {
            rs.next();
            return rs.getString(1);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
