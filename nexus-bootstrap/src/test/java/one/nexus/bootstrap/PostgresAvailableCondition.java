package one.nexus.bootstrap;

import java.sql.DriverManager;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class PostgresAvailableCondition implements ExecutionCondition {
    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        String url = env("NEXUS_DB_URL", "jdbc:postgresql://127.0.0.1:5432/nexusone");
        String user = env("NEXUS_DB_USER", "nexus");
        String password = env("NEXUS_DB_PASSWORD", "");
        try (var connection = DriverManager.getConnection(url, user, password)) {
            return ConditionEvaluationResult.enabled("PostgreSQL reachable at " + url);
        } catch (Exception ex) {
            return ConditionEvaluationResult.disabled("PostgreSQL not reachable: " + ex.getMessage());
        }
    }

    static String env(String name, String fallback) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
