package one.nexus.p2.idp;

import java.io.IOException;
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
 * G4 leftover honesty inventory <em>beyond #177 / #147</em>: live Keycloak
 * CA / SPA-held PKCE remainder PASS/SKIP.
 *
 * <p>#147 SPA S256 mint / client-held challenge stay always-green.
 * #177 honesty harness stay always-green. Live Keycloak Authorization
 * Code + PKCE is PASS only when OIDC discovery answers <em>and</em> a
 * SPA-held token exchange was actually observed. #147 harness discovery
 * and #177 honesty collect are not a live token exchange. Missing
 * Keycloak is honest SKIP. Does not start {@code kc.sh}. Does not redo
 * #108 / #120 / #125 / #147 / #152 / #166 / #177. Does not claim
 * product G4 close. G18 stays closed. No P0/M0 redo.
 */
public final class G4PkceSpaVerifierRemainderHonesty {
    public static final String G4_CLOSE = "NOT_CLAIMED";

    public static final String ROW_147 = "g4-pkce-spa-verifier";
    public static final String ROW_177 = "g4-pkce-spa-verifier-honesty";
    public static final String ROW_REMAINDER = "g4-pkce-spa-verifier-remainder-honesty";
    public static final String ROW_HOT_PURGE = "g4-hot-purge-90d-honesty";
    public static final String ROW_EXPORT = "g4-export-wizard-deny-honesty";
    public static final String ROW_P1 = "g4-p1-staging-audit-events-honesty";
    public static final String ROW_G9 = "g9-sentinel-cluster-remainder-honesty";
    public static final String ROW_G9_148 = "g9-sentinel-cluster-honesty";
    public static final String ROW_G9_161 = "g9-live-multi-jvm-replica";
    public static final String ROW_G9_174 = "g9-redis-down-invalidate-honesty";
    public static final String ROW_G9_197 = "g9-redis-down-invalidate-remainder";
    public static final String ROW_G3 = "g3-outbox-consume-crash-honesty";
    public static final String ROW_G5 = "g5-mid-publish-failure-honesty";
    public static final String ROW_G5_217 = "g5-publishhook-ddl-honesty";
    public static final String ROW_G10 = "g10-leftover-honesty";
    public static final String ROW_G2 = "g2-h5-every-page-form-write-honesty";
    public static final String ROW_G2_OPS = "g2-ops-pg-dump-honesty";
    public static final String ROW_G17 = "g17-mfa-recovery-vue-otp-honesty";
    public static final String ROW_G17_210 = "g17-gm-tls-nginx-handshake-honesty";
    public static final String ROW_G17_216 = "g17-separation-leftover-honesty";
    public static final String ROW_G15 = "g15-wx-dt-native-bridge-honesty";
    public static final String ROW_G15_220 = "g15-wx-dt-native-bridge-remainder";
    public static final String ROW_S0 = "s0-secret-rotate-without-restart-honesty";
    public static final String ROW_S0S1 = "s0s1-live-redis-minio-isolation-honesty";
    public static final String ROW_G6 = "g6-recovery-port-dry-run-honesty";
    public static final String ROW_ARM = "linux-arm-profile-leftover-honesty";
    public static final String ROW_G7 = "g7-query-expense-travel-honesty";
    public static final String ROW_G1 = "g1-queryast-boundsql-honesty";
    public static final String ROW_G1_215 = "g1-queryast-export-honesty";

    public static final String SIBLING_ROWS = String.join(",",
            ROW_147, ROW_177, ROW_REMAINDER, ROW_HOT_PURGE, ROW_EXPORT, ROW_P1,
            ROW_G9, ROW_G9_148, ROW_G9_161, ROW_G9_174, ROW_G9_197,
            ROW_G3, ROW_G5, ROW_G5_217, ROW_G10, ROW_G2, ROW_G2_OPS, ROW_G17, ROW_G17_210, ROW_G17_216, ROW_G15,
            ROW_G15_220, ROW_S0, ROW_S0S1, ROW_G6, ROW_ARM, ROW_G7, ROW_G1, ROW_G1_215);

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
            "g7-business-types",
            "g7-withdraw-browser-honesty",
            "g7-query-expense-travel-honesty",
            "g17-separation-of-duties",
            "g17-separation-honesty",
            "g17-separation-leftover-honesty",
            "g17-gm-tls-dual-cert",
            "g17-admin-totp-enrollment",
            "g17-totp-enrollment-ux",
            "g17-admin-mfa-recovery-email",
            "g17-mfa-recovery-email-honesty",
            "g17-vue-otp-writeback",
            "g17-mfa-recovery-vue-otp-honesty",
            "g17-gm-tls-nginx-dual-stack",
            "g17-gm-tls-nginx-handshake",
            "g17-gm-tls-nginx-handshake-honesty",
            "g2-form-submission-write",
            "g2-submit-start-form-write",
            "g2-form-write-honesty",
            "g2-h5-permission",
            "g2-h5-every-page",
            "g2-h5-every-page-form-write-honesty",
            "g2-report-surface-guard",
            "g2-physical-dump-surface",
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
            ROW_147,
            ROW_177,
            ROW_REMAINDER,
            "g6-recovery-port-dry-run",
            "g6-recovery-port-honesty",
            "g6-recovery-port-dry-run-honesty",
            "g10-identity-drift-dry-run",
            "g10-file-reconcile-honesty",
            "g10-identity-drift-honesty",
            "g10-leftover-honesty",
            "linux-arm-profile-honesty",
            "linux-arm-profile-leftover-honesty",
            "g5-mid-publish-failure",
            "g5-browser-failed-publish",
            "g5-mid-publish-failure-honesty",
            "g5-publishhook-ddl-honesty",
            "g9-multinode-partition",
            "g9-sentinel-cluster-honesty",
            "g9-sentinel-cluster-remainder-honesty",
            "g9-live-multi-jvm-replica",
            "g9-policysnapshot-invalidate",
            "g9-failover-remainder",
            "g9-redis-down-invalidate-honesty",
            "g9-redis-down-invalidate-remainder",
            "g1-export-fieldpolicy-drop",
            "g1-fieldpolicy-mask-second-auth",
            "g1-fieldpolicy-mask-second-auth-honesty",
            "g3-outbox-consume-idempotent",
            "g3-claim-crash-window",
            "g3-outbox-consume-crash-honesty",
            "s0-secret-rotate-without-restart",
            "s0-secret-getenv-snapshot-honesty",
            "s0-secret-rotate-without-restart-honesty",
            "s0s1-live-redis-minio-isolation-honesty"
    );

    public record Inventory(
            boolean httpDiscovery,
            boolean httpsDiscovery,
            boolean caPresent,
            boolean keycloakHomePresent,
            boolean tokenExchangeObserved,
            boolean caTrustObserved,
            boolean laptopClickObserved,
            String livePkceSkipReason,
            String liveCaSkipReason,
            String laptopClickSkipReason
    ) {
        public boolean discoveryReachable() {
            return httpDiscovery || httpsDiscovery;
        }

        public String liveKeycloakPkce() {
            return "SKIP";
        }

        public String liveCaTrust() {
            return "SKIP";
        }

        public String humanLaptopClick() {
            return "SKIP";
        }

        public boolean discoveryIsNotLiveExchange() {
            return true;
        }

        public boolean honesty177IsNotLiveExchange() {
            return true;
        }
    }

    private G4PkceSpaVerifierRemainderHonesty() {}

    public static boolean rfc7636AppendixB() {
        return G4PkceSpaVerifierHonesty.rfc7636AppendixB();
    }

    public static boolean clientHeldDoesNotMintVerifier() {
        return G4PkceSpaVerifierHonesty.clientHeldDoesNotMintVerifier();
    }

    public static boolean honesty177HarnessStaysPass() {
        return rfc7636AppendixB() && clientHeldDoesNotMintVerifier();
    }

    public static Inventory probeLive() {
        G4PkceSpaVerifierHonesty.Inventory base = G4PkceSpaVerifierHonesty.probeLive();
        return new Inventory(
                base.httpDiscovery(),
                base.httpsDiscovery(),
                base.caPresent(),
                base.keycloakHomePresent(),
                base.tokenExchangeObserved(),
                base.caTrustObserved(),
                base.laptopClickObserved(),
                remainderPkceSkipReason(base),
                remainderCaSkipReason(base),
                remainderLaptopSkipReason(base));
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
        require(errors, report, "kc_sh_started", "false");
        require(errors, report, "g4_close", G4_CLOSE);
        require(errors, report, "spa_verifier_mint", "PASS");
        require(errors, report, "rfc7636_appendix_b", "PASS");
        require(errors, report, "client_held_no_bff_verifier", "PASS");
        require(errors, report, "honesty_177_harness", "PASS");
        require(errors, report, "leftover_rows_kept", "true");
        require(errors, report, "discovery_is_not_live_exchange", "true");
        require(errors, report, "honesty_177_is_not_live_exchange", "true");
        require(errors, report, "redo_108", "false");
        require(errors, report, "redo_120", "false");
        require(errors, report, "redo_125", "false");
        require(errors, report, "redo_147", "false");
        require(errors, report, "redo_152", "false");
        require(errors, report, "redo_166", "false");
        require(errors, report, "redo_177", "false");
        require(errors, report, "g4_108_120_125_147_152_166_177_redone", "false");

        String rows = value(report, "leftover_rows");
        for (String row : List.of(
                ROW_147, ROW_177, ROW_REMAINDER, ROW_HOT_PURGE, ROW_EXPORT, ROW_P1,
                ROW_G9, ROW_G9_148, ROW_G9_161, ROW_G9_174, ROW_G9_197,
                ROW_G3, ROW_G5, ROW_G5_217, ROW_G10, ROW_G2, ROW_G2_OPS, ROW_G17, ROW_G17_210, ROW_G17_216, ROW_G15,
                ROW_G15_220, ROW_S0, ROW_S0S1, ROW_G6, ROW_ARM, ROW_G7, ROW_G1, ROW_G1_215)) {
            if (!rows.contains(row)) {
                errors.add("leftover_rows must keep " + row);
            }
        }

        refusePassWithout(errors, report, "live_keycloak_pkce", "keycloak_discovery", "PASS",
                "live_keycloak_pkce=PASS requires keycloak_discovery=PASS");
        refusePassWithout(errors, report, "live_keycloak_pkce", "token_exchange_observed", "true",
                "live_keycloak_pkce=PASS requires token_exchange_observed=true");
        refusePassWithout(errors, report, "live_ca_trust", "ca_cert_present", "true",
                "live_ca_trust=PASS requires ca_cert_present=true");
        refusePassWithout(errors, report, "live_ca_trust", "keycloak_https_discovery", "PASS",
                "live_ca_trust=PASS requires keycloak_https_discovery=PASS");
        refusePassWithout(errors, report, "live_ca_trust", "ca_trust_observed", "true",
                "live_ca_trust=PASS requires ca_trust_observed=true");
        refusePassWithout(errors, report, "human_laptop_click", "laptop_click_observed", "true",
                "human_laptop_click=PASS requires laptop_click_observed=true");

        requireVerdict(errors, report, "live_keycloak_pkce");
        requireVerdict(errors, report, "live_ca_trust");
        requireVerdict(errors, report, "human_laptop_click");
        requireSkipReason(errors, report, "live_keycloak_pkce", "live_keycloak_skip_reason");
        requireSkipReason(errors, report, "live_ca_trust", "live_ca_skip_reason");
        requireSkipReason(errors, report, "human_laptop_click", "laptop_click_skip_reason");

        if ("SKIP".equals(value(report, "live_keycloak_pkce"))
                && "true".equals(value(report, "token_exchange_observed"))) {
            errors.add("SKIP cannot claim token_exchange_observed=true");
        }
        if ("SKIP".equals(value(report, "live_ca_trust"))
                && "true".equals(value(report, "ca_trust_observed"))) {
            errors.add("SKIP cannot claim ca_trust_observed=true");
        }
        if ("SKIP".equals(value(report, "human_laptop_click"))
                && "true".equals(value(report, "laptop_click_observed"))) {
            errors.add("SKIP cannot claim laptop_click_observed=true");
        }

        for (String key : report.keySet()) {
            String upper = key.toUpperCase(Locale.ROOT);
            if (upper.contains("PASSWORD") || upper.contains("SECRET") || upper.contains("PASSWD")
                    || upper.contains("CODE_VERIFIER") || upper.contains("CLIENT_SECRET")) {
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
        report.put("spa_verifier_mint", "PASS");
        report.put("rfc7636_appendix_b", "PASS");
        report.put("client_held_no_bff_verifier", "PASS");
        report.put("honesty_177_harness", "PASS");
        report.put("leftover_rows_kept", "true");
        report.put("leftover_rows", SIBLING_ROWS);
        report.put("discovery_is_not_live_exchange", "true");
        report.put("honesty_177_is_not_live_exchange", "true");
        report.put("keycloak_http_discovery", inventory.httpDiscovery() ? "PASS" : "SKIP");
        report.put("keycloak_https_discovery", inventory.httpsDiscovery() ? "PASS" : "SKIP");
        report.put("keycloak_discovery", inventory.discoveryReachable() ? "PASS" : "SKIP");
        report.put("keycloak_home_present", Boolean.toString(inventory.keycloakHomePresent()));
        report.put("ca_cert_present", Boolean.toString(inventory.caPresent()));
        report.put("kc_sh_started", "false");
        report.put("token_exchange_observed", Boolean.toString(inventory.tokenExchangeObserved()));
        report.put("live_keycloak_pkce", "SKIP");
        report.put("live_keycloak_skip_reason", inventory.livePkceSkipReason());
        report.put("ca_trust_observed", Boolean.toString(inventory.caTrustObserved()));
        report.put("live_ca_trust", "SKIP");
        report.put("live_ca_skip_reason", inventory.liveCaSkipReason());
        report.put("laptop_click_observed", Boolean.toString(inventory.laptopClickObserved()));
        report.put("human_laptop_click", "SKIP");
        report.put("laptop_click_skip_reason", inventory.laptopClickSkipReason());
        report.put("g4_close", G4_CLOSE);
        report.put("g18_status", "closed_unchanged");
        report.put("audit_port_changed", "false");
        report.put("p0_m0_redone", "false");
        report.put("redo_108", "false");
        report.put("redo_120", "false");
        report.put("redo_125", "false");
        report.put("redo_147", "false");
        report.put("redo_152", "false");
        report.put("redo_166", "false");
        report.put("redo_177", "false");
        report.put("g4_108_120_125_147_152_166_177_redone", "false");
        return report;
    }

    private static String remainderPkceSkipReason(G4PkceSpaVerifierHonesty.Inventory base) {
        if (!base.discoveryReachable() && !base.keycloakHomePresent()) {
            return "live Keycloak OIDC discovery unreachable on :8180 / :8543; KEYCLOAK_HOME missing; "
                    + "#147 harness probe and #177 honesty collect are not a live token exchange; "
                    + "this leftover does not start kc.sh";
        }
        if (!base.discoveryReachable()) {
            return "Keycloak dist present but OIDC discovery unreachable; this leftover does not start kc.sh "
                    + "(#108/#120 own start); #147/#177 discovery is not a live SPA-held token exchange";
        }
        if (!base.tokenExchangeObserved()) {
            return "OIDC discovery reachable but SPA-held token exchange was not observed; "
                    + "#147 harness / #177 honesty collect is not a live token exchange; Docker forbidden";
        }
        return "live token exchange listed but this leftover still does not claim product G4 close";
    }

    private static String remainderCaSkipReason(G4PkceSpaVerifierHonesty.Inventory base) {
        if (!base.caPresent()) {
            return "#120 loopback CA .local/g4-https/server.crt missing; CA inventory is not a trusted laptop walk; "
                    + "#177 honesty collect is not a trusted CA walk";
        }
        if (!base.httpsDiscovery()) {
            return "loopback CA present but HTTPS OIDC discovery did not answer; CA file is not a trusted walk";
        }
        if (!base.caTrustObserved()) {
            return "HTTPS discovery reachable and CA present but a trusted laptop CA walk was not observed";
        }
        return "CA trust listed but this leftover still does not claim product G4 close";
    }

    private static String remainderLaptopSkipReason(G4PkceSpaVerifierHonesty.Inventory base) {
        if (!base.laptopClickObserved()) {
            return "#147 notebook is documented; #177 honesty leftover did not record a laptop click; "
                    + "this agent VM did not record a human laptop CA click";
        }
        return "laptop click listed but this leftover still does not claim product G4 close";
    }

    private static String[] requiredKeys() {
        return new String[] {
                "schema_version", "timestamp_utc", "java_feature", "boot_parent_version",
                "boot_parent_match", "docker_used", "spa_verifier_mint", "rfc7636_appendix_b",
                "client_held_no_bff_verifier", "honesty_177_harness", "leftover_rows_kept",
                "leftover_rows", "discovery_is_not_live_exchange",
                "honesty_177_is_not_live_exchange", "keycloak_http_discovery",
                "keycloak_https_discovery", "keycloak_discovery", "keycloak_home_present",
                "ca_cert_present", "kc_sh_started", "token_exchange_observed",
                "live_keycloak_pkce", "live_keycloak_skip_reason", "ca_trust_observed",
                "live_ca_trust", "live_ca_skip_reason", "laptop_click_observed",
                "human_laptop_click", "laptop_click_skip_reason", "g4_close",
                "g18_status", "audit_port_changed", "p0_m0_redone",
                "redo_108", "redo_120", "redo_125", "redo_147", "redo_152", "redo_166",
                "redo_177", "g4_108_120_125_147_152_166_177_redone"
        };
    }

    private static void require(List<String> errors, Map<String, String> report, String key, String expected) {
        String actual = value(report, key);
        if (!expected.equals(actual)) {
            errors.add(key + " must be " + expected + ", got " + actual);
        }
    }

    private static void requireVerdict(List<String> errors, Map<String, String> report, String key) {
        if (!isVerdict(value(report, key))) {
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

    private static boolean isVerdict(String value) {
        return "PASS".equals(value) || "SKIP".equals(value) || "FAIL".equals(value);
    }

    private static String value(Map<String, String> report, String key) {
        String raw = report.get(key);
        return raw == null ? "" : raw;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
