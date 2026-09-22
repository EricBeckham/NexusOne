package one.nexus.audit;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Strips secrets before they enter audit records, hash-chain payloads, or logs.
 */
public final class SensitiveRedactor {
    public static final String REDACTED = "[REDACTED]";
    private static final Set<String> EXACT = Set.of(
            "password",
            "passwd",
            "pwd",
            "secret",
            "token",
            "authorization",
            "cookie",
            "credential",
            "master_key",
            "masterkey",
            "client_secret",
            "access_token",
            "refresh_token",
            "id_token",
            "private_key",
            "api_key",
            "apikey");

    private SensitiveRedactor() {}

    public static Map<String, String> redact(Map<String, ?> details) {
        if (details == null || details.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        details.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String key = entry.getKey();
                    String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
                    out.put(key, isSensitive(key) ? REDACTED : value);
                });
        return Map.copyOf(out);
    }

    public static boolean isSensitive(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replace('-', '_');
        if (EXACT.contains(normalized)) {
            return true;
        }
        return normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("token")
                || normalized.contains("credential")
                || normalized.endsWith("_key");
    }
}
