package one.nexus.isolation.boundary;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * S0+S1 leftover honesty inventory for <em>tenant-key</em> ({@code CacheKeyGuard}
 * {@code P1.M02.014}) and <em>bucket-prefix</em> ({@code FileObjectKeyGuard}
 * {@code P1.M02.015}) isolation under live Redis 3.2 / MinIO.
 *
 * <p>In-process fixture rejects stay always-green. Live Redis / MinIO isolation
 * is PASS only when the listener is present <em>and</em> a reject-before-backend
 * walk was observed. Missing listeners are honest SKIP. Orthogonal to G9
 * Redis-down / PolicySnapshot (#174) and G10 file-reconcile (#169). Does not
 * redo #83 / #100 / #121 / #148 / #161 / #169 / #174. Does not claim product
 * G9 / G10 close or a product live Redis/MinIO drill close. G18 stays closed.
 * No Docker. Does not start Redis or MinIO.
 */
public final class S0S1LiveRedisMinioIsolationHonesty {
    public static final String HOST = "127.0.0.1";
    public static final int DEDICATED_REDIS_PORT = 16379;
    public static final int PRODUCT_REDIS_PORT = 6379;
    public static final int DEDICATED_MINIO_PORT = 19000;
    public static final int LEGACY_MINIO_PORT = 9000;
    public static final String G9_CLOSE = "NOT_CLAIMED";
    public static final String G10_CLOSE = "NOT_CLAIMED";
    public static final String LIVE_DRILL_CLOSE = "NOT_CLAIMED";

    public record Inventory(
            boolean redisPresent,
            int redisPortProbed,
            String redisSkipReason,
            boolean s3EndpointSet,
            boolean s3CredentialsSet,
            boolean minioListener,
            String minioSkipReason
    ) {
        public boolean minioPresent() {
            return s3EndpointSet && s3CredentialsSet && minioListener;
        }

        public String liveRedisIsolation() {
            return "SKIP";
        }

        public String liveMinioIsolation() {
            return "SKIP";
        }
    }

    private S0S1LiveRedisMinioIsolationHonesty() {}

    public static Inventory probeLive() {
        boolean dedicatedRedis = tcpOpen(HOST, DEDICATED_REDIS_PORT);
        boolean productRedis = tcpOpen(HOST, PRODUCT_REDIS_PORT);
        boolean redisPresent = dedicatedRedis || productRedis;
        int redisPort = dedicatedRedis ? DEDICATED_REDIS_PORT
                : productRedis ? PRODUCT_REDIS_PORT : DEDICATED_REDIS_PORT;

        String endpoint = env("S3_ENDPOINT");
        boolean endpointSet = !blank(endpoint);
        boolean credentialsSet = !blank(env("S3_ACCESS_KEY")) && !blank(env("S3_SECRET_KEY"));
        boolean dedicatedMinio = tcpOpen(HOST, DEDICATED_MINIO_PORT);
        boolean legacyMinio = tcpOpen(HOST, LEGACY_MINIO_PORT);
        boolean endpointOpen = endpointSet && tcpOpen(endpoint);
        boolean minioListener = dedicatedMinio || legacyMinio || endpointOpen;

        return new Inventory(
                redisPresent,
                redisPort,
                redisSkipReason(redisPresent, dedicatedRedis, productRedis),
                endpointSet,
                credentialsSet,
                minioListener,
                minioSkipReason(endpointSet, credentialsSet, minioListener, dedicatedMinio, legacyMinio));
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
        require(errors, report, "p0_m0_redone", "false");
        require(errors, report, "p0_05_06_redone", "false");
        require(errors, report, "m0_redis_minio_redone", "false");
        require(errors, report, "g9_redis_down_174_redone", "false");
        require(errors, report, "g10_file_reconcile_169_redone", "false");
        require(errors, report, "g9_83_100_121_148_161_redone", "false");
        require(errors, report, "g9_close", G9_CLOSE);
        require(errors, report, "g10_close", G10_CLOSE);
        require(errors, report, "live_drill_close", LIVE_DRILL_CLOSE);
        require(errors, report, "fixture_tenant_key_guard", "PASS");
        require(errors, report, "fixture_bucket_prefix_guard", "PASS");

        String liveRedis = value(report, "live_redis_isolation");
        if (!isVerdict(liveRedis)) {
            errors.add("live_redis_isolation must be PASS/SKIP/FAIL");
        }
        if ("PASS".equals(liveRedis)) {
            if (!"true".equals(value(report, "redis_present"))) {
                errors.add("live_redis_isolation=PASS requires redis_present=true");
            }
            if (!"true".equals(value(report, "live_redis_isolation_observed"))) {
                errors.add("live_redis_isolation=PASS requires live_redis_isolation_observed=true");
            }
        }
        if ("SKIP".equals(liveRedis)) {
            if (blank(value(report, "live_redis_isolation_skip_reason"))) {
                errors.add("live_redis_isolation SKIP needs live_redis_isolation_skip_reason");
            }
            if ("true".equals(value(report, "live_redis_isolation_observed"))) {
                errors.add("SKIP cannot claim a live Redis tenant-key isolation walk");
            }
        }

        String liveMinio = value(report, "live_minio_isolation");
        if (!isVerdict(liveMinio)) {
            errors.add("live_minio_isolation must be PASS/SKIP/FAIL");
        }
        if ("PASS".equals(liveMinio)) {
            if (!"true".equals(value(report, "minio_present"))) {
                errors.add("live_minio_isolation=PASS requires minio_present=true");
            }
            if (!"true".equals(value(report, "live_minio_isolation_observed"))) {
                errors.add("live_minio_isolation=PASS requires live_minio_isolation_observed=true");
            }
        }
        if ("SKIP".equals(liveMinio)) {
            if (blank(value(report, "live_minio_isolation_skip_reason"))) {
                errors.add("live_minio_isolation SKIP needs live_minio_isolation_skip_reason");
            }
            if ("true".equals(value(report, "live_minio_isolation_observed"))) {
                errors.add("SKIP cannot claim a live MinIO bucket-prefix isolation walk");
            }
        }

        for (String key : report.keySet()) {
            String upper = key.toUpperCase(Locale.ROOT);
            if (upper.contains("PASSWORD") || upper.contains("SECRET") || upper.contains("PASSWD")
                    || upper.contains("ACCESS_KEY")) {
                errors.add("report must not contain password/secret material");
            }
        }
        return errors;
    }

    public static Map<String, String> honestSkipTemplate(Inventory inventory) {
        Map<String, String> report = new LinkedHashMap<>();
        report.put("schema_version", "1");
        report.put("timestamp_utc", "");
        report.put("java_feature", "21");
        report.put("boot_parent_version", "4.1.1");
        report.put("boot_parent_match", "PASS");
        report.put("docker_used", "false");
        report.put("fixture_tenant_key_guard", "PASS");
        report.put("fixture_bucket_prefix_guard", "PASS");
        report.put("redis_present", Boolean.toString(inventory.redisPresent()));
        report.put("redis_port_probed", Integer.toString(inventory.redisPortProbed()));
        report.put("live_redis_isolation", "SKIP");
        report.put("live_redis_isolation_skip_reason", inventory.redisSkipReason());
        report.put("live_redis_isolation_observed", "false");
        report.put("s3_endpoint_set", Boolean.toString(inventory.s3EndpointSet()));
        report.put("s3_credentials_set", Boolean.toString(inventory.s3CredentialsSet()));
        report.put("minio_listener", Boolean.toString(inventory.minioListener()));
        report.put("minio_present", Boolean.toString(inventory.minioPresent()));
        report.put("live_minio_isolation", "SKIP");
        report.put("live_minio_isolation_skip_reason", inventory.minioSkipReason());
        report.put("live_minio_isolation_observed", "false");
        report.put("g9_redis_down_174_redone", "false");
        report.put("g10_file_reconcile_169_redone", "false");
        report.put("g9_83_100_121_148_161_redone", "false");
        report.put("p0_05_06_redone", "false");
        report.put("m0_redis_minio_redone", "false");
        report.put("g9_close", G9_CLOSE);
        report.put("g10_close", G10_CLOSE);
        report.put("live_drill_close", LIVE_DRILL_CLOSE);
        report.put("g18_status", "closed_unchanged");
        report.put("audit_port_changed", "false");
        report.put("p0_m0_redone", "false");
        return report;
    }

    static boolean tcpOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 200);
            return true;
        } catch (IOException | RuntimeException ex) {
            return false;
        }
    }

    static boolean tcpOpen(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return false;
            }
            int port = uri.getPort();
            if (port <= 0) {
                port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
            }
            return tcpOpen(host, port);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static String redisSkipReason(boolean present, boolean dedicated, boolean product) {
        if (!present) {
            return "no Redis listener on " + HOST + ":" + DEDICATED_REDIS_PORT
                    + " / :" + PRODUCT_REDIS_PORT
                    + "; live tenant-key isolation walk not observed; Docker forbidden; "
                    + "this leftover does not start Redis";
        }
        return "Redis is listening on " + HOST
                + " (dedicated=" + dedicated + " product=" + product
                + ") but a live tenant-key reject-before-command walk was not observed; "
                + "presence is not a product live Redis drill close; Docker forbidden";
    }

    private static String minioSkipReason(
            boolean endpointSet,
            boolean credentialsSet,
            boolean listener,
            boolean dedicated,
            boolean legacy
    ) {
        if (!endpointSet && !listener) {
            return "S3_ENDPOINT unset; nothing listening on " + HOST + ":"
                    + DEDICATED_MINIO_PORT + " / :" + LEGACY_MINIO_PORT
                    + "; live bucket-prefix isolation walk not observed; Docker forbidden; "
                    + "this leftover does not start MinIO";
        }
        if (!credentialsSet) {
            return "S3 credentials unset; live bucket-prefix isolation SKIP (secrets stay in env/SecretPort)";
        }
        if (!listener) {
            return "S3_ENDPOINT set but object store not reachable; live bucket-prefix isolation SKIP";
        }
        return "live MinIO listener present (dedicated=" + dedicated + " legacy=" + legacy
                + ") but a live bucket-prefix reject-before-S3 walk was not observed; "
                + "presence is not a product live MinIO drill close";
    }

    private static String[] requiredKeys() {
        return new String[] {
                "schema_version", "timestamp_utc", "java_feature", "boot_parent_version",
                "boot_parent_match", "docker_used", "fixture_tenant_key_guard",
                "fixture_bucket_prefix_guard", "redis_present", "redis_port_probed",
                "live_redis_isolation", "live_redis_isolation_skip_reason",
                "live_redis_isolation_observed", "s3_endpoint_set", "s3_credentials_set",
                "minio_listener", "minio_present", "live_minio_isolation",
                "live_minio_isolation_skip_reason", "live_minio_isolation_observed",
                "g9_redis_down_174_redone", "g10_file_reconcile_169_redone",
                "g9_83_100_121_148_161_redone", "p0_05_06_redone", "m0_redis_minio_redone",
                "g9_close", "g10_close", "live_drill_close", "g18_status",
                "audit_port_changed", "p0_m0_redone"
        };
    }

    private static void require(List<String> errors, Map<String, String> report, String key, String expected) {
        String actual = value(report, key);
        if (!expected.equals(actual)) {
            errors.add(key + " must be " + expected + ", got " + actual);
        }
    }

    private static boolean isVerdict(String value) {
        return "PASS".equals(value) || "SKIP".equals(value) || "FAIL".equals(value);
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
}
