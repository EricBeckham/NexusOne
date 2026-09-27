package one.nexus.bootstrap.readiness;

import java.util.List;
import java.util.Map;
import one.nexus.bootstrap.profile.ProductionProfileGuard;
import one.nexus.bootstrap.profile.ProductionProfileGuard.Presence;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.IsolationGuard;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

/**
 * DL-L3: read-only demo readiness for the platform console. Every field is a presence boolean, a fixed enum word
 * or an environment variable NAME; no configuration value (issuer URL, client id, secret, token) is ever returned.
 * Readiness ≠ acceptance: M7 §1 real-host checks are still done by the environment owner.
 */
@Service
public class DemoReadinessService {
    static final String NOTE = "只报是否已配置（布尔 / 变量名），不回任何配置值；预备 ≠ 验收，真机勾选见 M7 §1。";
    private static final List<String> HEALTH_WORDS = List.of("UP", "DOWN", "OUT_OF_SERVICE", "UNKNOWN");

    private final Environment env;
    private final IsolationGuard isolation;
    private final ObjectProvider<HealthEndpoint> health;

    public DemoReadinessService(Environment env, IsolationGuard isolation, ObjectProvider<HealthEndpoint> health) {
        this.env = env;
        this.isolation = isolation;
        this.health = health;
    }

    /**
     * @param missingKeycloakVariables env variable names that are missing, placeholders or local defaults
     */
    public record DemoReadiness(boolean prodProfileActive, boolean demoProfileActive, String idpMode,
            boolean keycloakIssuerConfigured, boolean keycloakIssuerHttps, boolean keycloakClientConfigured,
            List<String> missingKeycloakVariables, boolean demoFlagsOff, boolean cryptoKeyFromEnv,
            boolean durablePersistence, int prodGuardViolationCount, String health, boolean contentBootstrapOpen,
            String note) {
        public DemoReadiness {
            missingKeycloakVariables = List.copyOf(missingKeycloakVariables);
        }
    }

    public DemoReadiness read() {
        isolation.requirePlatformContext("demo-readiness.read", PlatformDuty.SYSTEM_ADMIN);
        return snapshot();
    }

    DemoReadiness snapshot() {
        Map<String, Presence> keycloak = ProductionProfileGuard.keycloakPresence(env);
        List<String> missing = keycloak.entrySet().stream()
                .filter(entry -> entry.getValue() != Presence.CONFIGURED).map(Map.Entry::getKey).toList();
        boolean issuerConfigured = keycloak.get("NEXUS_IAM_KEYCLOAK_ISSUER") == Presence.CONFIGURED;
        String issuer = env.getProperty("nexus.iam.keycloak.issuer", "").trim();
        boolean issuerHttps = issuerConfigured && issuer.startsWith("https://");
        return new DemoReadiness(
                ProductionProfileGuard.productionActive(env),
                env.acceptsProfiles(Profiles.of("demo")),
                ProductionProfileGuard.idpMode(env),
                issuerConfigured,
                issuerHttps,
                missing.isEmpty(),
                missing,
                ProductionProfileGuard.demoFlagsOff(env),
                ProductionProfileGuard.cryptoKeyFromEnv(env),
                ProductionProfileGuard.durablePersistence(env),
                ProductionProfileGuard.violations(env).size(),
                healthWord(),
                "true".equalsIgnoreCase(env.getProperty("nexus.content-bootstrap.enabled", "false").trim()),
                NOTE);
    }

    private String healthWord() {
        HealthEndpoint endpoint = health.getIfAvailable();
        if (endpoint == null) return "UNKNOWN";
        try {
            var descriptor = endpoint.health();
            String code = descriptor == null || descriptor.getStatus() == null ? "UNKNOWN" : descriptor.getStatus().getCode();
            return HEALTH_WORDS.contains(code) ? code : "UNKNOWN";
        } catch (RuntimeException unavailable) {
            return "UNKNOWN";
        }
    }
}
