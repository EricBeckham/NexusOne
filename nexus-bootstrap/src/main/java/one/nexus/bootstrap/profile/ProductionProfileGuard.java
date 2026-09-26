package one.nexus.bootstrap.profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Startup checks for the {@code prod} Spring profile. A violation refuses the
 * start; a warning is logged. Outside {@code prod} only the ephemeral master
 * key is reported, because durable secrets written with it are unreadable
 * after a restart.
 */
public final class ProductionProfileGuard {
    public static final String PROFILE = "prod";

    private static final List<String> DEMO_CONTEXT_SWITCHES = List.of(
            "nexus.p1.demo-context.enabled",
            "nexus.p3.demo-context",
            "nexus.studio.demo-context",
            "nexus.files.demo-context",
            "nexus.notify.demo-context");

    private ProductionProfileGuard() {}

    public static boolean productionActive(Environment env) {
        return env.acceptsProfiles(Profiles.of(PROFILE));
    }

    public static List<String> violations(Environment env) {
        List<String> violations = new ArrayList<>();
        if (!productionActive(env)) return violations;
        if (env.acceptsProfiles(Profiles.of("demo"))) {
            violations.add("spring profile demo must not be active together with prod");
        }
        if (flag(env, "nexus.demo.enabled")) {
            violations.add("nexus.demo.enabled must be false");
        }
        for (String name : DEMO_CONTEXT_SWITCHES) {
            if (flag(env, name)) violations.add(name + " must be false (it trusts demo identity headers)");
        }
        if (!"env".equals(value(env, "nexus.crypto.key-source", "ephemeral"))) {
            violations.add("nexus.crypto.key-source must be env (set NEXUS_CRYPTO_MASTER_KEY); "
                    + "an ephemeral key makes stored secrets unreadable after a restart");
        }
        if ("stub".equals(value(env, "nexus.iam.idp", "stub"))) {
            violations.add("nexus.iam.idp must not be stub");
        }
        for (String name : List.of("nexus.iam.persistence", "nexus.permission.persistence")) {
            if (!"jdbc".equals(value(env, name, "memory"))) {
                violations.add(name + " must be jdbc; memory loses identities and grants on restart");
            }
        }
        if ("keycloak".equals(value(env, "nexus.iam.idp", "stub"))) {
            violations.addAll(keycloakViolations(env));
        }
        if ("always".equals(value(env, "management.endpoint.health.show-details", "never"))) {
            violations.add("management.endpoint.health.show-details must not be always");
        }
        for (String endpoint : value(env, "management.endpoints.web.exposure.include", "health").split(",")) {
            String name = endpoint.trim();
            if (!name.isEmpty() && !"health".equals(name)) {
                violations.add("management.endpoints.web.exposure.include must be health only; actuator has no "
                        + "authentication, so '" + name + "' would be anonymous");
            }
        }
        return violations;
    }

    public static List<String> warnings(Environment env) {
        List<String> warnings = new ArrayList<>();
        if (!productionActive(env)) {
            if ("ephemeral".equals(value(env, "nexus.crypto.key-source", "ephemeral"))) {
                warnings.add("nexus.crypto.key-source=ephemeral: secrets stored in platform.secret_ciphertext "
                        + "cannot be decrypted after this process restarts");
            }
            return warnings;
        }
        if (!"s3".equals(value(env, "nexus.files.storage", "memory"))) {
            warnings.add("nexus.files.storage is not s3; uploaded file bytes are lost on restart");
        }
        for (String name : List.of("nexus.iam.platform-bootstrap-token", "nexus.files.platform-bootstrap-token")) {
            if (!value(env, name, "").isEmpty()) {
                warnings.add(name + " is set; clear it once platform bootstrap is done");
            }
        }
        String issuer = value(env, "nexus.iam.keycloak.issuer", "");
        if (!issuer.isEmpty() && !issuer.startsWith("https://")) {
            warnings.add("nexus.iam.keycloak.issuer is not https");
        }
        return warnings;
    }

    /**
     * DPO-L1: one Keycloak client setting that prod needs. {@code devDefault} is the local value
     * application.yml falls back to, which must never reach prod; null when any non-blank value is acceptable.
     */
    record KeycloakSetting(String env, String property, String devDefault) {}

    static final List<KeycloakSetting> KEYCLOAK_SETTINGS = List.of(
            new KeycloakSetting("NEXUS_IAM_KEYCLOAK_ISSUER", "nexus.iam.keycloak.issuer",
                    "http://127.0.0.1:8080/realms/nexus"),
            new KeycloakSetting("NEXUS_IAM_KEYCLOAK_CLIENT_ID", "nexus.iam.keycloak.client-id", null),
            new KeycloakSetting("NEXUS_IAM_KEYCLOAK_CLIENT_SECRET", "nexus.iam.keycloak.client-secret", null),
            new KeycloakSetting("NEXUS_IAM_KEYCLOAK_REDIRECT_URI", "nexus.iam.keycloak.redirect-uri",
                    "http://127.0.0.1:8080/api/v1/auth/callback"));

    /**
     * DPO-L1: prod with {@code nexus.iam.idp=keycloak} must have every client setting. Messages name the
     * environment variable and the property only — never the value, which may be a secret.
     */
    static List<String> keycloakViolations(Environment env) {
        List<String> violations = new ArrayList<>();
        for (KeycloakSetting setting : KEYCLOAK_SETTINGS) {
            String raw = env.getProperty(setting.property());
            String resolved = raw == null ? "" : raw.trim();
            if (resolved.isEmpty()) {
                violations.add(setting.env() + " is not set (" + setting.property()
                        + "); required when prod runs with nexus.iam.idp=keycloak");
            } else if (resolved.contains("REPLACE_")) {
                violations.add(setting.env() + " still holds a template placeholder (" + setting.property()
                        + "); fill it from the secret store");
            } else if (setting.devDefault() != null && setting.devDefault().equals(resolved)) {
                violations.add(setting.env() + " is not set (" + setting.property()
                        + " is still the local development default); required when prod runs with nexus.iam.idp=keycloak");
            }
        }
        return violations;
    }

    private static boolean flag(Environment env, String name) {
        return "true".equals(value(env, name, "false"));
    }

    private static String value(Environment env, String name, String fallback) {
        String raw = env.getProperty(name);
        return raw == null ? fallback : raw.trim().toLowerCase(Locale.ROOT);
    }
}
