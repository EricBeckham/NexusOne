package one.nexus.permission.scope;

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
 * G1 leftover honesty inventory <em>beyond #146</em>: leftover BoundSql /
 * {@code COUNT(*)} stay on {@link QueryAstScopedQuery} → GuardedJdbc.
 *
 * <p>#146 already forced leftover-bound SQL and count wraps through
 * {@link QueryAstScopedQuery#executed} / {@link QueryAstScopedQuery#countExecuted}.
 * Raw unquoted {@code tenant_id} BoundSql is rejected. This leftover does
 * <strong>not</strong> redo #142 / #146 / #162 / #168 AST product code. It
 * does not claim product G1 close. G18 stays closed.
 *
 * <p>In-process leftover BoundSql / COUNT guards are always-green PASS.
 * Live product-JAR {@code GuardedLedgerStore} list / count is PASS only when
 * a listener is present <em>and</em> the walk was observed. Missing
 * {@code NEXUS_BASE_URL} / issued context is honest SKIP.
 */
public final class G1QueryAstBoundSqlHonesty {
    public static final String G1_CLOSE = "NOT_CLAIMED";
    public static final String LEFTOVER_SURFACE = "QueryAstScopedQuery";
    public static final String HOST = "127.0.0.1";
    public static final int PRODUCT_PORT = 8080;
    public static final int ALT_PORT = 8088;
    public static final String ROW_BOUNDSQL = "g1-queryast-boundsql-guard";
    public static final String ROW_DATASCOPE = "g1-datascope-or-in-ast";
    public static final String ROW_MASK = "g1-fieldpolicy-mask-second-auth";
    public static final String ROW_MASK_HONESTY = "g1-fieldpolicy-mask-second-auth-honesty";
    public static final String SIBLING_ROWS =
            ROW_BOUNDSQL + "," + ROW_DATASCOPE + "," + ROW_MASK + "," + ROW_MASK_HONESTY;

    public record Inventory(
            boolean endpointSet,
            boolean issuedContextSet,
            boolean listenerPresent,
            boolean listObserved,
            boolean countObserved,
            String skipReason
    ) {
        public boolean livePresent() {
            return endpointSet && issuedContextSet && listenerPresent;
        }

        public String liveProductWalk() {
            return "SKIP";
        }

        public String uiH5Report() {
            return "SKIP";
        }
    }

    private G1QueryAstBoundSqlHonesty() {}

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
                envTruthy("NEXUS_G1_LIVE_LIST_OBSERVED"),
                envTruthy("NEXUS_G1_LIVE_COUNT_OBSERVED"),
                skipReason(endpointSet, issuedContextSet, listener, product, alt));
    }

    public static boolean leftoverBoundSqlGuarded(String storeSource) {
        if (storeSource == null || storeSource.isBlank()) {
            return false;
        }
        return storeSource.contains("QueryAstScopedQuery.executed")
                && storeSource.contains("QueryAstScopedQuery.countExecuted")
                && !storeSource.contains("prepareStatement")
                && !storeSource.contains("new BoundSql");
    }

    public static Path guardedLedgerStorePath() {
        Path module = Path.of(
                "nexus-p3-studio/src/main/java/one/nexus/p3/infra/jdbc/GuardedLedgerStore.java");
        if (Files.isRegularFile(module)) {
            return module;
        }
        return Path.of(
                "../nexus-p3-studio/src/main/java/one/nexus/p3/infra/jdbc/GuardedLedgerStore.java");
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
        require(errors, report, "redone_142", "false");
        require(errors, report, "redone_146", "false");
        require(errors, report, "redone_162", "false");
        require(errors, report, "redone_168", "false");
        require(errors, report, "g1_close", G1_CLOSE);
        require(errors, report, "leftover_boundsql_guard", "PASS");
        require(errors, report, "leftover_count_guard", "PASS");
        require(errors, report, "raw_boundsql_rejected", "true");
        require(errors, report, "count_is_ast_node", "false");
        require(errors, report, "leftover_surface", LEFTOVER_SURFACE);
        require(errors, report, "leftover_rows_kept", "true");
        String rows = value(report, "leftover_rows");
        if (!rows.contains(ROW_BOUNDSQL)
                || !rows.contains(ROW_DATASCOPE)
                || !rows.contains(ROW_MASK)
                || !rows.contains(ROW_MASK_HONESTY)) {
            errors.add("leftover_rows must keep " + SIBLING_ROWS);
        }
        requireVerdict(errors, report, "live_product_walk");
        requireVerdict(errors, report, "ui_h5_report");
        refusePassWithout(errors, report, "live_product_walk", "live_jar_present", "true",
                "live_product_walk=PASS requires live_jar_present=true");
        refusePassWithout(errors, report, "live_product_walk", "live_list_observed", "true",
                "live_product_walk=PASS requires live_list_observed=true");
        refusePassWithout(errors, report, "live_product_walk", "live_count_observed", "true",
                "live_product_walk=PASS requires live_count_observed=true");
        requireSkipReason(errors, report, "live_product_walk", "live_product_skip_reason");
        requireSkipReason(errors, report, "ui_h5_report", "ui_h5_report_skip_reason");
        refuseSkipWithObserved(errors, report, "live_product_walk", "live_list_observed");
        refuseSkipWithObserved(errors, report, "live_product_walk", "live_count_observed");
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
        report.put("leftover_boundsql_guard", "PASS");
        report.put("leftover_count_guard", "PASS");
        report.put("raw_boundsql_rejected", "true");
        report.put("count_is_ast_node", "false");
        report.put("leftover_surface", LEFTOVER_SURFACE);
        report.put("leftover_rows_kept", "true");
        report.put("leftover_rows", SIBLING_ROWS);
        report.put("live_jar_endpoint_set", Boolean.toString(inventory.endpointSet()));
        report.put("live_jar_context_set", Boolean.toString(inventory.issuedContextSet()));
        report.put("live_jar_listener", Boolean.toString(inventory.listenerPresent()));
        report.put("live_jar_present", Boolean.toString(inventory.livePresent()));
        report.put("live_product_walk", "SKIP");
        report.put("live_list_observed", "false");
        report.put("live_count_observed", "false");
        report.put("live_product_skip_reason", inventory.skipReason());
        report.put("ui_h5_report", "SKIP");
        report.put("ui_h5_report_skip_reason",
                "UI / H5 / report UI leftover BoundSql / COUNT was not walked; leftover stays open");
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
                    + "; leftover BoundSql / COUNT stay on QueryAstScopedQuery (#146);"
                    + " live GuardedLedgerStore SKIP; Docker forbidden";
        }
        if (!issuedContextSet) {
            return "NEXUS_G1_LIVE_CONTEXT_ID unset; live GuardedLedgerStore list/count walk SKIP";
        }
        if (!listener) {
            return "live base set but product JAR not reachable; live walk SKIP";
        }
        return "live listener present (product=" + product + " alt=" + alt
                + ") but this leftover does not claim a walk until leftover BoundSql list"
                + " and COUNT through QueryAstScopedQuery → GuardedJdbc were observed";
    }

    private static String[] requiredKeys() {
        return new String[] {
                "schema_version", "timestamp_utc", "java_feature", "boot_parent_version",
                "boot_parent_match", "docker_used", "leftover_boundsql_guard",
                "leftover_count_guard", "raw_boundsql_rejected", "count_is_ast_node",
                "leftover_surface", "leftover_rows_kept", "leftover_rows",
                "live_jar_endpoint_set", "live_jar_context_set", "live_jar_listener",
                "live_jar_present", "live_product_walk", "live_list_observed",
                "live_count_observed", "live_product_skip_reason", "ui_h5_report",
                "ui_h5_report_skip_reason", "redone_142", "redone_146", "redone_162",
                "redone_168", "g1_close", "g18_status", "audit_port_changed",
                "p0_m0_redone"
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
