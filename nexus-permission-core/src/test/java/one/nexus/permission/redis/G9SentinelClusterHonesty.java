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
 * G9 leftover inventory: probe live Redis 3.2 topology and verify an
 * honesty report. Not a Sentinel / Cluster / replica-promotion client.
 * Does not start a second JVM or rewrite PolicySnapshot.
 */
public final class G9SentinelClusterHonesty {
    public static final String HOST = "127.0.0.1";
    public static final int PORT = 6379;

    public record Inventory(
            boolean redisPresent,
            String redisVersion,
            String redisMode,
            String redisRole,
            int connectedSlaves,
            boolean sentinelCommand,
            boolean clusterEnabled,
            String skipReason
    ) {
        public boolean sentinelPresent() {
            return redisPresent && ("sentinel".equalsIgnoreCase(redisMode) || sentinelCommand);
        }

        public boolean replicaPresent() {
            return redisPresent && (connectedSlaves > 0 || "slave".equalsIgnoreCase(redisRole));
        }

        public String sentinelFailover() {
            return "SKIP";
        }

        public String clusterFailover() {
            return "SKIP";
        }

        public String replicaPromotion() {
            return "SKIP";
        }

        public String liveMultiJvm() {
            return "SKIP";
        }
    }

    private G9SentinelClusterHonesty() {}

    public static Inventory probeLive() {
        try {
            Redis32Client client = new Redis32Client(HOST, PORT, Duration.ofMillis(200));
            if (!"PONG".equals(client.ping())) {
                return missing("PING did not return PONG on " + HOST + ":" + PORT);
            }
            String server = client.info("server");
            String replication = client.info("replication");
            String version = Redis32Client.infoField(server, "redis_version");
            String mode = Redis32Client.infoField(server, "redis_mode");
            if (mode.isBlank()) {
                mode = "standalone";
            }
            String role = Redis32Client.infoField(replication, "role");
            int slaves = parseInt(Redis32Client.infoField(replication, "connected_slaves"), 0);
            boolean sentinelCommand = client.tryCommand("SENTINEL", "MASTERS").isPresent();
            boolean clusterEnabled = clusterEnabled(client, mode);
            String reason = skipReason(mode, role, slaves, sentinelCommand, clusterEnabled);
            return new Inventory(true, version, mode, role, slaves, sentinelCommand, clusterEnabled, reason);
        } catch (RuntimeException ex) {
            return missing("no Redis listener on " + HOST + ":" + PORT
                    + "; redis-cli/redis-sentinel not required; Docker forbidden ("
                    + ex.getClass().getSimpleName() + ")");
        }
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
        require(report, "g9_close", "NOT_CLAIMED");
        require(report, "in_process_partition", "PASS");
        refusePassWithout(report, "sentinel_failover", "sentinel_present", "true",
                "sentinel_failover=PASS requires sentinel_present=true");
        refusePassWithout(report, "sentinel_failover", "failover_observed", "true",
                "sentinel_failover=PASS requires failover_observed=true");
        refusePassWithout(report, "cluster_failover", "cluster_enabled", "true",
                "cluster_failover=PASS requires cluster_enabled=true");
        refusePassWithout(report, "cluster_failover", "failover_observed", "true",
                "cluster_failover=PASS requires failover_observed=true");
        refusePassWithout(report, "replica_promotion", "replica_present", "true",
                "replica_promotion=PASS requires replica_present=true");
        refusePassWithout(report, "replica_promotion", "promotion_observed", "true",
                "replica_promotion=PASS requires promotion_observed=true");
        refusePassWithout(report, "live_multi_jvm", "multi_jvm_observed", "true",
                "live_multi_jvm=PASS requires multi_jvm_observed=true");
        requireSkipReason(report, "sentinel_failover", "sentinel_skip_reason");
        requireSkipReason(report, "cluster_failover", "cluster_skip_reason");
        requireSkipReason(report, "replica_promotion", "replica_skip_reason");
        requireSkipReason(report, "live_multi_jvm", "multi_jvm_skip_reason");
        for (String key : report.keySet()) {
            String upper = key.toUpperCase();
            if (upper.contains("PASSWORD") || upper.contains("SECRET") || upper.contains("PASSWD")) {
                throw new IllegalArgumentException("report must not contain password/secret material");
            }
        }
    }

    public static Map<String, String> honestSkipTemplate(Inventory inventory) {
        Map<String, String> report = new LinkedHashMap<>();
        report.put("schema_version", "1");
        report.put("timestamp_utc", "");
        report.put("java_feature", "21");
        report.put("boot_parent_version", "4.1.1");
        report.put("boot_parent_match", "PASS");
        report.put("docker_used", "false");
        report.put("redis_present", Boolean.toString(inventory.redisPresent()));
        report.put("redis_version", nullToEmpty(inventory.redisVersion()));
        report.put("redis_mode", nullToEmpty(inventory.redisMode()));
        report.put("redis_role", nullToEmpty(inventory.redisRole()));
        report.put("connected_slaves", Integer.toString(inventory.connectedSlaves()));
        report.put("sentinel_present", Boolean.toString(inventory.sentinelPresent()));
        report.put("sentinel_command", Boolean.toString(inventory.sentinelCommand()));
        report.put("cluster_enabled", Boolean.toString(inventory.clusterEnabled()));
        report.put("replica_present", Boolean.toString(inventory.replicaPresent()));
        report.put("multi_jvm_observed", "false");
        report.put("failover_observed", "false");
        report.put("promotion_observed", "false");
        report.put("in_process_partition", "PASS");
        report.put("sentinel_failover", "SKIP");
        report.put("cluster_failover", "SKIP");
        report.put("replica_promotion", "SKIP");
        report.put("live_multi_jvm", "SKIP");
        String reason = inventory.skipReason();
        report.put("sentinel_skip_reason", reason);
        report.put("cluster_skip_reason", reason);
        report.put("replica_skip_reason", reason);
        report.put("multi_jvm_skip_reason",
                "in-process dual-reader on one JVM; no second process, no iptables, no Docker");
        report.put("g9_close", "NOT_CLAIMED");
        report.put("g18_status", "closed_unchanged");
        report.put("audit_port_changed", "false");
        report.put("policy_snapshot_redesigned", "false");
        return report;
    }

    private static Inventory missing(String reason) {
        return new Inventory(false, "", "", "", 0, false, false, reason);
    }

    private static boolean clusterEnabled(Redis32Client client, String mode) {
        if ("cluster".equalsIgnoreCase(mode)) {
            return true;
        }
        return client.tryCommand("CLUSTER", "INFO")
                .map(body -> body.contains("cluster_enabled:1") || body.contains("cluster_state:ok"))
                .orElse(false);
    }

    private static String skipReason(
            String mode,
            String role,
            int slaves,
            boolean sentinelCommand,
            boolean clusterEnabled
    ) {
        if ("sentinel".equalsIgnoreCase(mode) || sentinelCommand) {
            return "Redis Sentinel process is present but no failover / replica promotion was observed";
        }
        if (clusterEnabled || "cluster".equalsIgnoreCase(mode)) {
            return "Redis Cluster mode is present but no slot failover was observed; Docker forbidden";
        }
        if (slaves > 0 || "slave".equalsIgnoreCase(role)) {
            return "replica is present but promotion was not observed; Redis32Client is standalone RESP2";
        }
        return "redis_mode=" + mode + " role=" + role
                + " connected_slaves=" + slaves
                + "; standalone RESP2 only; no Sentinel/Cluster/replica promotion";
    }

    private static String[] requiredKeys() {
        return new String[] {
                "schema_version", "timestamp_utc", "java_feature", "boot_parent_version",
                "boot_parent_match", "docker_used", "redis_present", "redis_version",
                "redis_mode", "redis_role", "connected_slaves", "sentinel_present",
                "sentinel_command", "cluster_enabled", "replica_present",
                "multi_jvm_observed", "failover_observed", "promotion_observed",
                "in_process_partition", "sentinel_failover", "cluster_failover",
                "replica_promotion", "live_multi_jvm", "sentinel_skip_reason",
                "cluster_skip_reason", "replica_skip_reason", "multi_jvm_skip_reason",
                "g9_close", "g18_status", "audit_port_changed", "policy_snapshot_redesigned"
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

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
