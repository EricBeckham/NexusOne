package one.nexus.p2;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Enables live Keycloak tests only when explicitly requested and OIDC discovery answers.
 * Default {@code mvn test} stays green without Keycloak, even if a leftover process is listening.
 */
public final class KeycloakAvailableCondition implements ExecutionCondition {
    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        String smoke = System.getenv("NEXUS_KEYCLOAK_SMOKE");
        boolean requested = "1".equals(smoke) || "true".equalsIgnoreCase(smoke);
        if (!requested) {
            return ConditionEvaluationResult.disabled(
                    "live Keycloak smoke is opt-in; set NEXUS_KEYCLOAK_SMOKE=1");
        }
        String issuer = env("NEXUS_KEYCLOAK_ISSUER", "");
        if (issuer.isBlank()) {
            issuer = firstReachable(
                    "http://127.0.0.1:8080/realms/nexus",
                    "http://127.0.0.1:8180/realms/nexus");
        }
        if (issuer == null) {
            return ConditionEvaluationResult.disabled(
                    "Keycloak OIDC discovery is not reachable on 8080 or 8180");
        }
        String url = issuer + "/.well-known/openid-configuration";
        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(3)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body().contains("\"issuer\"")) {
                return ConditionEvaluationResult.enabled("Keycloak OIDC discovery at " + url);
            }
            return ConditionEvaluationResult.disabled(
                    "Keycloak discovery HTTP " + response.statusCode() + " at " + url);
        } catch (Exception ex) {
            return ConditionEvaluationResult.disabled("Keycloak not reachable at " + url + ": " + ex.getMessage());
        }
    }

    static String env(String name, String fallback) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? fallback : value;
    }

    static String firstReachable(String... issuers) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        for (String issuer : issuers) {
            try {
                HttpResponse<String> response = http.send(
                        HttpRequest.newBuilder(URI.create(issuer + "/.well-known/openid-configuration"))
                                .timeout(Duration.ofSeconds(3)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200 && response.body().contains("\"issuer\"")) {
                    return issuer;
                }
            } catch (Exception ignored) {
                // try the next loopback issuer
            }
        }
        return null;
    }
}
