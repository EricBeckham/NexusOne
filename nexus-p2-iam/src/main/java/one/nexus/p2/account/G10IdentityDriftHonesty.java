package one.nexus.p2.account;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * G10 leftover honesty inventory around identity drift-fix apply / dry-run
 * beyond #141.
 *
 * <p>Live Keycloak 8080 dry-run is PASS only when the issuer is reachable
 * <em>and</em> a dry-run walk was observed. Missing listener / OIDC discovery
 * is honest SKIP. Does not start Keycloak. Does not redo the identity apply
 * product path or #141 / #169. Does not claim product G10 / SAML / LDAP / SMS
 * MFA. G18 stays closed.
 */
public final class G10IdentityDriftHonesty {
    public static final String G10_CLOSE = "NOT_CLAIMED";
    public static final String HOST = "127.0.0.1";
    public static final int KEYCLOAK_PORT = 8080;
    public static final String DEFAULT_ISSUER = "http://127.0.0.1:8080/realms/nexus";

    public record Inventory(
            boolean listenerPresent,
            boolean oidcDiscovery,
            String issuer,
            String skipReason
    ) {
        public boolean livePresent() {
            return listenerPresent && oidcDiscovery;
        }

        public String liveKeycloak8080() {
            return "SKIP";
        }

        public String liveApply() {
            return "SKIP";
        }
    }

    private G10IdentityDriftHonesty() {}

    public static Inventory probeLive() {
        boolean listener = tcpOpen(HOST, KEYCLOAK_PORT);
        String issuer = issuerFromEnv();
        boolean discovery = discoveryOk(issuer);
        return new Inventory(listener, discovery, issuer, skipReason(listener, discovery, issuer));
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
        require(errors, report, "dry_run_default_no_write", "true");
        require(errors, report, "identity_apply_product_redone", "false");
        require(errors, report, "identity_drift_141_redone", "false");
        require(errors, report, "file_reconcile_169_redone", "false");
        require(errors, report, "saml_claimed", "false");
        require(errors, report, "ldap_claimed", "false");
        require(errors, report, "sms_mfa_claimed", "false");
        require(errors, report, "g10_close", G10_CLOSE);
        require(errors, report, "live_apply", "SKIP");
        require(errors, report, "live_apply_observed", "false");

        String live = value(report, "live_keycloak_8080");
        if (!isVerdict(live)) {
            errors.add("live_keycloak_8080 must be PASS/SKIP/FAIL");
        }
        if ("PASS".equals(live)) {
            if (!"true".equals(value(report, "live_keycloak_present"))) {
                errors.add("live_keycloak_8080=PASS requires live_keycloak_present=true");
            }
            if (!"true".equals(value(report, "keycloak_oidc_discovery"))) {
                errors.add("live_keycloak_8080=PASS requires keycloak_oidc_discovery=true");
            }
            if (!"true".equals(value(report, "live_dry_run_observed"))) {
                errors.add("live_keycloak_8080=PASS requires live_dry_run_observed=true");
            }
        }
        if ("SKIP".equals(live)) {
            if (blank(value(report, "live_keycloak_skip_reason"))) {
                errors.add("live_keycloak_8080 SKIP needs live_keycloak_skip_reason");
            }
            if ("true".equals(value(report, "live_dry_run_observed"))) {
                errors.add("SKIP cannot claim a live dry-run walk");
            }
        }
        if (blank(value(report, "live_apply_skip_reason"))) {
            errors.add("live_apply SKIP needs live_apply_skip_reason");
        }
        for (String key : report.keySet()) {
            String upper = key.toUpperCase(Locale.ROOT);
            if (upper.contains("PASSWORD") || upper.contains("SECRET") || upper.contains("PASSWD")
                    || upper.contains("CLIENT_SECRET")) {
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
        report.put("dry_run_default_no_write", "true");
        report.put("keycloak_8080_listener", Boolean.toString(inventory.listenerPresent()));
        report.put("keycloak_oidc_discovery", Boolean.toString(inventory.oidcDiscovery()));
        report.put("live_keycloak_present", Boolean.toString(inventory.livePresent()));
        report.put("live_keycloak_8080", "SKIP");
        report.put("live_keycloak_skip_reason", inventory.skipReason());
        report.put("live_dry_run_observed", "false");
        report.put("live_apply", "SKIP");
        report.put("live_apply_skip_reason", applySkipReason());
        report.put("live_apply_observed", "false");
        report.put("identity_apply_product_redone", "false");
        report.put("identity_drift_141_redone", "false");
        report.put("file_reconcile_169_redone", "false");
        report.put("saml_claimed", "false");
        report.put("ldap_claimed", "false");
        report.put("sms_mfa_claimed", "false");
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

    static boolean discoveryOk(String issuer) {
        if (blank(issuer)) {
            return false;
        }
        String url = issuer + "/.well-known/openid-configuration";
        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(3)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200
                    && response.body() != null
                    && response.body().toLowerCase(Locale.ROOT).contains("\"issuer\"");
        } catch (Exception ignored) {
            return false;
        }
    }

    static String issuerFromEnv() {
        String fromEnv = env("NEXUS_KEYCLOAK_ISSUER");
        return blank(fromEnv) ? DEFAULT_ISSUER : fromEnv;
    }

    static String applySkipReason() {
        return "identity apply stays on nexus-p2-iam/scripts/run-g10-identity-reconcile.sh; "
                + "this leftover does not redo the product apply path";
    }

    private static String skipReason(boolean listener, boolean discovery, String issuer) {
        if (!listener && !discovery) {
            return "Keycloak 8080 unavailable; nothing listening on 127.0.0.1:" + KEYCLOAK_PORT
                    + " and OIDC discovery not reachable at " + issuer + "; Docker forbidden";
        }
        if (!listener) {
            return "OIDC discovery answered but nothing listening on 127.0.0.1:" + KEYCLOAK_PORT
                    + "; live Keycloak 8080 SKIP";
        }
        if (!discovery) {
            return "listener on 127.0.0.1:" + KEYCLOAK_PORT + " but OIDC discovery not reachable at "
                    + issuer + "; live Keycloak 8080 SKIP";
        }
        return "Keycloak 8080 present (" + issuer
                + ") but this leftover does not claim a dry-run walk until it was observed; "
                + "does not start Keycloak";
    }

    private static String[] requiredKeys() {
        return new String[] {
                "schema_version", "timestamp_utc", "java_feature", "boot_parent_version",
                "boot_parent_match", "docker_used", "dry_run_default_no_write",
                "keycloak_8080_listener", "keycloak_oidc_discovery", "live_keycloak_present",
                "live_keycloak_8080", "live_keycloak_skip_reason", "live_dry_run_observed",
                "live_apply", "live_apply_skip_reason", "live_apply_observed",
                "identity_apply_product_redone", "identity_drift_141_redone",
                "file_reconcile_169_redone", "saml_claimed", "ldap_claimed", "sms_mfa_claimed",
                "g10_close", "g18_status", "audit_port_changed", "p0_m0_redone"
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
