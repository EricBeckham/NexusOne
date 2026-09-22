package one.nexus.crypto;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Logical secret names used by business call sites. Values live in the
 * in-process overlay or in environment variables — never OpenBao / KMS.
 */
public final class SecretNames {
    public static final String DATASOURCE_PASSWORD = "datasource.password";
    public static final String S3_ACCESS_KEY = "s3.access-key";
    public static final String S3_SECRET_KEY = "s3.secret-key";
    public static final String KEYCLOAK_CLIENT_SECRET = "iam.keycloak.client-secret";
    public static final String CRYPTO_MASTER_KEY = "crypto.master-key";
    public static final String PLATFORM_BOOTSTRAP_TOKEN = "iam.platform-bootstrap-token";

    private SecretNames() {}

    /**
     * Logical name → process environment variable. The env adapter also accepts
     * the raw variable name as an alias.
     */
    public static Map<String, String> envBindings() {
        Map<String, String> bindings = new LinkedHashMap<>();
        bindings.put(DATASOURCE_PASSWORD, "NEXUS_DB_PASSWORD");
        bindings.put(S3_ACCESS_KEY, "S3_ACCESS_KEY");
        bindings.put(S3_SECRET_KEY, "S3_SECRET_KEY");
        bindings.put(KEYCLOAK_CLIENT_SECRET, "NEXUS_KEYCLOAK_CLIENT_SECRET");
        bindings.put(CRYPTO_MASTER_KEY, "NEXUS_CRYPTO_MASTER_KEY");
        bindings.put(PLATFORM_BOOTSTRAP_TOKEN, "NEXUS_PLATFORM_BOOTSTRAP_TOKEN");
        return Map.copyOf(bindings);
    }

    public static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        return name;
    }

    public static String envVar(String logicalName) {
        return Objects.requireNonNull(envBindings().get(logicalName), "unmapped secret name: " + logicalName);
    }
}
