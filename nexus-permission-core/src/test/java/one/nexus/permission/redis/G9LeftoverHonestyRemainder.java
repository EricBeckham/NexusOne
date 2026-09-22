package one.nexus.permission.redis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * G9 leftover honesty remainder <em>beyond #237 / #229 / #209 / #197</em>:
 * Sentinel / Cluster, live multi-JVM / replica promotion, and Redis-down /
 * PolicySnapshot invalidate PASS/SKIP. Parent leftover harness fixtures stay
 * always-green {@code PASS}. Live Sentinel/Cluster failover, replica
 * promotion, multi-JVM partition, Redis process-down, and live invalidate
 * stay {@code SKIP} unless those walks were actually observed.
 *
 * <p>Does not redo #83 / #100 / #121 / #128 / #148 / #161 / #174 / #197 /
 * #209 / #229 / #237 product code. Does not redesign {@code PolicySnapshot}.
 * Does not claim product G9 / Sentinel / Cluster close. G18 stays closed.
 * No P0/M0 redo. {@code AuditPort} same-TX rules stay untouched.
 */
public final class G9LeftoverHonestyRemainder {
    public static final String HOST = "127.0.0.1";
    public static final int LIVE_PORT = 6379;
    public static final int DEAD_CLIENT_PORT = 1;
    public static final String G9_CLOSE = "NOT_CLAIMED";
    public static final String SENTINEL_CLUSTER_CLOSE = "NOT_CLAIMED";

    public static final String ROW_237 = "g9-redis-down-invalidate-remainder-honesty";
    public static final String ROW_229 = "g9-live-multi-jvm-replica-remainder-honesty";
    public static final String ROW_209 = "g9-sentinel-cluster-remainder-honesty";
    public static final String ROW_197 = "g9-redis-down-invalidate-remainder";
    public static final String ROW_THIS = "g9-leftover-honesty-remainder";
    public static final String ROW_S0_LEFTOVER = "s0-secret-rotate-getenv-leftover-honesty";
    public static final String ROW_G5_REM = "g5-publishhook-ddl-remainder-honesty";
    public static final String ROW_G17_REM = "g17-mfa-recovery-vue-otp-remainder-honesty";
    public static final String ROW_G10_FILE_REM = "g10-file-object-reconcile-remainder-honesty";
    public static final String ROW_G4_PKCE_REM = "g4-hot-purge-staging-pkce-remainder-honesty";
    public static final String ROW_LINUX_ARM_REM = "linux-arm-profile-remainder-honesty";
    public static final String ROW_G2_REPORT_REM = "g2-h5-every-page-form-write-report-remainder-honesty";
    public static final String ROW_G15_REM = "g15-h5-pkce-miniprogram-remainder-honesty";
    public static final String ROW_G17_SOD_REM = "g17-separation-leftover-honesty-remainder";

    public static final List<String> README_ROWS = List.of(
            "g15-h5-mobile",
            "g15-f010-h5-runtime",
            "g15-miniprogram-webview",
            "g15-app-webview-host",
            "g15-h5-pkce-browser",
            "g15-wx-dt-prod-host",
            "g15-native-bridge-stub",
            "g15-native-bridge-honesty",
            "g15-wx-dt-prod-gap-honesty",
            "g15-wx-dt-native-bridge-honesty",
            "g15-wx-dt-native-bridge-remainder",
            "g15-h5-pkce-miniprogram-remainder-honesty",
            "g7-business-types",
            "g7-withdraw-browser-honesty",
            "g7-query-expense-travel-honesty",
            "g7-withdraw-browser-evidence-honesty",
            "g7-query-expense-travel-honesty-remainder",
            "g17-separation-of-duties",
            "g17-separation-honesty",
            "g17-separation-leftover-honesty",
            "g17-separation-leftover-honesty-remainder",
            "g17-gm-tls-dual-cert",
            "g17-admin-totp-enrollment",
            "g17-totp-enrollment-ux",
            "g17-admin-mfa-recovery-email",
            "g17-mfa-recovery-email-honesty",
            "g17-vue-otp-writeback",
            "g17-mfa-recovery-vue-otp-honesty",
            "g17-mfa-recovery-vue-otp-remainder-honesty",
            "g17-gm-tls-nginx-dual-stack",
            "g17-gm-tls-nginx-handshake",
            "g17-gm-tls-nginx-handshake-honesty",
            "g2-form-submission-write",
            "g2-submit-start-form-write",
            "g2-form-write-honesty",
            "g2-h5-permission",
            "g2-h5-every-page",
            "g2-h5-every-page-form-write-honesty",
            "g2-h5-every-page-form-write-remainder-honesty",
            "g2-report-surface-guard",
            "g2-physical-dump-surface",
            "g2-report-physical-dump-honesty",
            "g2-h5-every-page-form-write-report-remainder-honesty",
            "g1-queryast-product-optin",
            "g1-datascope-or-in-ast",
            "g1-queryast-boundsql-guard",
            "g1-queryast-boundsql-honesty",
            "g1-queryast-export-honesty",
            "g2-surfaceguard-bypass-ci",
            "g2-tenant-only-catalog",
            "g2-todo-http-surface",
            "g2-ops-pg-dump",
            "g2-ops-pg-dump-honesty",
            "g4-hot-purge-90d",
            "g4-hot-purge-90d-honesty",
            "g4-pkce-browser-click",
            "g4-guard-deny-audit",
            "g4-export-wizard",
            "g4-export-wizard-deny",
            "g4-export-wizard-deny-honesty",
            "g4-p1-staging-audit-events",
            "g4-p1-staging-audit-events-honesty",
            "g4-hot-purge-staging-leftover-honesty",
            "g4-pkce-spa-verifier",
            "g4-pkce-spa-verifier-honesty",
            "g4-pkce-spa-verifier-remainder-honesty",
            "g4-hot-purge-staging-pkce-remainder-honesty",
            "g6-recovery-port-dry-run",
            "g6-recovery-port-honesty",
            "g6-recovery-port-dry-run-honesty",
            "g6-recovery-port-dry-run-remainder-honesty",
            "g10-identity-drift-dry-run",
            "g10-file-reconcile-honesty",
            "g10-identity-drift-honesty",
            "g10-leftover-honesty",
            "g10-leftover-honesty-remainder",
            "g10-file-object-reconcile-remainder-honesty",
            "linux-arm-profile-honesty",
            "linux-arm-profile-leftover-honesty",
            "linux-arm-profile-remainder-honesty",
            "g5-mid-publish-failure",
            "g5-browser-failed-publish",
            "g5-mid-publish-failure-honesty",
            "g5-publishhook-ddl-honesty",
            "g5-publishhook-ddl-remainder-honesty",
            "g9-multinode-partition",
            "g9-sentinel-cluster-honesty",
            "g9-sentinel-cluster-remainder-honesty",
            "g9-live-multi-jvm-replica",
            "g9-live-multi-jvm-replica-remainder-honesty",
            "g9-policysnapshot-invalidate",
            "g9-failover-remainder",
            "g9-redis-down-invalidate-honesty",
            "g9-redis-down-invalidate-remainder",
            "g9-redis-down-invalidate-remainder-honesty",
            "g9-leftover-honesty-remainder",
            "g1-export-fieldpolicy-drop",
            "g1-fieldpolicy-mask-second-auth",
            "g1-fieldpolicy-mask-second-auth-honesty",
            "g1-fieldpolicy-mask-second-auth-remainder-honesty",
            "g3-outbox-consume-idempotent",
            "g3-claim-crash-window",
            "g3-outbox-consume-crash-honesty",
            "g3-outbox-consume-idempotent-honesty",
            "s0-secret-rotate-without-restart",
            "s0-secret-getenv-snapshot-honesty",
            "s0-secret-rotate-without-restart-honesty",
            "s0-secret-rotate-getenv-remainder-honesty",
            "s0-secret-rotate-getenv-leftover-honesty",
            "s0s1-live-redis-minio-isolation-honesty"
    );

    public static final String SIBLING_ROWS = String.join(",", README_ROWS);

    public record ParentHarness(
            String leftover237,
            String leftover229,
            String leftover209,
            String leftover197,
            String leftover237Cells,
            String leftover229Cells,
            String leftover209Cells,
            String leftover197Cells
    ) {}

    public record LiveInventory(
            boolean redisPresent,
            String redisRole,
            boolean sentinelPresent,
            boolean clusterEnabled,
            boolean replicaPresent,
            boolean secondJvmPresent,
            String liveRedisDownSkipReason,
            String liveInvalidateSkipReason,
            String sentinelSkipReason,
            String clusterSkipReason,
            String replicaSkipReason,
            String multiJvmSkipReason
    ) {
        public String liveRedisDown() {
            return "SKIP";
        }

        public String liveInvalidate() {
            return "SKIP";
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

    private G9LeftoverHonestyRemainder() {}

    /**
     * Parent leftover harness still verifies. This is not a redo of #237 /
     * #229 / #209 / #197 product code — it only reads the committed honesty
     * fixtures.
     */
    public static ParentHarness proveParentHarness() {
        Map<String, String> leftover237 = parseReport(parentFixture(ROW_237, "honest-skip.env"));
        if (!"PASS".equals(leftover237.get("leftover_197_cells_honest"))
                || !"PASS".equals(leftover237.get("leftover_174_cells_honest"))
                || !"PASS".equals(leftover237.get("leftover_83_cells_honest"))
                || !"PASS".equals(leftover237.get("leftover_121_cells_honest"))
                || !"SKIP".equals(leftover237.get("live_redis_down"))
                || !"SKIP".equals(leftover237.get("live_invalidate"))
                || !"NOT_CLAIMED".equals(leftover237.get("g9_close"))
                || !"false".equals(leftover237.get("leftover_197_redone"))
                || !"PASS".equals(leftover237.get("dead_client_harness"))) {
            throw new IllegalStateException("#237 leftover cells are no longer honest");
        }
        Map<String, String> leftover229 = parseReport(parentFixture(ROW_229, "honest-skip.env"));
        if (!"PASS".equals(leftover229.get("inventory_harness"))
                || !"SKIP".equals(leftover229.get("replica_promotion"))
                || !"SKIP".equals(leftover229.get("live_multi_jvm"))
                || !"NOT_CLAIMED".equals(leftover229.get("g9_close"))
                || !"NOT_CLAIMED".equals(leftover229.get("sentinel_cluster_close"))
                || !"false".equals(leftover229.get("g9_83_100_121_128_148_161_174_209_redone"))) {
            throw new IllegalStateException("#229 leftover cells are no longer honest");
        }
        Map<String, String> leftover209 = parseReport(parentFixture(ROW_209, "honest-skip.env"));
        if (!"PASS".equals(leftover209.get("inventory_harness"))
                || !"SKIP".equals(leftover209.get("sentinel_failover"))
                || !"SKIP".equals(leftover209.get("cluster_failover"))
                || !"NOT_CLAIMED".equals(leftover209.get("g9_close"))
                || !"false".equals(leftover209.get("g9_83_100_121_128_148_161_174_redone"))) {
            throw new IllegalStateException("#209 leftover cells are no longer honest");
        }
        Map<String, String> leftover197 = parseReport(parentFixture(ROW_197, "honest-skip.env"));
        if (!"PASS".equals(leftover197.get("dead_client_harness"))
                || !"true".equals(leftover197.get("dead_client_is_not_live_down"))
                || !"SKIP".equals(leftover197.get("live_redis_down"))
                || !"SKIP".equals(leftover197.get("live_invalidate"))
                || !"NOT_CLAIMED".equals(leftover197.get("g9_close"))) {
            throw new IllegalStateException("#197 leftover cells are no longer honest");
        }
        return new ParentHarness("PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS");
    }

    public static LiveInventory probeLive() {
        boolean listed = listedObservationEnv();
        try {
            Redis32Client client = new Redis32Client(HOST, LIVE_PORT, Duration.ofMillis(200));
            if (!"PONG".equals(client.ping())) {
                return missing("PING did not return PONG on " + HOST + ":" + LIVE_PORT
                        + "; parent leftover harness PASS is not a live walk; Docker forbidden"
                        + listedSuffix(listed));
            }
            String server = client.info("server");
            String mode = Redis32Client.infoField(server, "redis_mode");
            if (mode.isBlank()) {
                mode = "standalone";
            }
            String replication = client.info("replication");
            String role = Redis32Client.infoField(replication, "role");
            int slaves = parseInt(Redis32Client.infoField(replication, "connected_slaves"), 0);
            boolean replicaPresent = slaves > 0 || "slave".equalsIgnoreCase(role);
            boolean sentinelCommand = client.tryCommand("SENTINEL", "MASTERS").isPresent();
            boolean sentinelPresent = "sentinel".equalsIgnoreCase(mode) || sentinelCommand;
            boolean clusterEnabled = "cluster".equalsIgnoreCase(mode)
                    || client.tryCommand("CLUSTER", "INFO")
                    .map(body -> body.contains("cluster_enabled:1") || body.contains("cluster_state:ok"))
                    .orElse(false);
            JvmLive jvms = probeJvms();
            String downReason = "Redis is listening on " + HOST + ":" + LIVE_PORT
                    + " (role=" + (role.isBlank() ? "unknown" : role)
                    + ") but a live Redis process-down was not observed; dead client on port "
                    + DEAD_CLIENT_PORT + " is not live Redis-down; Docker forbidden"
                    + listedSuffix(listed);
            String invalidateReason = "Redis is listening on " + HOST + ":" + LIVE_PORT
                    + " but a live PUBLISH/SUBSCRIBE invalidate walk was not observed; "
                    + "#174/#197/#237 inventory is not a live invalidate PASS"
                    + listedSuffix(listed);
            String sentinelReason = sentinelPresent
                    ? "Redis Sentinel process is present but no failover was observed; SENTINEL MASTERS is not a failover; Docker forbidden"
                    + listedSuffix(listed)
                    : "redis_mode=" + mode + "; no redis-sentinel / :26379 / SENTINEL MASTERS; standalone RESP2 only; live Sentinel failover not observed; Docker forbidden"
                    + listedSuffix(listed);
            String clusterReason = clusterEnabled
                    ? "Redis Cluster mode is present but no slot failover was observed; Docker forbidden"
                    + listedSuffix(listed)
                    : "redis_mode=" + mode + "; no Redis Cluster slots / ASK / MOVED; live Cluster failover not observed; Docker forbidden"
                    + listedSuffix(listed);
            String replicaReason;
            if (!replicaPresent) {
                replicaReason = "redis_role=" + (role.isBlank() ? "unknown" : role)
                        + " connected_slaves=" + slaves
                        + "; Redis32Client is standalone RESP2; replica promotion not observed; Docker forbidden"
                        + listedSuffix(listed);
            } else {
                replicaReason = "replica is present (role=" + role + ", connected_slaves=" + slaves
                        + ") but promotion was not observed; Redis32Client is not a failover client; Docker forbidden"
                        + listedSuffix(listed);
            }
            return new LiveInventory(
                    true,
                    role,
                    sentinelPresent,
                    clusterEnabled,
                    replicaPresent,
                    jvms.secondJvmPresent,
                    downReason,
                    invalidateReason,
                    sentinelReason,
                    clusterReason,
                    replicaReason,
                    jvms.skipReason + listedSuffix(listed));
        } catch (RuntimeException ex) {
            return missing("no Redis listener on " + HOST + ":" + LIVE_PORT
                    + "; parent leftover harness PASS is not a live walk; Docker forbidden ("
                    + ex.getClass().getSimpleName() + ")"
                    + listedSuffix(listed));
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
        List<String> errors = verifyErrors(report);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(errors.getFirst());
        }
    }

    public static List<String> verifyErrors(Map<String, String> report) {
        List<String> errors = new ArrayList<>();
        if (report == null || report.isEmpty()) {
            errors.add("empty report");
            return errors;
        }
        for (String key : requiredKeys()) {
            if (!report.containsKey(key)) {
                errors.add("missing key " + key);
            }
        }
        require(errors, report, "java_feature", "21");
        require(errors, report, "boot_parent_version", "4.1.1");
        require(errors, report, "boot_parent_match", "PASS");
        require(errors, report, "docker_used", "false");
        require(errors, report, "g18_status", "closed_unchanged");
        require(errors, report, "audit_port_changed", "false");
        require(errors, report, "policy_snapshot_redesigned", "false");
        require(errors, report, "p0_m0_redone", "false");
        require(errors, report, "g9_close", G9_CLOSE);
        require(errors, report, "sentinel_cluster_close", SENTINEL_CLUSTER_CLOSE);
        require(errors, report, "leftover_237_harness", "PASS");
        require(errors, report, "leftover_229_harness", "PASS");
        require(errors, report, "leftover_209_harness", "PASS");
        require(errors, report, "leftover_197_harness", "PASS");
        require(errors, report, "leftover_237_cells_honest", "PASS");
        require(errors, report, "leftover_229_cells_honest", "PASS");
        require(errors, report, "leftover_209_cells_honest", "PASS");
        require(errors, report, "leftover_197_cells_honest", "PASS");
        require(errors, report, "leftover_rows_kept", "true");
        require(errors, report, "dead_client_is_not_live_down", "true");
        require(errors, report, "redo_237", "false");
        require(errors, report, "redo_229", "false");
        require(errors, report, "redo_209", "false");
        require(errors, report, "redo_197", "false");
        require(errors, report, "g9_83_100_121_128_148_161_174_197_209_229_237_redone", "false");

        String leftoverRows = value(report, "leftover_rows");
        for (String row : README_ROWS) {
            if (!leftoverRows.contains(row)) {
                errors.add("leftover_rows must keep " + row);
            }
        }

        refusePassWithout(errors, report, "live_redis_down", "redis_present", "true",
                "live_redis_down=PASS requires redis_present=true");
        refusePassWithout(errors, report, "live_redis_down", "redis_down_observed", "true",
                "live_redis_down=PASS requires redis_down_observed=true");
        refusePassWithout(errors, report, "live_invalidate", "redis_present", "true",
                "live_invalidate=PASS requires redis_present=true");
        refusePassWithout(errors, report, "live_invalidate", "live_invalidate_observed", "true",
                "live_invalidate=PASS requires live_invalidate_observed=true");
        refusePassWithout(errors, report, "sentinel_failover", "sentinel_present", "true",
                "sentinel_failover=PASS requires sentinel_present=true");
        refusePassWithout(errors, report, "sentinel_failover", "sentinel_failover_observed", "true",
                "sentinel_failover=PASS requires sentinel_failover_observed=true");
        refusePassWithout(errors, report, "cluster_failover", "cluster_enabled", "true",
                "cluster_failover=PASS requires cluster_enabled=true");
        refusePassWithout(errors, report, "cluster_failover", "cluster_failover_observed", "true",
                "cluster_failover=PASS requires cluster_failover_observed=true");
        refusePassWithout(errors, report, "replica_promotion", "replica_present", "true",
                "replica_promotion=PASS requires replica_present=true");
        refusePassWithout(errors, report, "replica_promotion", "promotion_observed", "true",
                "replica_promotion=PASS requires promotion_observed=true");
        refusePassWithout(errors, report, "live_multi_jvm", "second_jvm_present", "true",
                "live_multi_jvm=PASS requires second_jvm_present=true");
        refusePassWithout(errors, report, "live_multi_jvm", "multi_jvm_observed", "true",
                "live_multi_jvm=PASS requires multi_jvm_observed=true");

        requireVerdict(errors, report, "live_redis_down");
        requireVerdict(errors, report, "live_invalidate");
        requireVerdict(errors, report, "sentinel_failover");
        requireVerdict(errors, report, "cluster_failover");
        requireVerdict(errors, report, "replica_promotion");
        requireVerdict(errors, report, "live_multi_jvm");
        requireSkipReason(errors, report, "live_redis_down", "live_redis_down_skip_reason", "redis_down_observed");
        requireSkipReason(errors, report, "live_invalidate", "live_invalidate_skip_reason", "live_invalidate_observed");
        requireSkipReason(errors, report, "sentinel_failover", "sentinel_skip_reason", "sentinel_failover_observed");
        requireSkipReason(errors, report, "cluster_failover", "cluster_skip_reason", "cluster_failover_observed");
        requireSkipReason(errors, report, "replica_promotion", "replica_skip_reason", "promotion_observed");
        requireSkipReason(errors, report, "live_multi_jvm", "multi_jvm_skip_reason", "multi_jvm_observed");

        for (String key : report.keySet()) {
            String upper = key.toUpperCase(Locale.ROOT);
            if (upper.contains("PASSWORD") || upper.contains("SECRET") || upper.contains("PASSWD")) {
                errors.add("report must not contain password/secret material");
            }
        }
        return errors;
    }

    public static Map<String, String> honestSkipTemplate(ParentHarness parent, LiveInventory live) {
        Map<String, String> report = new LinkedHashMap<>();
        report.put("schema_version", "1");
        report.put("timestamp_utc", "");
        report.put("java_feature", "21");
        report.put("boot_parent_version", "4.1.1");
        report.put("boot_parent_match", "PASS");
        report.put("docker_used", "false");
        report.put("leftover_237_harness", parent.leftover237());
        report.put("leftover_229_harness", parent.leftover229());
        report.put("leftover_209_harness", parent.leftover209());
        report.put("leftover_197_harness", parent.leftover197());
        report.put("leftover_237_cells_honest", parent.leftover237Cells());
        report.put("leftover_229_cells_honest", parent.leftover229Cells());
        report.put("leftover_209_cells_honest", parent.leftover209Cells());
        report.put("leftover_197_cells_honest", parent.leftover197Cells());
        report.put("leftover_rows_kept", "true");
        report.put("leftover_rows", SIBLING_ROWS);
        report.put("dead_client_is_not_live_down", "true");
        report.put("simulated_dead_client_port", Integer.toString(DEAD_CLIENT_PORT));
        report.put("redis_present", Boolean.toString(live.redisPresent()));
        report.put("redis_role", nullToEmpty(live.redisRole()));
        report.put("redis_down_observed", "false");
        report.put("live_redis_down", live.liveRedisDown());
        report.put("live_redis_down_skip_reason", live.liveRedisDownSkipReason());
        report.put("live_invalidate_observed", "false");
        report.put("live_invalidate", live.liveInvalidate());
        report.put("live_invalidate_skip_reason", live.liveInvalidateSkipReason());
        report.put("sentinel_present", Boolean.toString(live.sentinelPresent()));
        report.put("sentinel_failover_observed", "false");
        report.put("sentinel_failover", live.sentinelFailover());
        report.put("sentinel_skip_reason", live.sentinelSkipReason());
        report.put("cluster_enabled", Boolean.toString(live.clusterEnabled()));
        report.put("cluster_failover_observed", "false");
        report.put("cluster_failover", live.clusterFailover());
        report.put("cluster_skip_reason", live.clusterSkipReason());
        report.put("replica_present", Boolean.toString(live.replicaPresent()));
        report.put("promotion_observed", "false");
        report.put("replica_promotion", live.replicaPromotion());
        report.put("replica_skip_reason", live.replicaSkipReason());
        report.put("second_jvm_present", Boolean.toString(live.secondJvmPresent()));
        report.put("multi_jvm_observed", "false");
        report.put("live_multi_jvm", live.liveMultiJvm());
        report.put("multi_jvm_skip_reason", live.multiJvmSkipReason());
        report.put("g9_close", G9_CLOSE);
        report.put("sentinel_cluster_close", SENTINEL_CLUSTER_CLOSE);
        report.put("g18_status", "closed_unchanged");
        report.put("audit_port_changed", "false");
        report.put("policy_snapshot_redesigned", "false");
        report.put("p0_m0_redone", "false");
        report.put("redo_237", "false");
        report.put("redo_229", "false");
        report.put("redo_209", "false");
        report.put("redo_197", "false");
        report.put("g9_83_100_121_128_148_161_174_197_209_229_237_redone", "false");
        return report;
    }

    private static String[] requiredKeys() {
        return new String[] {
                "schema_version", "timestamp_utc", "java_feature", "boot_parent_version",
                "boot_parent_match", "docker_used", "leftover_237_harness",
                "leftover_229_harness", "leftover_209_harness", "leftover_197_harness",
                "leftover_237_cells_honest", "leftover_229_cells_honest",
                "leftover_209_cells_honest", "leftover_197_cells_honest",
                "leftover_rows_kept", "leftover_rows", "dead_client_is_not_live_down",
                "simulated_dead_client_port", "redis_present", "redis_role",
                "redis_down_observed", "live_redis_down", "live_redis_down_skip_reason",
                "live_invalidate_observed", "live_invalidate", "live_invalidate_skip_reason",
                "sentinel_present", "sentinel_failover_observed", "sentinel_failover",
                "sentinel_skip_reason", "cluster_enabled", "cluster_failover_observed",
                "cluster_failover", "cluster_skip_reason", "replica_present",
                "promotion_observed", "replica_promotion", "replica_skip_reason",
                "second_jvm_present", "multi_jvm_observed", "live_multi_jvm",
                "multi_jvm_skip_reason", "g9_close", "sentinel_cluster_close",
                "g18_status", "audit_port_changed", "policy_snapshot_redesigned",
                "p0_m0_redone", "redo_237", "redo_229", "redo_209", "redo_197",
                "g9_83_100_121_128_148_161_174_197_209_229_237_redone"
        };
    }

    private static Path parentFixture(String leftover, String name) {
        Path first = Path.of("scripts/" + leftover + "/fixtures/" + name);
        if (Files.isRegularFile(first)) {
            return first;
        }
        return Path.of("../scripts/" + leftover + "/fixtures/" + name);
    }

    private static JvmLive probeJvms() {
        String envPid = Optional.ofNullable(System.getenv("NEXUS_G9_SECOND_JVM_PID")).orElse("");
        long self = ProcessHandle.current().pid();
        boolean secondPresent = false;
        if (!envPid.isBlank()) {
            try {
                long pid = Long.parseLong(envPid.trim());
                if (pid != self && ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
                    secondPresent = true;
                }
            } catch (NumberFormatException ignored) {
                secondPresent = false;
            }
        }
        String reason;
        if (!secondPresent) {
            reason = "one application JVM (pid=" + self
                    + "); surefire parent/fork is not a live multi-JVM partition; no second JAR, no iptables/netem, Docker forbidden";
        } else {
            reason = "second JVM is listed but a live partition / netem / two-JAR walk was not observed";
        }
        return new JvmLive(secondPresent, reason);
    }

    private static LiveInventory missing(String reason) {
        JvmLive jvms = probeJvms();
        return new LiveInventory(
                false, "", false, false, false, jvms.secondJvmPresent,
                reason, reason, reason, reason, reason, jvms.skipReason);
    }

    private static boolean listedObservationEnv() {
        return truthy("NEXUS_G9_REDIS_DOWN_OBSERVED")
                || truthy("NEXUS_G9_LIVE_INVALIDATE_OBSERVED")
                || truthy("NEXUS_G9_SENTINEL_FAILOVER_OBSERVED")
                || truthy("NEXUS_G9_CLUSTER_FAILOVER_OBSERVED")
                || truthy("NEXUS_G9_PROMOTION_OBSERVED")
                || truthy("NEXUS_G9_MULTI_JVM_OBSERVED");
    }

    private static String listedSuffix(boolean listed) {
        return listed ? "; listed observation env is not a live walk" : "";
    }

    private static boolean truthy(String name) {
        return "true".equalsIgnoreCase(env(name));
    }

    private static void require(List<String> errors, Map<String, String> report, String key, String expected) {
        String actual = value(report, key);
        if (!expected.equals(actual)) {
            errors.add(key + " must be " + expected + ", got " + actual);
        }
    }

    private static void requireVerdict(List<String> errors, Map<String, String> report, String key) {
        String actual = value(report, key);
        if (!"PASS".equals(actual) && !"SKIP".equals(actual) && !"FAIL".equals(actual)) {
            errors.add(key + " must be PASS/SKIP/FAIL");
        }
    }

    private static void refusePassWithout(
            List<String> errors,
            Map<String, String> report,
            String verdictKey,
            String requiredKey,
            String requiredValue,
            String message
    ) {
        if ("PASS".equals(value(report, verdictKey)) && !requiredValue.equals(value(report, requiredKey))) {
            errors.add(message);
        }
    }

    private static void requireSkipReason(
            List<String> errors,
            Map<String, String> report,
            String verdictKey,
            String reasonKey,
            String observedKey
    ) {
        if ("SKIP".equals(value(report, verdictKey))) {
            if (blank(value(report, reasonKey))) {
                errors.add(verdictKey + " SKIP needs " + reasonKey);
            }
            if ("true".equals(value(report, observedKey))) {
                errors.add("SKIP cannot claim " + observedKey + "=true");
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

    private static String value(Map<String, String> report, String key) {
        String raw = report.get(key);
        return raw == null ? "" : raw;
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record JvmLive(boolean secondJvmPresent, String skipReason) {}
}
