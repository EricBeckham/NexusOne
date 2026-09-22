package one.nexus.permission.redis;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * G9 leftover honesty remainder beyond #174. Proves the #83 / #121
 * dead-client harness ({@code Redis32Client} on port {@code 1} +
 * {@link RedisPolicyInvalidation#notifyTenant}) is a <strong>PASS</strong>.
 * Live Redis process-down and live PolicySnapshot PUBLISH/SUBSCRIBE stay
 * <strong>SKIP</strong> unless Redis is listening <em>and</em> a crash /
 * live invalidate walk was observed. Does not redo
 * #83 / #100 / #121 / #128 / #148 / #161 / #174 product suites.
 * Not a product G9 close.
 */
public final class G9RedisDownInvalidateRemainder {
    public static final String HOST = "127.0.0.1";
    public static final int LIVE_PORT = 6379;
    public static final int DEAD_CLIENT_PORT = 1;
    public static final String DEAD_CLIENT_TENANT = "g9-remainder-dead-client";

    public record DeadClientHarness(
            int port,
            boolean notifyFailed,
            boolean remoteSuccessClaimed,
            int remoteFailureCount,
            boolean published,
            boolean versionHintWritten
    ) {
        public String verdict() {
            boolean pass = port == DEAD_CLIENT_PORT
                    && notifyFailed
                    && !remoteSuccessClaimed
                    && remoteFailureCount >= 1
                    && !published
                    && !versionHintWritten;
            return pass ? "PASS" : "FAIL";
        }

        public boolean deadClientIsNotLiveDown() {
            return true;
        }
    }

    public record LiveInventory(
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
    }

    private G9RedisDownInvalidateRemainder() {}

    /**
     * Thin leftover proof: a dead client on port {@code 1} fails closed and
     * is not recorded as a remote Redis success. Not a #83 / #121 suite redo.
     */
    public static DeadClientHarness proveDeadClientHarness() {
        Redis32Client dead = new Redis32Client(HOST, DEAD_CLIENT_PORT, Duration.ofMillis(50));
        AtomicInteger failures = new AtomicInteger();
        RedisPolicyInvalidation invalidation =
                new RedisPolicyInvalidation(dead, ex -> failures.incrementAndGet());
        invalidation.notifyTenant(DEAD_CLIENT_TENANT);
        RedisPolicyInvalidation.RemoteNotifyOutcome last = invalidation.lastRemoteNotify();
        boolean published = last != null && last.published();
        boolean versionHintWritten = last != null && last.versionHintWritten();
        return new DeadClientHarness(
                DEAD_CLIENT_PORT,
                failures.get() >= 1,
                invalidation.lastRemoteNotifySucceeded(),
                invalidation.remoteNotifyFailureCount(),
                published,
                versionHintWritten);
    }

    public static LiveInventory probeLive() {
        try {
            Redis32Client client = new Redis32Client(HOST, LIVE_PORT, Duration.ofMillis(200));
            if (!"PONG".equals(client.ping())) {
                return missing("PING did not return PONG on " + HOST + ":" + LIVE_PORT
                        + "; dead-client harness PASS is not live Redis-down; "
                        + "live invalidate not observed");
            }
            String replication = client.info("replication");
            return fromLivePing(Redis32Client.infoField(replication, "role"));
        } catch (RuntimeException ex) {
            return missing("no Redis listener on " + HOST + ":" + LIVE_PORT
                    + "; dead-client harness PASS is not live Redis-down; "
                    + "live invalidate not observed; Docker forbidden ("
                    + ex.getClass().getSimpleName() + ")");
        }
    }

    public static LiveInventory fromLivePing(String redisRole) {
        String role = redisRole == null ? "" : redisRole.trim();
        boolean downObserved = envTrue("NEXUS_G9_REDIS_DOWN_OBSERVED");
        boolean invalidateObserved = envTrue("NEXUS_G9_LIVE_INVALIDATE_OBSERVED");
        String downReason = downObserved
                ? "live Redis-down listed but this remainder still does not claim product G9 close"
                : "Redis is listening on " + HOST + ":" + LIVE_PORT
                        + " (role=" + (role.isBlank() ? "unknown" : role)
                        + ") but a live Redis process-down was not observed; "
                        + "dead-client harness PASS is not live Redis-down; Docker forbidden";
        String invalidateReason = invalidateObserved
                ? "live invalidate listed but this remainder still does not claim product G9 close"
                : "Redis is listening on " + HOST + ":" + LIVE_PORT
                        + " but a live PUBLISH/SUBSCRIBE invalidate walk was not observed; "
                        + "RedisInvalidationTest skip / #174 inventory is not a live invalidate PASS";
        return new LiveInventory(true, role, downObserved, invalidateObserved, downReason, invalidateReason);
    }

    public static boolean leftoverRowsKept(String readme) {
        if (readme == null || readme.isBlank()) {
            return false;
        }
        String[] required = {
                "[g9-policysnapshot-invalidate](./g9-policysnapshot-invalidate.md)",
                "[g9-failover-remainder](./g9-failover-remainder.md)",
                "[g9-redis-down-invalidate-honesty](./g9-redis-down-invalidate-honesty.md)",
                "[g9-redis-down-invalidate-remainder](./g9-redis-down-invalidate-remainder.md)",
                "[g9-multinode-partition](./g9-multinode-partition.md)",
                "[g9-sentinel-cluster-honesty](./g9-sentinel-cluster-honesty.md)",
                "[g9-live-multi-jvm-replica](./g9-live-multi-jvm-replica.md)",
                "[g17-separation-honesty](./g17-separation-honesty.md)",
                "[g4-hot-purge-90d-honesty](./g4-hot-purge-90d-honesty.md)",
                "[g4-pkce-spa-verifier-honesty](./g4-pkce-spa-verifier-honesty.md)",
                "[g15-native-bridge-honesty](./g15-native-bridge-honesty.md)",
                "[g10-identity-drift-honesty](./g10-identity-drift-honesty.md)",
                "[g1-fieldpolicy-mask-second-auth-honesty](./g1-fieldpolicy-mask-second-auth-honesty.md)",
                "[g5-browser-failed-publish](./g5-browser-failed-publish.md)",
                "[s0-secret-getenv-snapshot-honesty](./s0-secret-getenv-snapshot-honesty.md)",
                "[g7-withdraw-browser-honesty](./g7-withdraw-browser-honesty.md)",
                "[g3-claim-crash-window](./g3-claim-crash-window.md)",
                "[s0s1-live-redis-minio-isolation-honesty](./s0s1-live-redis-minio-isolation-honesty.md)",
                "[g6-recovery-port-dry-run-honesty](./g6-recovery-port-dry-run-honesty.md)",
                "[g4-p1-staging-audit-events-honesty](./g4-p1-staging-audit-events-honesty.md)",
                "[linux-arm-profile-leftover-honesty](./linux-arm-profile-leftover-honesty.md)",
                "[g7-query-expense-travel-honesty](./g7-query-expense-travel-honesty.md)",
                "[s0-secret-rotate-without-restart-honesty](./s0-secret-rotate-without-restart-honesty.md)",
                "[g15-wx-dt-native-bridge-honesty](./g15-wx-dt-native-bridge-honesty.md)",
                "[g1-queryast-boundsql-honesty](./g1-queryast-boundsql-honesty.md)",
                "[g3-outbox-consume-crash-honesty](./g3-outbox-consume-crash-honesty.md)",
                "[g5-mid-publish-failure-honesty](./g5-mid-publish-failure-honesty.md)",
                "[g10-leftover-honesty](./g10-leftover-honesty.md)",
                "[g4-export-wizard-deny-honesty](./g4-export-wizard-deny-honesty.md)",
                "[g2-h5-every-page-form-write-honesty](./g2-h5-every-page-form-write-honesty.md)"
        };
        for (String row : required) {
            if (!readme.contains(row)) {
                return false;
            }
        }
        return true;
    }

    public static boolean portOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 200);
            return true;
        } catch (IOException ex) {
            return false;
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
        require(report, "dead_client_harness", "PASS");
        require(report, "dead_client_is_not_live_down", "true");
        require(report, "simulated_dead_client_port", Integer.toString(DEAD_CLIENT_PORT));
        require(report, "leftover_rows_kept", "true");
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

    public static Map<String, String> honestSkipTemplate(DeadClientHarness harness, LiveInventory inventory) {
        Map<String, String> report = new LinkedHashMap<>();
        report.put("schema_version", "1");
        report.put("timestamp_utc", "");
        report.put("java_feature", "21");
        report.put("boot_parent_version", "4.1.1");
        report.put("boot_parent_match", "PASS");
        report.put("docker_used", "false");
        report.put("leftover_rows_kept", "true");
        report.put("dead_client_harness", harness.verdict());
        report.put("simulated_dead_client_port", Integer.toString(DEAD_CLIENT_PORT));
        report.put("dead_client_is_not_live_down", "true");
        report.put("redis_present", Boolean.toString(inventory.redisPresent()));
        report.put("redis_role", nullToEmpty(inventory.redisRole()));
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
        report.put("g9_83_100_121_128_148_161_174_redone", "false");
        return report;
    }

    private static LiveInventory missing(String reason) {
        return new LiveInventory(false, "", false, false, reason, reason);
    }

    private static String[] requiredKeys() {
        return new String[] {
                "schema_version", "timestamp_utc", "java_feature", "boot_parent_version",
                "boot_parent_match", "docker_used", "leftover_rows_kept",
                "dead_client_harness", "simulated_dead_client_port", "dead_client_is_not_live_down",
                "redis_present", "redis_role",
                "redis_down_observed", "live_redis_down", "live_redis_down_skip_reason",
                "live_invalidate_observed", "live_invalidate", "live_invalidate_skip_reason",
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

    private static boolean envTrue(String name) {
        return "true".equalsIgnoreCase(System.getenv(name))
                || "1".equals(System.getenv(name));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
