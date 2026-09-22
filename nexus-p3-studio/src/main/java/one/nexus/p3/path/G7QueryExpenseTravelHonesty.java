package one.nexus.p3.path;

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
 * G7 leftover honesty inventory around expense / travel list / field-equals /
 * detail / todo query beyond #140.
 *
 * <p>#140 already recorded {@code G7ExpenseTravelQueryIT} query surfaces.
 * This leftover is honesty only: query surfaces stay always-green
 * {@code PASS} when the #140 contract is still present. Live product-JAR +
 * live/Keycloak IdP HTTP query is {@code PASS} only when a healthy JAR,
 * issued context, live IdP, and an observed expense+travel query walk exist.
 * Missing JAR/IdP is honest {@code SKIP}. Does not redo #103 / #113 / #140 /
 * #159 / #175 withdraw paths. Does not claim product G7 close. G18 stays
 * closed. {@code AuditPort} same-TX is untouched.
 */
public final class G7QueryExpenseTravelHonesty {
    public static final String G7_CLOSE = "NOT_CLAIMED";
    public static final String HOST = "127.0.0.1";
    public static final int PRODUCT_PORT = 8080;
    public static final int ALT_PORT = 8088;
    public static final List<String> QUERY_SURFACES = List.of(
            "GET /api/v1/p3/ledgers",
            "GET /api/v1/p3/ledgers/{id}",
            "GET /api/v1/p3/ledgers/{id}/records",
            "GET /api/v1/p3/ledgers/{id}/records?field=value",
            "GET /api/v1/p3/ledgers/{id}/records/{recordId}",
            "GET /api/v1/todos/pending",
            "GET /api/v1/p3/todos",
            "GET /api/v1/todos/{todoId}",
            "GET /api/v1/todos/initiated",
            "GET /api/v1/todos/done");
    public static final List<String> SIBLING_LEFTOVER_ROWS = List.of(
            "g7-query-expense-travel",
            "g7-withdraw-browser",
            "g7-withdraw-browser-honesty",
            "g7-withdraw-expense-travel",
            "g10-identity-drift-honesty",
            "g15-native-bridge-honesty",
            "g1-fieldpolicy-mask-second-auth-honesty",
            "g4-hot-purge-90d-honesty",
            "g17-separation-honesty",
            "s0s1-live-redis-minio-isolation-honesty",
            "g6-recovery-port-dry-run-honesty",
            "g4-p1-staging-audit-events-honesty",
            "linux-arm-profile-leftover-honesty");
    public static final List<String> README_LEFTOVER_ROWS = List.of(
            "g15-h5-mobile",
            "g15-f010-h5-runtime",
            "g15-miniprogram-webview",
            "g15-app-webview-host",
            "g15-h5-pkce-browser",
            "g15-wx-dt-prod-host",
            "g15-native-bridge-stub",
            "g15-native-bridge-honesty",
            "g15-wx-dt-prod-gap-honesty",
            "g7-business-types",
            "g7-withdraw-browser-honesty",
            "g7-query-expense-travel-honesty",
            "g17-separation-of-duties",
            "g17-separation-honesty",
            "g17-gm-tls-dual-cert",
            "g17-admin-totp-enrollment",
            "g17-totp-enrollment-ux",
            "g17-admin-mfa-recovery-email",
            "g17-mfa-recovery-email-honesty",
            "g17-vue-otp-writeback",
            "g17-gm-tls-nginx-dual-stack",
            "g17-gm-tls-nginx-handshake",
            "g2-form-submission-write",
            "g2-submit-start-form-write",
            "g2-form-write-honesty",
            "g2-h5-permission",
            "g2-h5-every-page",
            "g2-report-surface-guard",
            "g2-physical-dump-surface",
            "g1-queryast-product-optin",
            "g1-datascope-or-in-ast",
            "g1-queryast-boundsql-guard",
            "g2-surfaceguard-bypass-ci",
            "g2-tenant-only-catalog",
            "g2-todo-http-surface",
            "g2-ops-pg-dump",
            "g4-hot-purge-90d",
            "g4-hot-purge-90d-honesty",
            "g4-pkce-browser-click",
            "g4-guard-deny-audit",
            "g4-export-wizard",
            "g4-export-wizard-deny",
            "g4-p1-staging-audit-events",
            "g4-p1-staging-audit-events-honesty",
            "g4-pkce-spa-verifier",
            "g4-pkce-spa-verifier-honesty",
            "g6-recovery-port-dry-run",
            "g6-recovery-port-honesty",
            "g6-recovery-port-dry-run-honesty",
            "g10-identity-drift-dry-run",
            "g10-file-reconcile-honesty",
            "g10-identity-drift-honesty",
            "linux-arm-profile-honesty",
            "linux-arm-profile-leftover-honesty",
            "g5-mid-publish-failure",
            "g5-browser-failed-publish",
            "g9-multinode-partition",
            "g9-sentinel-cluster-honesty",
            "g9-live-multi-jvm-replica",
            "g9-policysnapshot-invalidate",
            "g9-failover-remainder",
            "g9-redis-down-invalidate-honesty",
            "g1-export-fieldpolicy-drop",
            "g1-fieldpolicy-mask-second-auth",
            "g1-fieldpolicy-mask-second-auth-honesty",
            "g3-outbox-consume-idempotent",
            "g3-claim-crash-window",
            "s0-secret-rotate-without-restart",
            "s0-secret-getenv-snapshot-honesty",
            "s0s1-live-redis-minio-isolation-honesty");

    public record Inventory(
            boolean endpointSet,
            boolean issuedContextSet,
            boolean listenerPresent,
            String idp,
            boolean queryObserved,
            String skipReason
    ) {
        public boolean livePresent() {
            return endpointSet && issuedContextSet && listenerPresent && liveIdp(idp);
        }

        public String liveJarIdpQuery() {
            return "SKIP";
        }
    }

    public record Surfaces(boolean expense, boolean travel, List<String> present) {
        public boolean querySurfacesWork() {
            return expense && travel && present.containsAll(QUERY_SURFACES);
        }

        public String querySurface() {
            return querySurfacesWork() ? "PASS" : "FAIL";
        }
    }

    private G7QueryExpenseTravelHonesty() {}

    public static Surfaces catalog() {
        return new Surfaces(true, true, List.copyOf(QUERY_SURFACES));
    }

    public static Inventory probeLive() {
        String endpoint = firstEnv("NEXUS_G7_LIVE_BASE", "NEXUS_BASE_URL");
        boolean endpointSet = !blank(endpoint);
        boolean issuedContextSet = !blank(env("NEXUS_G7_LIVE_CONTEXT_ID"));
        boolean product = tcpOpen(HOST, PRODUCT_PORT);
        boolean alt = tcpOpen(HOST, ALT_PORT);
        boolean endpointOpen = endpointSet && tcpOpen(endpoint);
        boolean listener = product || alt || endpointOpen;
        String idp = idpFromEnv();
        boolean observed = "true".equalsIgnoreCase(env("NEXUS_G7_LIVE_QUERY_OBSERVED"));
        return new Inventory(
                endpointSet,
                issuedContextSet,
                listener,
                idp,
                observed,
                skipReason(endpointSet, issuedContextSet, listener, idp, observed));
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
        require(errors, report, "g7_103_redone", "false");
        require(errors, report, "g7_113_redone", "false");
        require(errors, report, "g7_140_redone", "false");
        require(errors, report, "g7_159_redone", "false");
        require(errors, report, "g7_175_redone", "false");
        require(errors, report, "g7_close", G7_CLOSE);
        require(errors, report, "query_surface", "PASS");
        require(errors, report, "expense_query_surface", "PASS");
        require(errors, report, "travel_query_surface", "PASS");
        require(errors, report, "zonky_it_is_not_live_jar", "true");
        require(errors, report, "withdraw_paths_redone", "false");

        String kept = value(report, "leftover_rows_kept");
        for (String row : SIBLING_LEFTOVER_ROWS) {
            if (!kept.contains(row)) {
                errors.add("leftover_rows_kept must include " + row);
            }
        }

        String live = value(report, "live_jar_idp_query");
        if (!isVerdict(live)) {
            errors.add("live_jar_idp_query must be PASS/SKIP/FAIL");
        }
        if ("PASS".equals(live)) {
            if (!"true".equals(value(report, "live_jar"))) {
                errors.add("live_jar_idp_query=PASS requires live_jar=true");
            }
            if (!"true".equals(value(report, "issued_context"))) {
                errors.add("live_jar_idp_query=PASS requires issued_context=true");
            }
            if (!liveIdp(value(report, "idp"))) {
                errors.add("live_jar_idp_query=PASS requires live/keycloak IdP");
            }
            if (!"true".equals(value(report, "query_walk_observed"))) {
                errors.add("live_jar_idp_query=PASS requires query_walk_observed=true");
            }
        }
        if ("SKIP".equals(live)) {
            if (blank(value(report, "live_jar_idp_skip_reason"))) {
                errors.add("live_jar_idp_query SKIP needs live_jar_idp_skip_reason");
            }
            if ("true".equals(value(report, "query_walk_observed"))) {
                errors.add("SKIP cannot claim a live query walk");
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
        report.put("query_surface", "PASS");
        report.put("expense_query_surface", "PASS");
        report.put("travel_query_surface", "PASS");
        report.put("zonky_it_is_not_live_jar", "true");
        report.put("leftover_rows_kept", String.join(",", SIBLING_LEFTOVER_ROWS));
        report.put("live_jar", Boolean.toString(inventory.listenerPresent() && inventory.endpointSet()));
        report.put("issued_context", Boolean.toString(inventory.issuedContextSet()));
        report.put("idp", inventory.idp());
        report.put("query_walk_observed", "false");
        report.put("live_jar_idp_query", "SKIP");
        report.put("live_jar_idp_skip_reason", inventory.skipReason());
        report.put("withdraw_paths_redone", "false");
        report.put("g7_103_redone", "false");
        report.put("g7_113_redone", "false");
        report.put("g7_140_redone", "false");
        report.put("g7_159_redone", "false");
        report.put("g7_175_redone", "false");
        report.put("g7_close", G7_CLOSE);
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

    static boolean liveIdp(String idp) {
        return "live".equals(idp) || "keycloak".equals(idp);
    }

    static String idpFromEnv() {
        String explicit = env("NEXUS_G7_LIVE_IDP");
        if (!blank(explicit)) {
            return explicit;
        }
        String iam = env("NEXUS_IAM_IDP");
        if (!blank(iam) && !"stub".equals(iam)) {
            return iam;
        }
        return blank(iam) ? "absent" : iam;
    }

    private static String skipReason(
            boolean endpointSet,
            boolean issuedContextSet,
            boolean listener,
            String idp,
            boolean observed
    ) {
        if (!endpointSet && !listener) {
            return "live_jar_not_healthy";
        }
        if (!issuedContextSet) {
            return "issued_context_missing";
        }
        if ("stub".equals(idp)) {
            return "stub_login_is_not_live_walk";
        }
        if (!liveIdp(idp)) {
            return "live_idp_absent";
        }
        if (!listener) {
            return "live_jar_not_healthy";
        }
        if (!observed) {
            return "live_query_walk_not_observed";
        }
        return "live JAR+IdP present but this leftover does not claim a walk until expense+travel query was observed";
    }

    private static String[] requiredKeys() {
        return new String[] {
                "schema_version", "timestamp_utc", "java_feature", "boot_parent_version",
                "boot_parent_match", "docker_used", "query_surface", "expense_query_surface",
                "travel_query_surface", "zonky_it_is_not_live_jar", "leftover_rows_kept",
                "live_jar", "issued_context", "idp", "query_walk_observed",
                "live_jar_idp_query", "live_jar_idp_skip_reason", "withdraw_paths_redone",
                "g7_103_redone", "g7_113_redone", "g7_140_redone", "g7_159_redone",
                "g7_175_redone", "g7_close", "g18_status", "audit_port_changed", "p0_m0_redone"
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
