package one.nexus.bootstrap.demo;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Visual-demo switch. Default off. This class does not insert business rows.
 * Later V-SEED-P* cards must call {@link #enabled(String)} and no-op when it is false.
 */
public final class DemoProfileGate {
    public static final String ENABLED = "nexus.demo.enabled";
    public static final String ENVIRONMENT = "nexus.demo.environment";

    private DemoProfileGate() {}

    public static boolean enabled(String raw) {
        return raw != null && "true".equalsIgnoreCase(raw.trim());
    }

    public static void assertAllowed(boolean enabled, String environment, String datasourceUrl) {
        if (!enabled) {
            return;
        }
        if (isProduction(environment) || looksRemote(datasourceUrl)) {
            throw new IllegalStateException(
                    "禁止连生产：演示开关默认关闭，不能对生产环境或非本机数据库启用");
        }
    }

    public static boolean isProduction(String environment) {
        if (environment == null) {
            return false;
        }
        String value = environment.trim().toLowerCase();
        return value.equals("prod") || value.equals("production");
    }

    /** Demo may follow a loopback database only. A remote URL is treated as production-shaped. */
    public static boolean looksRemote(String datasourceUrl) {
        if (datasourceUrl == null || datasourceUrl.isBlank()) {
            return true;
        }
        try {
            URI uri = URI.create(datasourceUrl.trim().substring("jdbc:".length()));
            String host = uri.getHost();
            if (host == null) {
                return true;
            }
            String normalized = host.replace("[", "").replace("]", "").toLowerCase(Locale.ROOT);
            return !Set.of("127.0.0.1", "localhost", "::1").contains(normalized);
        } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
            return true;
        }
    }
}
