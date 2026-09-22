package one.nexus.permission.redis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * G9 leftover honesty inventory for <em>live multi-JVM</em> and
 * <em>replica promotion</em> only. Does not redo Sentinel / Cluster
 * inventory from #148. Does not start a second JVM, a replica, or Docker.
 * Not a product G9 / Sentinel / Cluster close.
 */
public final class G9LiveMultiJvmReplicaHonesty {
    public static final String HOST = "127.0.0.1";
    public static final int PORT = 6379;

    public record ReplicaInventory(
            boolean redisPresent,
            String redisRole,
            int connectedSlaves,
            boolean replicaPresent,
            boolean promotionObserved,
            String skipReason
    ) {
        public String replicaPromotion() {
            return "SKIP";
        }
    }

    public record JvmInventory(
            boolean secondJvmPresent,
            String secondJvmPid,
            boolean multiJvmObserved,
            String skipReason
    ) {
        public String liveMultiJvm() {
            return "SKIP";
        }
    }

    private G9LiveMultiJvmReplicaHonesty() {}

    public static ReplicaInventory probeReplica() {
        try {
            Redis32Client client = new Redis32Client(HOST, PORT, Duration.ofMillis(200));
            if (!"PONG".equals(client.ping())) {
                return missingReplica("PING did not return PONG on " + HOST + ":" + PORT);
            }
            String replication = client.info("replication");
            return fromReplicationInfo(replication);
        } catch (RuntimeException ex) {
            return missingReplica("no Redis listener on " + HOST + ":" + PORT
                    + "; replica promotion not observed; Docker forbidden ("
                    + ex.getClass().getSimpleName() + ")");
        }
    }

    public static ReplicaInventory fromReplicationInfo(String replication) {
        String role = Redis32Client.infoField(replication, "role");
        int slaves = parseInt(Redis32Client.infoField(replication, "connected_slaves"), 0);
        boolean replicaPresent = slaves > 0 || "slave".equalsIgnoreCase(role);
        boolean promotionObserved = replicaPresent
                && "true".equalsIgnoreCase(System.getenv("NEXUS_G9_PROMOTION_OBSERVED"));
        String reason;
        if (!replicaPresent) {
            reason = "redis_role=" + (role.isBlank() ? "unknown" : role)
                    + " connected_slaves=" + slaves
                    + "; Redis32Client is standalone RESP2; replica promotion not observed";
        } else if (!promotionObserved) {
            reason = "replica is present (role=" + role + ", connected_slaves=" + slaves
                    + ") but promotion was not observed; Redis32Client is not a failover client";
        } else {
            reason = "replica present but this leftover still does not claim product G9 / Sentinel / Cluster close";
        }
        return new ReplicaInventory(true, role, slaves, replicaPresent, promotionObserved, reason);
    }

    public static JvmInventory probeJvms() {
        String envPid = Optional.ofNullable(System.getenv("NEXUS_G9_SECOND_JVM_PID")).orElse("");
        boolean envObserved = "true".equalsIgnoreCase(System.getenv("NEXUS_G9_MULTI_JVM_OBSERVED"));
        long self = ProcessHandle.current().pid();
        boolean secondPresent = false;
        String secondPid = "";
        if (!envPid.isBlank()) {
            try {
                long pid = Long.parseLong(envPid.trim());
                if (pid != self && ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
                    secondPresent = true;
                    secondPid = Long.toString(pid);
                }
            } catch (NumberFormatException ignored) {
                secondPresent = false;
            }
        }
        boolean observed = secondPresent && envObserved;
        String reason;
        if (!secondPresent) {
            reason = "one application JVM (pid=" + self
                    + "); surefire parent/fork is not a live multi-JVM partition; no second JAR, no iptables/netem, Docker forbidden";
        } else if (!observed) {
            reason = "second JVM pid=" + secondPid
                    + " is listed but a live partition / netem / two-JAR walk was not observed";
        } else {
            reason = "second JVM listed but this leftover still does not claim product G9 / Sentinel / Cluster close";
        }
        return new JvmInventory(secondPresent, secondPid, observed, reason);
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
        require(report, "g9_89_83_100_121_128_148_redone", "false");
        require(report, "g9_close", "NOT_CLAIMED");
        require(report, "sentinel_cluster_close", "NOT_CLAIMED");
        refusePassWithout(report, "replica_promotion", "replica_present", "true",
                "replica_promotion=PASS requires replica_present=true");
        refusePassWithout(report, "replica_promotion", "promotion_observed", "true",
                "replica_promotion=PASS requires promotion_observed=true");
        refusePassWithout(report, "live_multi_jvm", "second_jvm_present", "true",
                "live_multi_jvm=PASS requires second_jvm_present=true");
        refusePassWithout(report, "live_multi_jvm", "multi_jvm_observed", "true",
                "live_multi_jvm=PASS requires multi_jvm_observed=true");
        requireSkipReason(report, "replica_promotion", "replica_skip_reason");
        requireSkipReason(report, "live_multi_jvm", "multi_jvm_skip_reason");
        for (String key : report.keySet()) {
            String upper = key.toUpperCase();
            if (upper.contains("PASSWORD") || upper.contains("SECRET") || upper.contains("PASSWD")) {
                throw new IllegalArgumentException("report must not contain password/secret material");
            }
        }
    }

    public static Map<String, String> honestSkipTemplate(ReplicaInventory replica, JvmInventory jvms) {
        Map<String, String> report = new LinkedHashMap<>();
        report.put("schema_version", "1");
        report.put("timestamp_utc", "");
        report.put("java_feature", "21");
        report.put("boot_parent_version", "4.1.1");
        report.put("boot_parent_match", "PASS");
        report.put("docker_used", "false");
        report.put("redis_present", Boolean.toString(replica.redisPresent()));
        report.put("redis_role", nullToEmpty(replica.redisRole()));
        report.put("connected_slaves", Integer.toString(replica.connectedSlaves()));
        report.put("replica_present", Boolean.toString(replica.replicaPresent()));
        report.put("promotion_observed", Boolean.toString(replica.promotionObserved()));
        report.put("replica_promotion", "SKIP");
        report.put("replica_skip_reason", replica.skipReason());
        report.put("second_jvm_present", Boolean.toString(jvms.secondJvmPresent()));
        report.put("second_jvm_pid", nullToEmpty(jvms.secondJvmPid()));
        report.put("multi_jvm_observed", Boolean.toString(jvms.multiJvmObserved()));
        report.put("live_multi_jvm", "SKIP");
        report.put("multi_jvm_skip_reason", jvms.skipReason());
        report.put("sentinel_cluster_close", "NOT_CLAIMED");
        report.put("g9_close", "NOT_CLAIMED");
        report.put("g18_status", "closed_unchanged");
        report.put("audit_port_changed", "false");
        report.put("policy_snapshot_redesigned", "false");
        report.put("p0_m0_redone", "false");
        report.put("g9_89_83_100_121_128_148_redone", "false");
        return report;
    }

    private static ReplicaInventory missingReplica(String reason) {
        return new ReplicaInventory(false, "", 0, false, false, reason);
    }

    private static String[] requiredKeys() {
        return new String[] {
                "schema_version", "timestamp_utc", "java_feature", "boot_parent_version",
                "boot_parent_match", "docker_used", "redis_present", "redis_role",
                "connected_slaves", "replica_present", "promotion_observed",
                "replica_promotion", "replica_skip_reason", "second_jvm_present",
                "second_jvm_pid", "multi_jvm_observed", "live_multi_jvm",
                "multi_jvm_skip_reason", "sentinel_cluster_close", "g9_close",
                "g18_status", "audit_port_changed", "policy_snapshot_redesigned",
                "p0_m0_redone", "g9_89_83_100_121_128_148_redone"
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
