package one.nexus.bootstrap;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Enables the G4 PKCE live walk only when {@code NEXUS_KEYCLOAK_SMOKE} is set
 * and OIDC discovery answers. Default {@code mvn test} stays green without Keycloak.
 */
public final class G4LiveKeycloakCondition implements ExecutionCondition {
    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        String smoke = System.getenv("NEXUS_KEYCLOAK_SMOKE");
        boolean requested = "1".equals(smoke) || "true".equalsIgnoreCase(smoke);
        if (!requested) {
            return ConditionEvaluationResult.disabled(
                    "live Keycloak PKCE walk is opt-in; set NEXUS_KEYCLOAK_SMOKE=1");
        }
        String issuer = issuer();
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

    static String issuer() {
        String configured = env("NEXUS_KEYCLOAK_ISSUER", "");
        if (!configured.isBlank()) {
            return configured;
        }
        return firstReachable(
                "http://127.0.0.1:8180/realms/nexus",
                "http://127.0.0.1:8080/realms/nexus");
    }

    static String firstReachable(String... issuers) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        for (String candidate : issuers) {
            try {
                HttpResponse<String> response = http.send(
                        HttpRequest.newBuilder(URI.create(candidate + "/.well-known/openid-configuration"))
                                .timeout(Duration.ofSeconds(3)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200 && response.body().contains("\"issuer\"")) {
                    return candidate;
                }
            } catch (Exception ignored) {
                // try the next loopback issuer
            }
        }
        return null;
    }
}
