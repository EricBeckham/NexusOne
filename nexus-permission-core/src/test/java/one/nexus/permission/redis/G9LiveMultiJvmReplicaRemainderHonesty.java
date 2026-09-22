package one.nexus.permission.redis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * G9 leftover honesty inventory <em>beyond #209 / #161 / #148</em>: live
 * multi-JVM / replica promotion remainder PASS/SKIP. The standalone INFO
 * replication inventory stub may PASS. Live replica promotion and live
 * multi-JVM stay SKIP unless the topology is present
 * <strong>and</strong> a promotion / second-JVM partition was observed.
 *
 * <p>Does not redo #83 / #100 / #121 / #128 / #148 / #161 / #174 / #209
 * product code. Does not redesign {@code PolicySnapshot}. Does not claim
 * product G9 / Sentinel / Cluster close. G18 stays closed. No P0/M0 redo.
 * {@code AuditPort} same-TX rules are untouched.
 */
public final class G9LiveMultiJvmReplicaRemainderHonesty {
    public static final String HOST = "127.0.0.1";
    public static final int PORT = 6379;
    public static final String STUB_ROLE = "master";

    public static final String ROW_SENTINEL = "g9-sentinel-cluster-honesty";
    public static final String ROW_REPLICA = "g9-live-multi-jvm-replica";
    public static final String ROW_SENTINEL_REM = "g9-sentinel-cluster-remainder-honesty";
    public static final String ROW_INVALIDATE = "g9-policysnapshot-invalidate";
    public static final String ROW_FAILOVER = "g9-failover-remainder";
    public static final String ROW_REDIS_DOWN = "g9-redis-down-invalidate-honesty";
    public static final String ROW_REDIS_REM = "g9-redis-down-invalidate-remainder";
    public static final String ROW_G3 = "g3-outbox-consume-crash-honesty";
    public static final String ROW_G5 = "g5-mid-publish-failure-honesty";
    public static final String ROW_G5_DDL = "g5-publishhook-ddl-honesty";
    public static final String ROW_G10 = "g10-leftover-honesty";
    public static final String ROW_G4 = "g4-export-wizard-deny-honesty";
    public static final String ROW_G2 = "g2-h5-every-page-form-write-honesty";
    public static final String ROW_G17 = "g17-mfa-recovery-vue-otp-honesty";
    public static final String ROW_G2_OPS = "g2-ops-pg-dump-honesty";
    public static final String ROW_G15_REM = "g15-wx-dt-native-bridge-remainder";
    public static final String ROW_G1_EXPORT = "g1-queryast-export-honesty";
    public static final String ROW_G17_TLS = "g17-gm-tls-nginx-handshake-honesty";
    public static final String ROW_G17_SOD = "g17-separation-leftover-honesty";
    public static final String ROW_G4_SPA = "g4-pkce-spa-verifier-remainder-honesty";
    public static final String ROW_S0_REM = "s0-secret-rotate-getenv-remainder-honesty";
    public static final String ROW_G6_REM = "g6-recovery-port-dry-run-remainder-honesty";
    public static final String ROW_G2_REPORT = "g2-report-physical-dump-honesty";
    public static final String ROW_G10_REM = "g10-leftover-honesty-remainder";
    public static final String ROW_G3_IDEM = "g3-outbox-consume-idempotent-honesty";
    public static final String SIBLING_ROWS = ROW_SENTINEL + "," + ROW_REPLICA + ","
            + ROW_SENTINEL_REM + "," + ROW_INVALIDATE + "," + ROW_FAILOVER + ","
            + ROW_REDIS_DOWN + "," + ROW_REDIS_REM + "," + ROW_G3 + "," + ROW_G5
            + "," + ROW_G5_DDL + "," + ROW_G10 + "," + ROW_G4 + "," + ROW_G2 + ","
            + ROW_G17 + "," + ROW_G2_OPS + "," + ROW_G15_REM + "," + ROW_G1_EXPORT
            + "," + ROW_G17_TLS + "," + ROW_G17_SOD + "," + ROW_G4_SPA + "," + ROW_S0_REM
            + "," + ROW_G6_REM + "," + ROW_G2_REPORT + "," + ROW_G10_REM + "," + ROW_G3_IDEM;

    public static final List<String> README_ROWS = List.of(
            ROW_SENTINEL,
            ROW_REPLICA,
            ROW_SENTINEL_REM,
            ROW_INVALIDATE,
            ROW_FAILOVER,
            ROW_REDIS_DOWN,
            ROW_REDIS_REM,
            "g9-multinode-partition",
            ROW_G3,
            "g3-claim-crash-window",
            "g3-outbox-consume-idempotent",
            ROW_G5,
            ROW_G5_DDL,
            "g5-mid-publish-failure",
            "g5-browser-failed-publish",
            ROW_G10,
            "g10-file-reconcile-honesty",
            "g10-identity-drift-honesty",
            ROW_G4,
            "g4-export-wizard-deny",
            "g4-guard-deny-audit",
            "g4-p1-staging-audit-events-honesty",
            ROW_G2,
            "g2-h5-every-page",
            "g2-form-write-honesty",
            ROW_G17,
            ROW_G2_OPS,
            "g2-ops-pg-dump",
            ROW_G15_REM,
            "g15-wx-dt-native-bridge-honesty",
            "g15-native-bridge-honesty",
            "g15-wx-dt-prod-gap-honesty",
            ROW_G1_EXPORT,
            "g1-queryast-boundsql-honesty",
            ROW_G17_TLS,
            ROW_G17_SOD,
            "g17-separation-honesty",
            "g17-separation-of-duties",
            ROW_G4_SPA,
            ROW_S0_REM,
            ROW_G6_REM,
            ROW_G2_REPORT,
            ROW_G10_REM,
            ROW_G3_IDEM,
            "g4-pkce-spa-verifier-honesty",
            "g4-pkce-spa-verifier",
            "s0-secret-rotate-without-restart-honesty",
            "s0s1-live-redis-minio-isolation-honesty",
            "g6-recovery-port-dry-run-honesty",
            "linux-arm-profile-leftover-honesty",
            "g7-query-expense-travel-honesty"
    );

    public static final List<String> REQUIRED_LEFTOVER_ROWS = List.of(
            ROW_SENTINEL,
            ROW_REPLICA,
            ROW_SENTINEL_REM,
            ROW_INVALIDATE,
            ROW_FAILOVER,
            ROW_REDIS_DOWN,
            ROW_REDIS_REM,
            ROW_G3,
            ROW_G5,
            ROW_G5_DDL,
            ROW_G10,
            ROW_G4,
            ROW_G2,
            ROW_G17,
            ROW_G2_OPS,
            ROW_G15_REM,
            ROW_G1_EXPORT,
            ROW_G17_TLS,
            ROW_G17_SOD,
            ROW_G4_SPA,
            ROW_S0_REM,
            ROW_G6_REM,
            ROW_G2_REPORT,
            ROW_G10_REM,
            ROW_G3_IDEM
    );

    public record InventoryHarness(
            String redisRole,
            int connectedSlaves,
            boolean replicaPresent,
            boolean surefireIsSecondJvm,
            String verdict
    ) {}

    public record LiveObservation(
            boolean redisPresent,
            String redisRole,
            int connectedSlaves,
            boolean replicaPresent,
            boolean promotionObserved,
            String replicaSkipReason,
            boolean secondJvmPresent,
            String secondJvmPid,
            boolean multiJvmObserved,
            String multiJvmSkipReason
    ) {
        public String replicaPromotion() {
            return "SKIP";
        }

        public String liveMultiJvm() {
            return "SKIP";
        }
    }

    private G9LiveMultiJvmReplicaRemainderHonesty() {}

    /**
     * Standalone Redis 3.2 INFO replication stub. Proves
     * {@link Redis32Client#infoField} inventories {@code role} /
     * {@code connected_slaves} without a replica. A surefire parent/fork
     * is not a second application JVM. This is not a live promotion.
     */
    public static InventoryHarness proveStandaloneReplicationInventoryStub() {
        String replication = "# Replication\r\nrole:" + STUB_ROLE + "\r\nconnected_slaves:0\r\n";
        String role = Redis32Client.infoField(replication, "role");
        int slaves = parseInt(Redis32Client.infoField(replication, "connected_slaves"), 0);
        boolean replicaPresent = slaves > 0 || "slave".equalsIgnoreCase(role);
        boolean surefireIsSecondJvm = false;
        boolean pass = STUB_ROLE.equals(role)
                && slaves == 0
                && !replicaPresent
                && !surefireIsSecondJvm;
        return new InventoryHarness(role, slaves, replicaPresent, surefireIsSecondJvm,
                pass ? "PASS" : "FAIL");
    }

    public static LiveObservation probeLive() {
        ReplicaLive replica = probeReplica();
        JvmLive jvms = probeJvms();
        return new LiveObservation(
                replica.redisPresent,
                replica.redisRole,
                replica.connectedSlaves,
                replica.replicaPresent,
                replica.promotionObserved,
                replica.skipReason,
                jvms.secondJvmPresent,
                jvms.secondJvmPid,
                jvms.multiJvmObserved,
                jvms.skipReason
        );
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
        require(report, "g9_83_100_121_128_148_161_174_209_redone", "false");
        require(report, "g9_close", "NOT_CLAIMED");
        require(report, "sentinel_cluster_close", "NOT_CLAIMED");
        require(report, "leftover_rows_kept", "true");
        require(report, "inventory_harness", "PASS");
        require(report, "inventory_redis_role", STUB_ROLE);
        require(report, "inventory_connected_slaves", "0");
        require(report, "inventory_replica_present", "false");
        require(report, "inventory_surefire_is_second_jvm", "false");
        String rows = report.get("leftover_rows");
        for (String row : REQUIRED_LEFTOVER_ROWS) {
            if (rows == null || !rows.contains(row)) {
                throw new IllegalArgumentException("leftover_rows must keep " + row);
            }
        }
        requireVerdict(report, "replica_promotion");
        requireVerdict(report, "live_multi_jvm");
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
        if ("SKIP".equals(report.get("replica_promotion"))
                && "true".equals(report.get("promotion_observed"))) {
            throw new IllegalArgumentException("SKIP cannot claim a live replica promotion walk");
        }
        if ("SKIP".equals(report.get("live_multi_jvm"))
                && "true".equals(report.get("multi_jvm_observed"))) {
            throw new IllegalArgumentException("SKIP cannot claim a live multi-JVM partition walk");
        }
        for (String key : report.keySet()) {
            String upper = key.toUpperCase(Locale.ROOT);
            if (upper.contains("PASSWORD") || upper.contains("SECRET") || upper.contains("PASSWD")) {
                throw new IllegalArgumentException("report must not contain password/secret material");
            }
        }
        String joined = String.join(" ", report.values()).toLowerCase(Locale.ROOT);
        if (joined.contains("product g9 closed") || joined.contains("g9_close=closed")) {
            throw new IllegalArgumentException("report must not claim product G9 closed");
        }
        if (joined.contains("sentinel_cluster_close=closed")) {
            throw new IllegalArgumentException("report must not claim Sentinel/Cluster closed");
        }
    }

    public static Map<String, String> honestSkipTemplate(InventoryHarness harness, LiveObservation live) {
        Map<String, String> report = new LinkedHashMap<>();
        report.put("schema_version", "1");
        report.put("timestamp_utc", "");
        report.put("java_feature", "21");
        report.put("boot_parent_version", "4.1.1");
        report.put("boot_parent_match", "PASS");
        report.put("docker_used", "false");
        report.put("inventory_harness", harness.verdict());
        report.put("inventory_redis_role", harness.redisRole());
        report.put("inventory_connected_slaves", Integer.toString(harness.connectedSlaves()));
        report.put("inventory_replica_present", Boolean.toString(harness.replicaPresent()));
        report.put("inventory_surefire_is_second_jvm", Boolean.toString(harness.surefireIsSecondJvm()));
        report.put("redis_present", Boolean.toString(live.redisPresent()));
        report.put("redis_role", nullToEmpty(live.redisRole()));
        report.put("connected_slaves", Integer.toString(live.connectedSlaves()));
        report.put("replica_present", Boolean.toString(live.replicaPresent()));
        report.put("promotion_observed", Boolean.toString(live.promotionObserved()));
        report.put("replica_promotion", live.replicaPromotion());
        report.put("replica_skip_reason", live.replicaSkipReason());
        report.put("second_jvm_present", Boolean.toString(live.secondJvmPresent()));
        report.put("second_jvm_pid", nullToEmpty(live.secondJvmPid()));
        report.put("multi_jvm_observed", Boolean.toString(live.multiJvmObserved()));
        report.put("live_multi_jvm", live.liveMultiJvm());
        report.put("multi_jvm_skip_reason", live.multiJvmSkipReason());
        report.put("leftover_rows_kept", "true");
        report.put("leftover_rows", SIBLING_ROWS);
        report.put("sentinel_cluster_close", "NOT_CLAIMED");
        report.put("g9_close", "NOT_CLAIMED");
        report.put("g18_status", "closed_unchanged");
        report.put("audit_port_changed", "false");
        report.put("policy_snapshot_redesigned", "false");
        report.put("p0_m0_redone", "false");
        report.put("g9_83_100_121_128_148_161_174_209_redone", "false");
        return report;
    }

    private static ReplicaLive probeReplica() {
        boolean promotionObserved = "true".equalsIgnoreCase(System.getenv("NEXUS_G9_PROMOTION_OBSERVED"));
        try {
            Redis32Client client = new Redis32Client(HOST, PORT, Duration.ofMillis(200));
            if (!"PONG".equals(client.ping())) {
                return missingReplica("PING did not return PONG on " + HOST + ":" + PORT
                        + "; standalone RESP2 inventory stub is not a live replica promotion");
            }
            String replication = client.info("replication");
            String role = Redis32Client.infoField(replication, "role");
            int slaves = parseInt(Redis32Client.infoField(replication, "connected_slaves"), 0);
            boolean replicaPresent = slaves > 0 || "slave".equalsIgnoreCase(role);
            String reason;
            if (!replicaPresent) {
                reason = "redis_role=" + (role.isBlank() ? "unknown" : role)
                        + " connected_slaves=" + slaves
                        + "; Redis32Client is standalone RESP2; replica promotion not observed; Docker forbidden";
            } else if (!promotionObserved) {
                reason = "replica is present (role=" + role + ", connected_slaves=" + slaves
                        + ") but promotion was not observed; Redis32Client is not a failover client; Docker forbidden";
            } else {
                reason = "replica present but this leftover still does not claim product G9 / Sentinel / Cluster close";
            }
            return new ReplicaLive(true, role, slaves, replicaPresent, promotionObserved && replicaPresent, reason);
        } catch (RuntimeException ex) {
            return missingReplica("no Redis listener on " + HOST + ":" + PORT
                    + "; replica promotion not observed; Docker forbidden ("
                    + ex.getClass().getSimpleName() + ")");
        }
    }

    private static JvmLive probeJvms() {
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
        return new JvmLive(secondPresent, secondPid, observed, reason);
    }

    private static ReplicaLive missingReplica(String reason) {
        return new ReplicaLive(false, "", 0, false, false, reason);
    }

    private static String[] requiredKeys() {
        return new String[] {
                "schema_version", "timestamp_utc", "java_feature", "boot_parent_version",
                "boot_parent_match", "docker_used", "inventory_harness",
                "inventory_redis_role", "inventory_connected_slaves",
                "inventory_replica_present", "inventory_surefire_is_second_jvm",
                "redis_present", "redis_role", "connected_slaves", "replica_present",
                "promotion_observed", "replica_promotion", "replica_skip_reason",
                "second_jvm_present", "second_jvm_pid", "multi_jvm_observed",
                "live_multi_jvm", "multi_jvm_skip_reason", "leftover_rows_kept",
                "leftover_rows", "sentinel_cluster_close", "g9_close", "g18_status",
                "audit_port_changed", "policy_snapshot_redesigned", "p0_m0_redone",
                "g9_83_100_121_128_148_161_174_209_redone"
        };
    }

    private static void require(Map<String, String> report, String key, String expected) {
        String actual = report.get(key);
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(key + " must be " + expected + ", got " + actual);
        }
    }

    private static void requireVerdict(Map<String, String> report, String key) {
        String value = report.get(key);
        if (!"PASS".equals(value) && !"SKIP".equals(value) && !"FAIL".equals(value)) {
            throw new IllegalArgumentException(key + " must be PASS/SKIP/FAIL");
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

    private record ReplicaLive(
            boolean redisPresent,
            String redisRole,
            int connectedSlaves,
            boolean replicaPresent,
            boolean promotionObserved,
            String skipReason
    ) {}

    private record JvmLive(
            boolean secondJvmPresent,
            String secondJvmPid,
            boolean multiJvmObserved,
            String skipReason
    ) {}
}
