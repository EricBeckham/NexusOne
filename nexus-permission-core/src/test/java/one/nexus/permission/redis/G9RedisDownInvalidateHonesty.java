package one.nexus.permission.redis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * G9 leftover honesty inventory for <em>live Redis-down</em> and
 * <em>live PolicySnapshot invalidate</em> beyond #121 / #148.
 * Dead client on port {@code 1} (#83 / #121) is <strong>not</strong> a
 * live Redis process-down. Does not redo #100 / #121 / #128 / #148 / #161.
 * Not a product G9 close.
 */
public final class G9RedisDownInvalidateHonesty {
    public static final String HOST = "127.0.0.1";
    public static final int LIVE_PORT = 6379;
    public static final int DEAD_CLIENT_PORT = 1;

    public record RedisInventory(
            boolean redisPresent,
            String redisRole,
            boolean redisDownObserved,
            boolean liveInvalidateObserved,
            String liveRedisDownSkipReason,
            String liveInvalidateSkipReason
    ) {
        public String liveRedisDown() {
            return "SKIP";
        }

        public String liveInvalidate() {
            return "SKIP";
        }

        public boolean deadClientIsNotLiveDown() {
            return true;
        }

        public int simulatedDeadClientPort() {
            return DEAD_CLIENT_PORT;
        }
    }

    private G9RedisDownInvalidateHonesty() {}

    public static RedisInventory probeLive() {
        try {
            Redis32Client client = new Redis32Client(HOST, LIVE_PORT, Duration.ofMillis(200));
            if (!"PONG".equals(client.ping())) {
                return missing("PING did not return PONG on " + HOST + ":" + LIVE_PORT
                        + "; dead client on port " + DEAD_CLIENT_PORT
                        + " is not live Redis-down; live invalidate not observed");
            }
            String replication = client.info("replication");
            return fromLivePing(Redis32Client.infoField(replication, "role"));
        } catch (RuntimeException ex) {
            return missing("no Redis listener on " + HOST + ":" + LIVE_PORT
                    + "; dead client on port " + DEAD_CLIENT_PORT
                    + " is not live Redis-down; live invalidate not observed; Docker forbidden ("
                    + ex.getClass().getSimpleName() + ")");
        }
    }

    public static RedisInventory fromLivePing(String redisRole) {
        String role = redisRole == null ? "" : redisRole.trim();
        boolean downObserved = "true".equalsIgnoreCase(System.getenv("NEXUS_G9_REDIS_DOWN_OBSERVED"));
        boolean invalidateObserved = "true".equalsIgnoreCase(System.getenv("NEXUS_G9_LIVE_INVALIDATE_OBSERVED"));
        String downReason;
        if (!downObserved) {
            downReason = "Redis is listening on " + HOST + ":" + LIVE_PORT
                    + " (role=" + (role.isBlank() ? "unknown" : role)
                    + ") but a live Redis process-down was not observed; dead client on port "
                    + DEAD_CLIENT_PORT + " is not live Redis-down; Docker forbidden";
        } else {
            downReason = "live Redis-down listed but this leftover still does not claim product G9 close";
        }
        String invalidateReason;
        if (!invalidateObserved) {
            invalidateReason = "Redis is listening on " + HOST + ":" + LIVE_PORT
                    + " but a live PUBLISH/SUBSCRIBE invalidate walk was not observed; "
                    + "RedisInvalidationTest skip is not a live invalidate PASS";
        } else {
            invalidateReason = "live invalidate listed but this leftover still does not claim product G9 close";
        }
        return new RedisInventory(true, role, downObserved, invalidateObserved, downReason, invalidateReason);
    }

    public static Map<String, String> parseReport(Path file) {
        try {
            Map<String, String> values = new TreeMap<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                values.put(line.substring(0, eq), line.substring(eq + 1));
            }
            return values;
        } catch (IOException ex) {
            throw new IllegalArgumentException("report not readable: " + file, ex);
        }
    }

    public static void verify(Map<String, String> report) {
        if (report == null || report.isEmpty()) {
            throw new IllegalArgumentException("empty report");
        }
        for (String key : requiredKeys()) {
            if (!report.containsKey(key)) {
                throw new IllegalArgumentException("missing key " + key);
            }
        }
        require(report, "java_feature", "21");
        require(report, "boot_parent_version", "4.1.1");
        require(report, "boot_parent_match", "PASS");
        require(report, "docker_used", "false");
        require(report, "g18_status", "closed_unchanged");
        require(report, "audit_port_changed", "false");
        require(report, "policy_snapshot_redesigned", "false");
        require(report, "p0_m0_redone", "false");
        require(report, "g9_100_121_128_148_161_redone", "false");
        require(report, "g9_close", "NOT_CLAIMED");
        require(report, "dead_client_is_not_live_down", "true");
        require(report, "simulated_dead_client_port", Integer.toString(DEAD_CLIENT_PORT));
        refusePassWithout(report, "live_redis_down", "redis_present", "true",
                "live_redis_down=PASS requires redis_present=true");
        refusePassWithout(report, "live_redis_down", "redis_down_observed", "true",
                "live_redis_down=PASS requires redis_down_observed=true");
        refusePassWithout(report, "live_invalidate", "redis_present", "true",
                "live_invalidate=PASS requires redis_present=true");
        refusePassWithout(report, "live_invalidate", "live_invalidate_observed", "true",
                "live_invalidate=PASS requires live_invalidate_observed=true");
        requireSkipReason(report, "live_redis_down", "live_redis_down_skip_reason");
        requireSkipReason(report, "live_invalidate", "live_invalidate_skip_reason");
        if ("SKIP".equals(report.get("live_redis_down"))
                && "true".equals(report.get("redis_down_observed"))) {
            throw new IllegalArgumentException("SKIP cannot claim a live Redis-down walk");
        }
        if ("SKIP".equals(report.get("live_invalidate"))
                && "true".equals(report.get("live_invalidate_observed"))) {
            throw new IllegalArgumentException("SKIP cannot claim a live invalidate walk");
        }
        for (String key : report.keySet()) {
            String upper = key.toUpperCase();
            if (upper.contains("PASSWORD") || upper.contains("SECRET") || upper.contains("PASSWD")) {
                throw new IllegalArgumentException("report must not contain password/secret material");
            }
        }
    }

    public static Map<String, String> honestSkipTemplate(RedisInventory inventory) {
        Map<String, String> report = new LinkedHashMap<>();
        report.put("schema_version", "1");
        report.put("timestamp_utc", "");
        report.put("java_feature", "21");
        report.put("boot_parent_version", "4.1.1");
        report.put("boot_parent_match", "PASS");
        report.put("docker_used", "false");
        report.put("redis_present", Boolean.toString(inventory.redisPresent()));
        report.put("redis_role", nullToEmpty(inventory.redisRole()));
        report.put("simulated_dead_client_port", Integer.toString(DEAD_CLIENT_PORT));
        report.put("dead_client_is_not_live_down", "true");
        report.put("redis_down_observed", Boolean.toString(inventory.redisDownObserved()));
        report.put("live_redis_down", "SKIP");
        report.put("live_redis_down_skip_reason", inventory.liveRedisDownSkipReason());
        report.put("live_invalidate_observed", Boolean.toString(inventory.liveInvalidateObserved()));
        report.put("live_invalidate", "SKIP");
        report.put("live_invalidate_skip_reason", inventory.liveInvalidateSkipReason());
        report.put("g9_close", "NOT_CLAIMED");
        report.put("g18_status", "closed_unchanged");
        report.put("audit_port_changed", "false");
        report.put("policy_snapshot_redesigned", "false");
        report.put("p0_m0_redone", "false");
        report.put("g9_100_121_128_148_161_redone", "false");
        return report;
    }

    private static RedisInventory missing(String reason) {
        return new RedisInventory(false, "", false, false, reason, reason);
    }

    private static String[] requiredKeys() {
        return new String[] {
                "schema_version", "timestamp_utc", "java_feature", "boot_parent_version",
                "boot_parent_match", "docker_used", "redis_present", "redis_role",
                "simulated_dead_client_port", "dead_client_is_not_live_down",
                "redis_down_observed", "live_redis_down", "live_redis_down_skip_reason",
                "live_invalidate_observed", "live_invalidate", "live_invalidate_skip_reason",
                "g9_close", "g18_status", "audit_port_changed", "policy_snapshot_redesigned",
                "p0_m0_redone", "g9_100_121_128_148_161_redone"
        };
    }

    private static void require(Map<String, String> report, String key, String expected) {
        String actual = report.get(key);
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(key + " must be " + expected + ", got " + actual);
        }
    }

    private static void refusePassWithout(
            Map<String, String> report,
            String verdictKey,
            String requiredKey,
            String requiredValue,
            String message
    ) {
        if ("PASS".equals(report.get(verdictKey)) && !requiredValue.equals(report.get(requiredKey))) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireSkipReason(Map<String, String> report, String verdictKey, String reasonKey) {
        if ("SKIP".equals(report.get(verdictKey))) {
            String reason = report.get(reasonKey);
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException(verdictKey + " SKIP needs " + reasonKey);
            }
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
