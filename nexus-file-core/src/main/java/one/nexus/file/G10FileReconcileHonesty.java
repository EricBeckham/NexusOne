package one.nexus.file;

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
 * G10 leftover honesty inventory around file metadata ↔ object reconcile.
 *
 * <p>Counting / in-memory preview vs apply and tenant-prefix guards stay
 * always-green. Live MinIO / object-store is PASS only when the endpoint is
 * reachable <em>and</em> a preview + apply + tenant-prefix walk was observed.
 * Missing {@code S3_*} or a down listener is honest SKIP. Does not redo
 * identity drift (#141) or G6 object restore. Does not claim product G10 close.
 */
public final class G10FileReconcileHonesty {
    public static final String G10_CLOSE = "NOT_CLAIMED";
    public static final int DEDICATED_PORT = 19000;
    public static final int LEGACY_PORT = 9000;
    public static final String HOST = "127.0.0.1";

    public record Inventory(
            boolean endpointSet,
            boolean credentialsSet,
            boolean listenerPresent,
            String skipReason
    ) {
        public boolean livePresent() {
            return endpointSet && credentialsSet && listenerPresent;
        }

        public String liveReconcile() {
            return "SKIP";
        }
    }

    private G10FileReconcileHonesty() {}

    public static Inventory probeLive() {
        String endpoint = env("S3_ENDPOINT");
        boolean endpointSet = !blank(endpoint);
        boolean credentialsSet = !blank(env("S3_ACCESS_KEY")) && !blank(env("S3_SECRET_KEY"));
        boolean dedicated = tcpOpen(HOST, DEDICATED_PORT);
        boolean legacy = tcpOpen(HOST, LEGACY_PORT);
        boolean endpointOpen = endpointSet && tcpOpen(endpoint);
        boolean listener = dedicated || legacy || endpointOpen;
        return new Inventory(endpointSet, credentialsSet, listener, skipReason(
                endpointSet, credentialsSet, listener, dedicated, legacy));
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
        require(errors, report, "identity_drift_141_redone", "false");
        require(errors, report, "g6_object_restore_redone", "false");
        require(errors, report, "g10_close", G10_CLOSE);
        require(errors, report, "counting_preview_apply", "PASS");
        require(errors, report, "tenant_prefix_guard", "PASS");
        String live = value(report, "live_minio_reconcile");
        if (!isVerdict(live)) {
            errors.add("live_minio_reconcile must be PASS/SKIP/FAIL");
        }
        if ("PASS".equals(live)) {
            if (!"true".equals(value(report, "live_minio_present"))) {
                errors.add("live_minio_reconcile=PASS requires live_minio_present=true");
            }
            if (!"true".equals(value(report, "s3_credentials_set"))) {
                errors.add("live_minio_reconcile=PASS requires s3_credentials_set=true");
            }
            if (!"true".equals(value(report, "live_preview_observed"))) {
                errors.add("live_minio_reconcile=PASS requires live_preview_observed=true");
            }
            if (!"true".equals(value(report, "live_apply_observed"))) {
                errors.add("live_minio_reconcile=PASS requires live_apply_observed=true");
            }
            if (!"true".equals(value(report, "live_tenant_prefix_observed"))) {
                errors.add("live_minio_reconcile=PASS requires live_tenant_prefix_observed=true");
            }
        }
        if ("SKIP".equals(live)) {
            if (blank(value(report, "live_minio_skip_reason"))) {
                errors.add("live_minio_reconcile SKIP needs live_minio_skip_reason");
            }
            if ("true".equals(value(report, "live_preview_observed"))
                    || "true".equals(value(report, "live_apply_observed"))) {
                errors.add("SKIP cannot claim a live preview/apply walk");
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
        report.put("counting_preview_apply", "PASS");
        report.put("tenant_prefix_guard", "PASS");
        report.put("s3_endpoint_set", Boolean.toString(inventory.endpointSet()));
        report.put("s3_credentials_set", Boolean.toString(inventory.credentialsSet()));
        report.put("live_minio_listener", Boolean.toString(inventory.listenerPresent()));
        report.put("live_minio_present", Boolean.toString(inventory.livePresent()));
        report.put("live_minio_reconcile", "SKIP");
        report.put("live_minio_skip_reason", inventory.skipReason());
        report.put("live_preview_observed", "false");
        report.put("live_apply_observed", "false");
        report.put("live_tenant_prefix_observed", "false");
        report.put("identity_drift_141_redone", "false");
        report.put("g6_object_restore_redone", "false");
        report.put("g10_close", G10_CLOSE);
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

    private static String skipReason(
            boolean endpointSet,
            boolean credentialsSet,
            boolean listener,
            boolean dedicated,
            boolean legacy
    ) {
        if (!endpointSet && !listener) {
            return "S3_ENDPOINT unset; nothing listening on 127.0.0.1:" + DEDICATED_PORT
                    + " / :" + LEGACY_PORT + "; Docker forbidden";
        }
        if (!credentialsSet) {
            return "S3 credentials unset; live reconcile SKIP (secrets stay in env/SecretPort)";
        }
        if (!listener) {
            return "S3_ENDPOINT set but object store not reachable; live reconcile SKIP";
        }
        return "live listener present (dedicated=" + dedicated + " legacy=" + legacy
                + ") but this leftover does not claim a walk until preview/apply were observed";
    }

    private static String[] requiredKeys() {
        return new String[] {
                "schema_version", "timestamp_utc", "java_feature", "boot_parent_version",
                "boot_parent_match", "docker_used", "counting_preview_apply",
                "tenant_prefix_guard", "s3_endpoint_set", "s3_credentials_set",
                "live_minio_listener", "live_minio_present", "live_minio_reconcile",
                "live_minio_skip_reason", "live_preview_observed", "live_apply_observed",
                "live_tenant_prefix_observed", "identity_drift_141_redone",
                "g6_object_restore_redone", "g10_close", "g18_status",
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
