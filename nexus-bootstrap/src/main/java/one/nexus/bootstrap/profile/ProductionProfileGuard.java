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
        if ("always".equals(value(env, "management.endpoint.health.show-details", "never"))) {
            violations.add("management.endpoint.health.show-details must not be always");
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

    private static boolean flag(Environment env, String name) {
        return "true".equals(value(env, name, "false"));
    }

    private static String value(Environment env, String name, String fallback) {
        String raw = env.getProperty(name);
        return raw == null ? fallback : raw.trim().toLowerCase(Locale.ROOT);
    }
}
