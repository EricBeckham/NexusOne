package one.nexus.permission.redis;

/**
 * P0-05 key contract: {@code nexus:v1:{tenant-id}:cache:&lt;domain&gt;:&lt;resource&gt;}.
 * Braces are hash tags for future Cluster; Redis 3.2 single-node treats them as literals.
 */
public final class PolicyCacheKeys {
    public static final String INVALIDATE_CHANNEL = "nexus:v1:permission:invalidate";

    private PolicyCacheKeys() {}

    public static String snapshot(String tenantId) {
        return "nexus:v1:{" + tenantId + "}:cache:permission:snapshot";
    }

    public static String version(String tenantId) {
        return "nexus:v1:{" + tenantId + "}:cache:permission:version";
    }
}
