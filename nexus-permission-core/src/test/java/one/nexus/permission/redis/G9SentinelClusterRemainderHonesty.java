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
import java.util.TreeMap;

/**
 * G9 leftover honesty inventory <em>beyond #148 / #161</em>: Sentinel /
 * Cluster remainder PASS/SKIP. The standalone INFO / SENTINEL|CLUSTER
 * inventory stub may PASS. Live Sentinel / Cluster observation stays
 * SKIP unless the topology is present <strong>and</strong> a failover
 * was observed.
 *
 * <p>Does not redo #83 / #100 / #121 / #128 / #148 / #161 / #174 product
 * code. Does not redesign {@code PolicySnapshot}. Does not claim product
 * G9 close. G18 stays closed. No P0/M0 redo.
 */
public final class G9SentinelClusterRemainderHonesty {
    public static final String HOST = "127.0.0.1";
    public static final int PORT = 6379;
    public static final String STUB_VERSION = "3.2.13";
    public static final String STUB_MODE = "standalone";
    public static final String STUB_ROLE = "master";

    public static final String ROW_SENTINEL = "g9-sentinel-cluster-honesty";
    public static final String ROW_REPLICA = "g9-live-multi-jvm-replica";
    public static final String ROW_INVALIDATE = "g9-policysnapshot-invalidate";
    public static final String ROW_FAILOVER = "g9-failover-remainder";
    public static final String ROW_REDIS_DOWN = "g9-redis-down-invalidate-honesty";
    public static final String ROW_G3 = "g3-outbox-consume-crash-honesty";
    public static final String ROW_G5 = "g5-mid-publish-failure-honesty";
    public static final String ROW_G10 = "g10-leftover-honesty";
    public static final String ROW_G4 = "g4-export-wizard-deny-honesty";
    public static final String ROW_G2 = "g2-h5-every-page-form-write-honesty";
    public static final String ROW_REDIS_REM = "g9-redis-down-invalidate-remainder";
    public static final String ROW_G17 = "g17-mfa-recovery-vue-otp-honesty";
    public static final String ROW_G2_OPS = "g2-ops-pg-dump-honesty";
    public static final String SIBLING_ROWS = ROW_SENTINEL + "," + ROW_REPLICA + ","
            + ROW_INVALIDATE + "," + ROW_FAILOVER + "," + ROW_REDIS_DOWN + ","
            + ROW_G3 + "," + ROW_G5 + "," + ROW_G10 + "," + ROW_G4 + "," + ROW_G2
            + "," + ROW_REDIS_REM + "," + ROW_G17 + "," + ROW_G2_OPS;

    public static final List<String> README_ROWS = List.of(
            ROW_SENTINEL,
            ROW_REPLICA,
            ROW_INVALIDATE,
            ROW_FAILOVER,
            ROW_REDIS_DOWN,
            "g9-multinode-partition",
            ROW_G3,
            "g3-claim-crash-window",
            "g3-outbox-consume-idempotent",
            ROW_G5,
            "g5-mid-publish-failure",
            "g5-browser-failed-publish",
            ROW_G10,
            "g10-file-reconcile-honesty",
            "g10-identity-drift-honesty",
            ROW_G4,
            "g4-export-wizard-deny",
            "g4-guard-deny-audit",
            ROW_G2,
            "g2-h5-every-page",
            "g2-form-write-honesty",
            ROW_REDIS_REM,
            ROW_G17,
            ROW_G2_OPS,
            "g2-ops-pg-dump"
    );

    public record InventoryHarness(
            String redisVersion,
            String redisMode,
            String redisRole,
            int connectedSlaves,
            boolean sentinelCommandPresent,
            boolean clusterEnabled,
            String verdict
    ) {}

    public record LiveObservation(
            boolean redisPresent,
            boolean sentinelPresent,
            boolean clusterEnabled,
            boolean sentinelFailoverObserved,
            boolean clusterFailoverObserved,
            String sentinelSkipReason,
            String clusterSkipReason
    ) {
        public String sentinelFailover() {
            return "SKIP";
        }

        public String clusterFailover() {
            return "SKIP";
        }
    }

    private G9SentinelClusterRemainderHonesty() {}

    /**
     * Standalone Redis 3.2 INFO stub. Proves {@link Redis32Client#infoField}
     * inventories {@code redis_mode}/{@code role}/{@code connected_slaves}
     * without a live Sentinel or Cluster. SENTINEL/CLUSTER commands stay
     * absent on the stub. This is not a live failover.
     */
    public static InventoryHarness proveStandaloneInventoryStub() {
        String server = "# Server\r\nredis_version:" + STUB_VERSION + "\r\nredis_mode:" + STUB_MODE + "\r\n";
        String replication = "# Replication\r\nrole:" + STUB_ROLE + "\r\nconnected_slaves:0\r\n";
        String version = Redis32Client.infoField(server, "redis_version");
        String mode = Redis32Client.infoField(server, "redis_mode");
        String role = Redis32Client.infoField(replication, "role");
        int slaves = parseInt(Redis32Client.infoField(replication, "connected_slaves"), 0);
        boolean sentinelCommand = false;
        boolean clusterEnabled = "cluster".equalsIgnoreCase(mode);
        boolean pass = STUB_VERSION.equals(version)
                && STUB_MODE.equals(mode)
                && STUB_ROLE.equals(role)
                && slaves == 0
                && !sentinelCommand
                && !clusterEnabled;
        return new InventoryHarness(version, mode, role, slaves, sentinelCommand, clusterEnabled,
                pass ? "PASS" : "FAIL");
    }

    public static LiveObservation probeLive() {
        boolean sentinelObserved = "true".equalsIgnoreCase(System.getenv("NEXUS_G9_SENTINEL_FAILOVER_OBSERVED"));
        boolean clusterObserved = "true".equalsIgnoreCase(System.getenv("NEXUS_G9_CLUSTER_FAILOVER_OBSERVED"));
        try {
            Redis32Client client = new Redis32Client(HOST, PORT, Duration.ofMillis(200));
            if (!"PONG".equals(client.ping())) {
                return missing("PING did not return PONG on " + HOST + ":" + PORT
                        + "; standalone RESP2 inventory stub is not a live Sentinel/Cluster failover");
            }
            String server = client.info("server");
            String mode = Redis32Client.infoField(server, "redis_mode");
            if (mode.isBlank()) {
                mode = "standalone";
            }
            boolean sentinelCommand = client.tryCommand("SENTINEL", "MASTERS").isPresent();
            boolean sentinelPresent = "sentinel".equalsIgnoreCase(mode) || sentinelCommand;
            boolean clusterEnabled = "cluster".equalsIgnoreCase(mode)
                    || client.tryCommand("CLUSTER", "INFO")
                    .map(body -> body.contains("cluster_enabled:1") || body.contains("cluster_state:ok"))
                    .orElse(false);
            String sentinelReason;
            if (!sentinelPresent) {
                sentinelReason = "redis_mode=" + mode
                        + "; no redis-sentinel / :26379 / SENTINEL MASTERS; standalone RESP2 only; "
                        + "live Sentinel failover not observed; Docker forbidden";
            } else if (!sentinelObserved) {
                sentinelReason = "Redis Sentinel process is present but no failover was observed; "
                        + "SENTINEL MASTERS is not a failover; Docker forbidden";
            } else {
                sentinelReason = "live Sentinel failover listed but this leftover still does not claim product G9 close";
            }
            String clusterReason;
            if (!clusterEnabled) {
                clusterReason = "redis_mode=" + mode
                        + "; no Redis Cluster slots / ASK / MOVED; live Cluster failover not observed; Docker forbidden";
            } else if (!clusterObserved) {
                clusterReason = "Redis Cluster mode is present but no slot failover was observed; Docker forbidden";
            } else {
                clusterReason = "live Cluster failover listed but this leftover still does not claim product G9 close";
            }
            return new LiveObservation(true, sentinelPresent, clusterEnabled, sentinelObserved, clusterObserved,
                    sentinelReason, clusterReason);
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
        require(report, "p0_m0_redone", "false");
        require(report, "g9_83_100_121_128_148_161_174_redone", "false");
        require(report, "g9_close", "NOT_CLAIMED");
        require(report, "leftover_rows_kept", "true");
        require(report, "inventory_harness", "PASS");
        require(report, "inventory_redis_version", STUB_VERSION);
        require(report, "inventory_redis_mode", STUB_MODE);
        require(report, "inventory_redis_role", STUB_ROLE);
        require(report, "inventory_connected_slaves", "0");
        require(report, "inventory_sentinel_command", "false");
        require(report, "inventory_cluster_enabled", "false");
        String rows = report.get("leftover_rows");
        for (String row : List.of(ROW_SENTINEL, ROW_REPLICA, ROW_INVALIDATE, ROW_FAILOVER, ROW_REDIS_DOWN, ROW_G3, ROW_G5, ROW_G10, ROW_G4, ROW_G2, ROW_REDIS_REM)) {
            if (rows == null || !rows.contains(row)) {
                throw new IllegalArgumentException("leftover_rows must keep " + row);
            }
        }
        requireVerdict(report, "sentinel_failover");
        requireVerdict(report, "cluster_failover");
        refusePassWithout(report, "sentinel_failover", "sentinel_present", "true",
                "sentinel_failover=PASS requires sentinel_present=true");
        refusePassWithout(report, "sentinel_failover", "sentinel_failover_observed", "true",
                "sentinel_failover=PASS requires sentinel_failover_observed=true");
        refusePassWithout(report, "cluster_failover", "cluster_enabled", "true",
                "cluster_failover=PASS requires cluster_enabled=true");
        refusePassWithout(report, "cluster_failover", "cluster_failover_observed", "true",
                "cluster_failover=PASS requires cluster_failover_observed=true");
        requireSkipReason(report, "sentinel_failover", "sentinel_skip_reason");
        requireSkipReason(report, "cluster_failover", "cluster_skip_reason");
        if ("SKIP".equals(report.get("sentinel_failover"))
                && "true".equals(report.get("sentinel_failover_observed"))) {
            throw new IllegalArgumentException("SKIP cannot claim a live Sentinel failover walk");
        }
        if ("SKIP".equals(report.get("cluster_failover"))
                && "true".equals(report.get("cluster_failover_observed"))) {
            throw new IllegalArgumentException("SKIP cannot claim a live Cluster failover walk");
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
        report.put("inventory_redis_version", harness.redisVersion());
        report.put("inventory_redis_mode", harness.redisMode());
        report.put("inventory_redis_role", harness.redisRole());
        report.put("inventory_connected_slaves", Integer.toString(harness.connectedSlaves()));
        report.put("inventory_sentinel_command", Boolean.toString(harness.sentinelCommandPresent()));
        report.put("inventory_cluster_enabled", Boolean.toString(harness.clusterEnabled()));
        report.put("redis_present", Boolean.toString(live.redisPresent()));
        report.put("sentinel_present", Boolean.toString(live.sentinelPresent()));
        report.put("cluster_enabled", Boolean.toString(live.clusterEnabled()));
        report.put("sentinel_failover_observed", Boolean.toString(live.sentinelFailoverObserved()));
        report.put("cluster_failover_observed", Boolean.toString(live.clusterFailoverObserved()));
        report.put("sentinel_failover", live.sentinelFailover());
        report.put("sentinel_skip_reason", live.sentinelSkipReason());
        report.put("cluster_failover", live.clusterFailover());
        report.put("cluster_skip_reason", live.clusterSkipReason());
        report.put("leftover_rows_kept", "true");
        report.put("leftover_rows", SIBLING_ROWS);
        report.put("g9_close", "NOT_CLAIMED");
        report.put("g18_status", "closed_unchanged");
        report.put("audit_port_changed", "false");
        report.put("policy_snapshot_redesigned", "false");
        report.put("p0_m0_redone", "false");
        report.put("g9_83_100_121_128_148_161_174_redone", "false");
        return report;
    }

    private static LiveObservation missing(String reason) {
        return new LiveObservation(false, false, false, false, false, reason, reason);
    }

    private static String[] requiredKeys() {
        return new String[] {
                "schema_version", "timestamp_utc", "java_feature", "boot_parent_version",
                "boot_parent_match", "docker_used", "inventory_harness",
                "inventory_redis_version", "inventory_redis_mode", "inventory_redis_role",
                "inventory_connected_slaves", "inventory_sentinel_command",
                "inventory_cluster_enabled", "redis_present", "sentinel_present",
                "cluster_enabled", "sentinel_failover_observed", "cluster_failover_observed",
                "sentinel_failover", "sentinel_skip_reason", "cluster_failover",
                "cluster_skip_reason", "leftover_rows_kept", "leftover_rows",
                "g9_close", "g18_status", "audit_port_changed", "policy_snapshot_redesigned",
                "p0_m0_redone", "g9_83_100_121_128_148_161_174_redone"
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
}
