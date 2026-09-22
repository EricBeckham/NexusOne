package one.nexus.p3;

import java.sql.DriverManager;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class PostgresAvailable implements ExecutionCondition {
    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        String url = env("NEXUS_TEST_DATASOURCE_URL", env("NEXUS_DB_URL", "jdbc:postgresql://127.0.0.1:5432/nexus_p3_test"));
        String user = env("NEXUS_DB_USER", "nexus");
        String password = env("NEXUS_DB_PASSWORD", "");
        try (var connection = DriverManager.getConnection(url, user, password);
                var statement = connection.createStatement();
                var rs = statement.executeQuery("SHOW server_version")) {
            if (!rs.next()) {
                return ConditionEvaluationResult.disabled("PostgreSQL did not report server_version");
            }
            String version = rs.getString(1);
            if (!version.startsWith("15.")) {
                return ConditionEvaluationResult.disabled("PostgreSQL is not 15.x: " + version);
            }
            return ConditionEvaluationResult.enabled("PostgreSQL 15 reachable at " + url);
        } catch (Exception ex) {
            return ConditionEvaluationResult.disabled("PostgreSQL not reachable: " + ex.getMessage());
        }
    }

    static String env(String name, String fallback) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
