package one.nexus.permission.field;

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
 * G1 leftover honesty inventory <em>beyond #162</em>: live FieldPolicy
 * mask / second-auth walks stay PASS/SKIP.
 *
 * <p>#162 already proved mask + 二次授权 stay on
 * {@link FieldPolicyMaskHonesty#LEFTOVER_SURFACE} beside Query AST
 * ({@code leftoverMaskFields}). IsolatedExport still writes raw cells.
 * This leftover does <strong>not</strong> redo #115 / #130 / #134 / #142 /
 * #146 / #162 / #168. It does not encode mask onto Query AST. It does not
 * claim product G1 close. G18 stays closed.
 *
 * <p>Live product-JAR list/detail mask, IsolatedExport mask apply, and a
 * live second-auth grant are PASS only when a listener is present
 * <em>and</em> the walk was observed. Missing {@code NEXUS_BASE_URL} /
 * issued context is honest SKIP.
 */
public final class FieldPolicyMaskLeftoverHonesty {
    public static final String G1_CLOSE = FieldPolicyMaskHonesty.G1_CLOSE;
    public static final String LEFTOVER_SURFACE = FieldPolicyMaskHonesty.LEFTOVER_SURFACE;
    public static final String HOST = "127.0.0.1";
    public static final int PRODUCT_PORT = 8080;
    public static final int ALT_PORT = 8088;
    public static final String ROW_DATASCOPE = "g1-datascope-or-in-ast";
    public static final String ROW_MASK = "g1-fieldpolicy-mask-second-auth";
    public static final String BOTH_ROWS = ROW_DATASCOPE + "," + ROW_MASK;

    public record Inventory(
            boolean endpointSet,
            boolean issuedContextSet,
            boolean listenerPresent,
            boolean apiMaskObserved,
            boolean exportMaskObserved,
            boolean secondAuthObserved,
            String skipReason
    ) {
        public boolean livePresent() {
            return endpointSet && issuedContextSet && listenerPresent;
        }

        public String liveApiMask() {
            return "SKIP";
        }

        public String liveExportMask() {
            return "SKIP";
        }

        public String liveSecondAuth() {
            return "SKIP";
        }

        public String uiH5Report() {
            return "SKIP";
        }
    }

    private FieldPolicyMaskLeftoverHonesty() {}

    public static Inventory probeLive() {
        String endpoint = firstEnv("NEXUS_G1_LIVE_BASE", "NEXUS_BASE_URL");
        boolean endpointSet = !blank(endpoint);
        boolean issuedContextSet = !blank(env("NEXUS_G1_LIVE_CONTEXT_ID"));
        boolean product = tcpOpen(HOST, PRODUCT_PORT);
        boolean alt = tcpOpen(HOST, ALT_PORT);
        boolean endpointOpen = endpointSet && tcpOpen(endpoint);
        boolean listener = product || alt || endpointOpen;
        return new Inventory(
                endpointSet,
                issuedContextSet,
                listener,
                envTruthy("NEXUS_G1_LIVE_API_MASK_OBSERVED"),
                envTruthy("NEXUS_G1_LIVE_EXPORT_MASK_OBSERVED"),
                envTruthy("NEXUS_G1_LIVE_SECOND_AUTH_OBSERVED"),
                skipReason(endpointSet, issuedContextSet, listener, product, alt));
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
        require(errors, report, "redone_115", "false");
        require(errors, report, "redone_130", "false");
        require(errors, report, "redone_134", "false");
        require(errors, report, "redone_142", "false");
        require(errors, report, "redone_146", "false");
        require(errors, report, "redone_162", "false");
        require(errors, report, "redone_168", "false");
        require(errors, report, "g1_close", G1_CLOSE);
        require(errors, report, "leftover_beside_ast", "PASS");
        require(errors, report, "mask_on_ast", "false");
        require(errors, report, "second_auth_on_ast", "false");
        require(errors, report, "isolated_export_applies_mask", "false");
        require(errors, report, "leftover_surface", LEFTOVER_SURFACE);
        require(errors, report, "leftover_rows_kept", "true");
        String rows = value(report, "leftover_rows");
        if (!rows.contains(ROW_DATASCOPE) || !rows.contains(ROW_MASK)) {
            errors.add("leftover_rows must keep " + ROW_DATASCOPE + " and " + ROW_MASK);
        }
        requireVerdict(errors, report, "live_api_mask");
        requireVerdict(errors, report, "live_export_mask");
        requireVerdict(errors, report, "live_second_auth");
        requireVerdict(errors, report, "ui_h5_report");
        refusePassWithout(errors, report, "live_api_mask", "live_jar_present", "true",
                "live_api_mask=PASS requires live_jar_present=true");
        refusePassWithout(errors, report, "live_api_mask", "live_api_mask_observed", "true",
                "live_api_mask=PASS requires live_api_mask_observed=true");
        refusePassWithout(errors, report, "live_export_mask", "live_jar_present", "true",
                "live_export_mask=PASS requires live_jar_present=true");
        refusePassWithout(errors, report, "live_export_mask", "live_export_mask_observed", "true",
                "live_export_mask=PASS requires live_export_mask_observed=true");
        refusePassWithout(errors, report, "live_second_auth", "live_jar_present", "true",
                "live_second_auth=PASS requires live_jar_present=true");
        refusePassWithout(errors, report, "live_second_auth", "live_second_auth_observed", "true",
                "live_second_auth=PASS requires live_second_auth_observed=true");
        requireSkipReason(errors, report, "live_api_mask", "live_api_mask_skip_reason");
        requireSkipReason(errors, report, "live_export_mask", "live_export_mask_skip_reason");
        requireSkipReason(errors, report, "live_second_auth", "live_second_auth_skip_reason");
        requireSkipReason(errors, report, "ui_h5_report", "ui_h5_report_skip_reason");
        refuseSkipWithObserved(errors, report, "live_api_mask", "live_api_mask_observed");
        refuseSkipWithObserved(errors, report, "live_export_mask", "live_export_mask_observed");
        refuseSkipWithObserved(errors, report, "live_second_auth", "live_second_auth_observed");
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
        report.put("leftover_beside_ast", "PASS");
        report.put("mask_on_ast", "false");
        report.put("second_auth_on_ast", "false");
        report.put("isolated_export_applies_mask", "false");
        report.put("leftover_surface", LEFTOVER_SURFACE);
        report.put("leftover_rows_kept", "true");
        report.put("leftover_rows", BOTH_ROWS);
        report.put("live_jar_endpoint_set", Boolean.toString(inventory.endpointSet()));
        report.put("live_jar_context_set", Boolean.toString(inventory.issuedContextSet()));
        report.put("live_jar_listener", Boolean.toString(inventory.listenerPresent()));
        report.put("live_jar_present", Boolean.toString(inventory.livePresent()));
        report.put("live_api_mask", "SKIP");
        report.put("live_api_mask_observed", "false");
        report.put("live_api_mask_skip_reason", inventory.skipReason());
        report.put("live_export_mask", "SKIP");
        report.put("live_export_mask_observed", "false");
        report.put("live_export_mask_skip_reason", inventory.skipReason());
        report.put("live_second_auth", "SKIP");
        report.put("live_second_auth_observed", "false");
        report.put("live_second_auth_skip_reason", inventory.skipReason());
        report.put("ui_h5_report", "SKIP");
        report.put("ui_h5_report_skip_reason",
                "UI / H5 / report UI FieldPolicy mask was not walked; leftover stays open");
        report.put("redone_115", "false");
        report.put("redone_130", "false");
        report.put("redone_134", "false");
        report.put("redone_142", "false");
        report.put("redone_146", "false");
        report.put("redone_162", "false");
        report.put("redone_168", "false");
        report.put("g1_close", G1_CLOSE);
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
            return "NEXUS_BASE_URL / NEXUS_G1_LIVE_BASE unset; nothing listening on 127.0.0.1:"
                    + PRODUCT_PORT + " / :" + ALT_PORT
                    + "; IsolatedExport still writes raw cells (#162); Docker forbidden";
        }
        if (!issuedContextSet) {
            return "NEXUS_G1_LIVE_CONTEXT_ID unset; live FieldPolicy mask / second-auth walk SKIP";
        }
        if (!listener) {
            return "live base set but product JAR not reachable; live walk SKIP";
        }
        return "live listener present (product=" + product + " alt=" + alt
                + ") but this leftover does not claim a walk until list/detail mask,"
                + " IsolatedExport mask, and second-auth grant were observed";
    }

    private static String[] requiredKeys() {
        return new String[] {
                "schema_version", "timestamp_utc", "java_feature", "boot_parent_version",
                "boot_parent_match", "docker_used", "leftover_beside_ast", "mask_on_ast",
                "second_auth_on_ast", "isolated_export_applies_mask", "leftover_surface",
                "leftover_rows_kept", "leftover_rows", "live_jar_endpoint_set",
                "live_jar_context_set", "live_jar_listener", "live_jar_present",
                "live_api_mask", "live_api_mask_observed", "live_api_mask_skip_reason",
                "live_export_mask", "live_export_mask_observed", "live_export_mask_skip_reason",
                "live_second_auth", "live_second_auth_observed", "live_second_auth_skip_reason",
                "ui_h5_report", "ui_h5_report_skip_reason", "redone_115", "redone_130",
                "redone_134", "redone_142", "redone_146", "redone_162", "redone_168",
                "g1_close", "g18_status", "audit_port_changed", "p0_m0_redone"
        };
    }

    private static void require(List<String> errors, Map<String, String> report, String key, String expected) {
        String actual = value(report, key);
        if (!expected.equals(actual)) {
            errors.add(key + " must be " + expected + ", got " + actual);
        }
    }

    private static void requireVerdict(List<String> errors, Map<String, String> report, String key) {
        String value = value(report, key);
        if (!isVerdict(value)) {
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
            String reasonKey
    ) {
        if ("SKIP".equals(value(report, verdictKey)) && blank(value(report, reasonKey))) {
            errors.add(verdictKey + " SKIP needs " + reasonKey);
        }
    }

    private static void refuseSkipWithObserved(
            List<String> errors,
            Map<String, String> report,
            String verdictKey,
            String observedKey
    ) {
        if ("SKIP".equals(value(report, verdictKey)) && "true".equals(value(report, observedKey))) {
            errors.add("SKIP cannot claim a live " + observedKey + " walk");
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

    private static boolean envTruthy(String name) {
        String value = env(name).toLowerCase(Locale.ROOT);
        return "true".equals(value) || "yes".equals(value) || "1".equals(value);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
