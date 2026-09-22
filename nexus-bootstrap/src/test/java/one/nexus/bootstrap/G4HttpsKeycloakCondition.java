package one.nexus.bootstrap;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import one.nexus.p2.idp.LoopbackHttps;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Enables the G4 HTTPS PKCE walk only when explicitly requested and HTTPS OIDC
 * discovery answers. Default {@code mvn test} stays green without Keycloak.
 */
public final class G4HttpsKeycloakCondition implements ExecutionCondition {
    private static volatile SSLContext installed;

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (!requested()) {
            return ConditionEvaluationResult.disabled(
                    "HTTPS Keycloak PKCE walk is opt-in; set NEXUS_KEYCLOAK_HTTPS=1");
        }
        try {
            installTrust();
        } catch (Exception ex) {
            return ConditionEvaluationResult.disabled("loopback HTTPS truststore missing: " + ex.getMessage());
        }
        String issuer = issuer();
        if (issuer == null || !issuer.startsWith("https://")) {
            return ConditionEvaluationResult.disabled(
                    "NEXUS_KEYCLOAK_ISSUER must be https://127.0.0.1:<port>/realms/nexus");
        }
        String url = issuer + "/.well-known/openid-configuration";
        try {
            HttpClient http = HttpClient.newBuilder()
                    .sslContext(clientContext())
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(4)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body().contains("\"issuer\"")) {
                return ConditionEvaluationResult.enabled("HTTPS Keycloak OIDC discovery at " + url);
            }
            return ConditionEvaluationResult.disabled(
                    "HTTPS Keycloak discovery HTTP " + response.statusCode() + " at " + url);
        } catch (Exception ex) {
            return ConditionEvaluationResult.disabled("HTTPS Keycloak not reachable at " + url + ": " + ex.getMessage());
        }
    }

    static boolean requested() {
        String flag = env("NEXUS_KEYCLOAK_HTTPS", "");
        return "1".equals(flag) || "true".equalsIgnoreCase(flag);
    }

    static String env(String name, String fallback) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? fallback : value;
    }

    static String issuer() {
        return env("NEXUS_KEYCLOAK_ISSUER", "https://127.0.0.1:8543/realms/nexus");
    }

    static Path keyStore() {
        return Path.of(env("NEXUS_G4_HTTPS_KEYSTORE", defaultDir() + "/server.p12"));
    }

    static Path trustStore() {
        return Path.of(env("NEXUS_G4_HTTPS_TRUSTSTORE", defaultDir() + "/truststore.p12"));
    }

    static Path certificatePem() {
        return Path.of(env("NEXUS_G4_HTTPS_CERT", defaultDir() + "/server.crt"));
    }

    static String storePassword() {
        return env("NEXUS_G4_HTTPS_STORE_PASSWORD", "");
    }

    static int productPort() {
        return Integer.parseInt(env("NEXUS_HTTPS_PORT", "18443"));
    }

    static String redirectUri() {
        return "https://127.0.0.1:" + productPort() + "/api/v1/auth/callback";
    }

    static synchronized void installTrust() throws Exception {
        if (installed != null) {
            return;
        }
        SSLContext client = clientContext();
        LoopbackHttps.installClientTrust(client);
        installed = client;
    }

    static SSLContext clientContext() throws Exception {
        if (installed != null) {
            return installed;
        }
        Path trust = trustStore();
        String password = storePassword();
        if (Files.isRegularFile(trust) && password != null && !password.isBlank()) {
            return LoopbackHttps.clientContext(trust, password.toCharArray());
        }
        Path pem = certificatePem();
        if (Files.isRegularFile(pem)) {
            return LoopbackHttps.clientContextFromPem(pem);
        }
        throw new IllegalStateException(
                "set NEXUS_G4_HTTPS_TRUSTSTORE + NEXUS_G4_HTTPS_STORE_PASSWORD or NEXUS_G4_HTTPS_CERT");
    }

    private static String defaultDir() {
        String configured = env("NEXUS_G4_HTTPS_DIR", "");
        if (!configured.isBlank()) {
            return configured;
        }
        Path local = Path.of(".local/g4-https");
        if (Files.isDirectory(local)) {
            return local.toAbsolutePath().toString();
        }
        return "/tmp/g4-https";
    }
}
