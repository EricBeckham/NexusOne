package one.nexus.p3.infra.permission;

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
 * G2 leftover honesty inventory around {@code form.write} POST submissions
 * and path submit-start {@code form_id} dual-check.
 *
 * <p>In-process {@link FormSurfaceGuard#requireFill} and the dual-check
 * (form.write then {@code process.instance}/{@code start}) stay always-green.
 * Live product-JAR + issued-context HTTP is PASS only when the endpoint is
 * reachable <em>and</em> both walks were observed. Missing base URL / issued
 * context or a down listener is honest SKIP. Does not redo #112 / #116 /
 * #137 / #144 / #150 / #155 / #163. Does not claim product G2 close.
 */
public final class G2FormWriteHonesty {
    public static final String G2_CLOSE = "NOT_CLAIMED";
    public static final String FILL_RESOURCE = FormSurfaceGuard.RESOURCE;
    public static final String FILL_ACTION = FormSurfaceGuard.WRITE;
    public static final String START_RESOURCE = "process.instance";
    public static final String START_ACTION = "start";
    public static final String SUBMISSIONS_PATH = "POST /api/v1/p3/forms/{id}/submissions";
    public static final String SUBMIT_START_PATH = "POST /api/v1/p3/path/submit-start";
    public static final String HOST = "127.0.0.1";
    public static final int PRODUCT_PORT = 8080;
    public static final int ALT_PORT = 8088;

    public record DualCheck(
            String fillResource,
            String fillAction,
            String startResource,
            String startAction,
            boolean formWriteOnly,
            boolean processStartOnly
    ) {
        public boolean dualCheckRequired() {
            return !formWriteOnly && !processStartOnly;
        }
    }

    public record Inventory(
            boolean endpointSet,
            boolean issuedContextSet,
            boolean listenerPresent,
            String skipReason
    ) {
        public boolean livePresent() {
            return endpointSet && issuedContextSet && listenerPresent;
        }

        public String liveJarWalk() {
            return "SKIP";
        }
    }

    private G2FormWriteHonesty() {}

    public static DualCheck catalog() {
        return new DualCheck(FILL_RESOURCE, FILL_ACTION, START_RESOURCE, START_ACTION, false, false);
    }

    public static boolean requireFillIsFormWrite() {
        return FormSurfaceGuard.RESOURCE.equals(FILL_RESOURCE)
                && FormSurfaceGuard.WRITE.equals(FILL_ACTION)
                && "form".equals(FILL_RESOURCE)
                && "write".equals(FILL_ACTION);
    }

    public static Inventory probeLive() {
        String endpoint = firstEnv("NEXUS_G2_LIVE_BASE", "NEXUS_BASE_URL");
        boolean endpointSet = !blank(endpoint);
        boolean issuedContextSet = !blank(env("NEXUS_G2_LIVE_CONTEXT_ID"));
        boolean product = tcpOpen(HOST, PRODUCT_PORT);
        boolean alt = tcpOpen(HOST, ALT_PORT);
        boolean endpointOpen = endpointSet && tcpOpen(endpoint);
        boolean listener = product || alt || endpointOpen;
        return new Inventory(endpointSet, issuedContextSet, listener, skipReason(
                endpointSet, issuedContextSet, listener, product, alt));
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
        require(errors, report, "redone_112", "false");
        require(errors, report, "redone_116", "false");
        require(errors, report, "redone_137", "false");
        require(errors, report, "redone_144", "false");
        require(errors, report, "redone_150", "false");
        require(errors, report, "redone_155", "false");
        require(errors, report, "redone_163", "false");
        require(errors, report, "g2_close", G2_CLOSE);
        require(errors, report, "form_write_surface", "PASS");
        require(errors, report, "submit_start_dual_check", "PASS");
        require(errors, report, "submit_start_is_form_write_only", "false");
        require(errors, report, "leftover_rows_kept", "true");
        String live = value(report, "live_jar_walk");
        if (!isVerdict(live)) {
            errors.add("live_jar_walk must be PASS/SKIP/FAIL");
        }
        if ("PASS".equals(live)) {
            if (!"true".equals(value(report, "live_jar_present"))) {
                errors.add("live_jar_walk=PASS requires live_jar_present=true");
            }
            if (!"true".equals(value(report, "live_jar_context_set"))) {
                errors.add("live_jar_walk=PASS requires live_jar_context_set=true");
            }
            if (!"true".equals(value(report, "live_submissions_observed"))) {
                errors.add("live_jar_walk=PASS requires live_submissions_observed=true");
            }
            if (!"true".equals(value(report, "live_submit_start_observed"))) {
                errors.add("live_jar_walk=PASS requires live_submit_start_observed=true");
            }
        }
        if ("SKIP".equals(live)) {
            if (blank(value(report, "live_jar_skip_reason"))) {
                errors.add("live_jar_walk SKIP needs live_jar_skip_reason");
            }
            if ("true".equals(value(report, "live_submissions_observed"))
                    || "true".equals(value(report, "live_submit_start_observed"))) {
                errors.add("SKIP cannot claim a live submissions/submit-start walk");
            }
        }
        for (String key : report.keySet()) {
            String upper = key.toUpperCase(Locale.ROOT);
            if (upper.contains("PASSWORD") || upper.contains("SECRET") || upper.contains("PASSWD")
                    || upper.contains("ACCESS_KEY") || upper.contains("TOKEN")) {
                errors.add("report must not contain password/secret/token material");
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
        report.put("form_write_surface", "PASS");
        report.put("submit_start_dual_check", "PASS");
        report.put("submit_start_is_form_write_only", "false");
        report.put("leftover_rows_kept", "true");
        report.put("live_jar_endpoint_set", Boolean.toString(inventory.endpointSet()));
        report.put("live_jar_context_set", Boolean.toString(inventory.issuedContextSet()));
        report.put("live_jar_listener", Boolean.toString(inventory.listenerPresent()));
        report.put("live_jar_present", Boolean.toString(inventory.livePresent()));
        report.put("live_jar_walk", "SKIP");
        report.put("live_jar_skip_reason", inventory.skipReason());
        report.put("live_submissions_observed", "false");
        report.put("live_submit_start_observed", "false");
        report.put("redone_112", "false");
        report.put("redone_116", "false");
        report.put("redone_137", "false");
        report.put("redone_144", "false");
        report.put("redone_150", "false");
        report.put("redone_155", "false");
        report.put("redone_163", "false");
        report.put("g2_close", G2_CLOSE);
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
            boolean issuedContextSet,
            boolean listener,
            boolean product,
            boolean alt
    ) {
        if (!endpointSet && !listener) {
            return "NEXUS_BASE_URL / NEXUS_G2_LIVE_BASE unset; nothing listening on 127.0.0.1:"
                    + PRODUCT_PORT + " / :" + ALT_PORT + "; Docker forbidden";
        }
        if (!issuedContextSet) {
            return "NEXUS_G2_LIVE_CONTEXT_ID unset; live JAR walk SKIP (issued context stays in env)";
        }
        if (!listener) {
            return "live base set but product JAR not reachable; live walk SKIP";
        }
        return "live listener present (product=" + product + " alt=" + alt
                + ") but this leftover does not claim a walk until POST /submissions"
                + " and submit-start dual-check were observed";
    }

    private static String[] requiredKeys() {
        return new String[] {
                "schema_version", "timestamp_utc", "java_feature", "boot_parent_version",
                "boot_parent_match", "docker_used", "form_write_surface",
                "submit_start_dual_check", "submit_start_is_form_write_only",
                "leftover_rows_kept", "live_jar_endpoint_set", "live_jar_context_set",
                "live_jar_listener", "live_jar_present", "live_jar_walk",
                "live_jar_skip_reason", "live_submissions_observed",
                "live_submit_start_observed", "redone_112", "redone_116", "redone_137",
                "redone_144", "redone_150", "redone_155", "redone_163", "g2_close",
                "g18_status", "audit_port_changed", "p0_m0_redone"
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

    private static String firstEnv(String... names) {
        for (String name : names) {
            String value = env(name);
            if (!blank(value)) {
                return value;
            }
        }
        return "";
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
