package one.nexus.p2.idp;

import java.io.IOException;
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
 * G4 leftover honesty inventory for live Keycloak CA / SPA-held PKCE
 * beyond #147.
 *
 * <p>#147 SPA S256 mint and the client-held authorize-url stay always-green.
 * Live Keycloak Authorization Code + PKCE is PASS only when OIDC discovery
 * answers <em>and</em> a SPA-held token exchange was actually observed.
 * Discovery-only (#147 harness probe) is not a live token exchange. Missing
 * Keycloak is honest SKIP. Does not start {@code kc.sh}. Does not redo
 * #108 / #120 / #125 / #147 / #152 / #166. Does not claim product G4 close.
 * G18 stays closed.
 */
public final class G4PkceSpaVerifierHonesty {
    public static final String G4_CLOSE = "NOT_CLAIMED";
    public static final String RFC7636_VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    public static final String RFC7636_CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
    public static final String HTTP_ISSUER = "http://127.0.0.1:8180/realms/nexus";
    public static final String HTTPS_ISSUER = "https://127.0.0.1:8543/realms/nexus";
    public static final String DEFAULT_CA = ".local/g4-https/server.crt";

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
    }

    private G4PkceSpaVerifierHonesty() {}

    public static boolean rfc7636AppendixB() {
        return RFC7636_CHALLENGE.equals(Pkce.s256(RFC7636_VERIFIER).challenge());
    }

    public static boolean clientHeldDoesNotMintVerifier() {
        Pkce.Challenge held = Pkce.clientHeld(RFC7636_CHALLENGE);
        return Pkce.clientHeld(held) && (held.verifier() == null || held.verifier().isBlank());
    }

    public static Inventory probeLive() {
        boolean http = discoveryOk(envOr("NEXUS_KEYCLOAK_HTTP_ISSUER", HTTP_ISSUER));
        boolean https = discoveryOk(envOr("NEXUS_KEYCLOAK_HTTPS_ISSUER", HTTPS_ISSUER));
        String envIssuer = env("NEXUS_KEYCLOAK_ISSUER");
        if (!envIssuer.isBlank()) {
            boolean envOk = discoveryOk(envIssuer);
            if (envIssuer.startsWith("https:")) {
                https = https || envOk;
            } else {
                http = http || envOk;
            }
        }
        boolean caPresent = Files.isRegularFile(caPath());
        boolean kcHome = Files.isRegularFile(keycloakHome().resolve("bin/kc.sh"));
        boolean tokenObserved = observed("NEXUS_G4_LIVE_TOKEN_EXCHANGE_OBSERVED");
        boolean caTrustObserved = observed("NEXUS_G4_LIVE_CA_TRUST_OBSERVED");
        boolean clickObserved = observed("NEXUS_G4_LAPTOP_CLICK_OBSERVED");
        return new Inventory(
                http,
                https,
                caPresent,
                kcHome,
                tokenObserved,
                caTrustObserved,
                clickObserved,
                livePkceSkipReason(http || https, kcHome, tokenObserved),
                liveCaSkipReason(caPresent, https, caTrustObserved),
                laptopClickSkipReason(clickObserved));
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
        require(errors, report, "leftover_rows_kept", "true");
        require(errors, report, "discovery_is_not_live_exchange", "true");
        require(errors, report, "redo_108", "false");
        require(errors, report, "redo_120", "false");
        require(errors, report, "redo_125", "false");
        require(errors, report, "redo_147", "false");
        require(errors, report, "redo_152", "false");
        require(errors, report, "redo_166", "false");

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
        report.put("leftover_rows_kept", "true");
        report.put("discovery_is_not_live_exchange", "true");
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
        return report;
    }

    static boolean discoveryOk(String issuer) {
        if (issuer == null || issuer.isBlank()) {
            return false;
        }
        String url = issuer.replaceAll("/+$", "") + "/.well-known/openid-configuration";
        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(1500)).build();
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(2)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200
                    && response.body() != null
                    && response.body().contains("\"issuer\"");
        } catch (Exception ignored) {
            return false;
        }
    }

    static Path caPath() {
        String override = env("NEXUS_G4_HTTPS_CERT");
        if (!override.isBlank()) {
            return Path.of(override);
        }
        Path nested = Path.of("..", DEFAULT_CA);
        if (Files.isRegularFile(nested)) {
            return nested;
        }
        return Path.of(DEFAULT_CA);
    }

    static Path keycloakHome() {
        String home = env("KEYCLOAK_HOME");
        if (!home.isBlank()) {
            return Path.of(home);
        }
        String user = System.getProperty("user.home", "/home/ubuntu");
        return Path.of(user, "keycloak-26.5.2");
    }

    private static String livePkceSkipReason(boolean discovery, boolean kcHome, boolean tokenObserved) {
        if (!discovery && !kcHome) {
            return "live Keycloak OIDC discovery unreachable on :8180 / :8543; KEYCLOAK_HOME missing; "
                    + "#147 harness probe is not a live token exchange; this leftover does not start kc.sh";
        }
        if (!discovery) {
            return "Keycloak dist present but OIDC discovery unreachable; this leftover does not start kc.sh "
                    + "(#108/#120 own start); #147 discovery probe is not a live SPA-held token exchange";
        }
        if (!tokenObserved) {
            return "OIDC discovery reachable but SPA-held token exchange was not observed; "
                    + "#147 harness discovery is not a live token exchange; Docker forbidden";
        }
        return "live token exchange listed but this leftover still does not claim product G4 close";
    }

    private static String liveCaSkipReason(boolean caPresent, boolean https, boolean trustObserved) {
        if (!caPresent) {
            return "#120 loopback CA .local/g4-https/server.crt missing; CA inventory is not a trusted laptop walk";
        }
        if (!https) {
            return "loopback CA present but HTTPS OIDC discovery did not answer; CA file is not a trusted walk";
        }
        if (!trustObserved) {
            return "HTTPS discovery reachable and CA present but a trusted laptop CA walk was not observed";
        }
        return "CA trust listed but this leftover still does not claim product G4 close";
    }

    private static String laptopClickSkipReason(boolean clickObserved) {
        if (!clickObserved) {
            return "#147 notebook is documented; this agent VM did not record a human laptop CA click";
        }
        return "laptop click listed but this leftover still does not claim product G4 close";
    }

    private static String[] requiredKeys() {
        return new String[] {
                "schema_version", "timestamp_utc", "java_feature", "boot_parent_version",
                "boot_parent_match", "docker_used", "spa_verifier_mint", "rfc7636_appendix_b",
                "client_held_no_bff_verifier", "leftover_rows_kept",
                "discovery_is_not_live_exchange", "keycloak_http_discovery",
                "keycloak_https_discovery", "keycloak_discovery", "keycloak_home_present",
                "ca_cert_present", "kc_sh_started", "token_exchange_observed",
                "live_keycloak_pkce", "live_keycloak_skip_reason", "ca_trust_observed",
                "live_ca_trust", "live_ca_skip_reason", "laptop_click_observed",
                "human_laptop_click", "laptop_click_skip_reason", "g4_close",
                "g18_status", "audit_port_changed", "p0_m0_redone",
                "redo_108", "redo_120", "redo_125", "redo_147", "redo_152", "redo_166"
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

    private static boolean observed(String name) {
        String raw = env(name);
        return "true".equalsIgnoreCase(raw) || "1".equals(raw);
    }

    private static String envOr(String name, String fallback) {
        String value = env(name);
        return value.isBlank() ? fallback : value;
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
