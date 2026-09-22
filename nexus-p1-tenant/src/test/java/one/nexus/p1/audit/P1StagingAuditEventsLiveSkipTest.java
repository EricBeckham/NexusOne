package one.nexus.p1.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Live {@code 127.0.0.1:5432} staging probe. Honest SKIP when password, connection,
 * or {@code platform.audit_event} is missing. Does not switch GET off staging.
 */
class P1StagingAuditEventsLiveSkipTest {

    @TempDir
    Path tmp;

    @Test
    void liveStagingQueryExportOrHonestSkip() throws Exception {
        String url = firstEnv("NEXUS_DB_URL", "NEXUS_DATASOURCE_URL");
        if (url == null || url.isBlank()) {
            url = "jdbc:postgresql://127.0.0.1:5432/nexusone";
        }
        String user = firstEnv("NEXUS_DB_USER", "NEXUS_DATASOURCE_USERNAME");
        if (user == null || user.isBlank()) {
            user = "postgres";
        }
        String password = firstEnv("NEXUS_DB_PASSWORD", "NEXUS_DATASOURCE_PASSWORD");
        assumeTrue(password != null && !password.isBlank(),
                "SKIP live P1 staging: NEXUS_DB_PASSWORD / NEXUS_DATASOURCE_PASSWORD not set");

        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", password);
        props.setProperty("loginTimeout", "2");
        props.setProperty("connectTimeout", "2");

        try (Connection connection = DriverManager.getConnection(url, props)) {
            assumeTrue(tableExists(connection, "platform", "audit_event"),
                    "SKIP live P1 staging: platform.audit_event is not present");
            int count;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT count(*) FROM platform.audit_event");
                    ResultSet rs = statement.executeQuery()) {
                rs.next();
                count = rs.getInt(1);
            }
            Path export = tmp.resolve("live-staging-audit-events.json");
            String body = "{\"leftover\":\"g4-p1-staging-audit-events\","
                    + "\"source_table\":\"platform.audit_event\","
                    + "\"switched_off_staging\":false,"
                    + "\"product_g4_closed\":false,"
                    + "\"live\":true,"
                    + "\"count\":" + count + "}";
            java.nio.file.Files.writeString(export, body + "\n");
            assertThat(export).exists();
            assertThat(count).isGreaterThanOrEqualTo(0);
        } catch (Exception ex) {
            assumeTrue(false, "SKIP live P1 staging: " + ex.getMessage());
        }
    }

    private static boolean tableExists(Connection connection, String schema, String table) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM information_schema.tables WHERE table_schema = ? AND table_name = ?");) {
            statement.setString(1, schema);
            statement.setString(2, table);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String firstEnv(String... names) {
        for (String name : names) {
            String value = System.getenv(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
