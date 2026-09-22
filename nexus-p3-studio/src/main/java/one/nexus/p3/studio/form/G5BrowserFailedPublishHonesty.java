package one.nexus.p3.studio.form;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * G5 leftover honesty inventory for a <em>browser failed-publish click</em>
 * through {@link SchemaServicePublishHook} toward real SchemaService DDL.
 * Does not redo #94 evolve DDL abort or #145 mid-publish failure rollback.
 * Does not claim product G5 close. Does not reopen G18.
 */
public final class G5BrowserFailedPublishHonesty {
    public static final String DEFAULT_JAR_HEALTH = "http://127.0.0.1:8080/actuator/health";
    public static final String DEFAULT_IDP_DISCOVERY =
            "http://127.0.0.1:8080/realms/nexus/.well-known/openid-configuration";

    public record LiveInventory(
            boolean liveJar,
            String jarUrl,
            boolean liveIdp,
            String idpUrl,
            boolean liveBrowser,
            String browserName,
            boolean clickObserved,
            boolean publishHookDdlObserved,
            boolean midFailAbortHonest,
            String skipReason
    ) {
        public String browserFailedPublish() {
            return "SKIP";
        }
    }

    private G5BrowserFailedPublishHonesty() {}

    public static LiveInventory probe() {
        String jarUrl = firstNonBlank(
                System.getenv("NEXUS_BASE_URL"),
                System.getenv("NEXUS_G5_JAR_HEALTH"),
                DEFAULT_JAR_HEALTH);
        if (jarUrl.endsWith("/")) {
            jarUrl = jarUrl.substring(0, jarUrl.length() - 1);
        }
        if (!jarUrl.contains("/actuator/")) {
            jarUrl = jarUrl + "/actuator/health";
        }
        boolean liveJar = reachable(jarUrl);

        String idpUrl = firstNonBlank(
                issuerDiscovery(System.getenv("NEXUS_KEYCLOAK_ISSUER")),
                System.getenv("NEXUS_G5_IDP_DISCOVERY"),
                DEFAULT_IDP_DISCOVERY);
        boolean liveIdp = reachable(idpUrl);

        String browserName = detectBrowser();
        boolean liveBrowser = !browserName.isBlank()
                || "true".equalsIgnoreCase(env("NEXUS_G5_BROWSER_PRESENT"));
        if (liveBrowser && browserName.isBlank()) {
            browserName = "env-present";
        }

        boolean clickObserved = "true".equalsIgnoreCase(env("NEXUS_G5_FAILED_PUBLISH_CLICK_OBSERVED"));
        boolean ddlObserved = "true".equalsIgnoreCase(env("NEXUS_G5_PUBLISHHOOK_DDL_OBSERVED"));
        boolean abortHonest = "true".equalsIgnoreCase(env("NEXUS_G5_MID_FAIL_ABORT_HONEST"));

        String reason;
        if (!liveJar || !liveIdp || !liveBrowser) {
            reason = "live browser/IdP/JAR unavailable"
                    + "; jar=" + liveJar
                    + " idp=" + liveIdp
                    + " browser=" + liveBrowser
                    + "; publish-log viewer is not a failed-publish click"
                    + "; Docker forbidden";
        } else if (!clickObserved || !ddlObserved || !abortHonest) {
            reason = "live surfaces listed but a failed Publish click through"
                    + " SchemaServicePublishHook toward real DDL was not observed"
                    + "; mid-fail/abort honesty stays on #94/#145"
                    + "; publish-log viewer is not a failed-publish click";
        } else {
            reason = "observed walk still does not claim product G5 close";
        }
        return new LiveInventory(
                liveJar, jarUrl, liveIdp, idpUrl, liveBrowser, browserName,
                clickObserved, ddlObserved, abortHonest, reason);
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
        require(report, "p0_m0_redone", "false");
        require(report, "g5_94_145_redone", "false");
        require(report, "g5_close", "NOT_CLAIMED");
        require(report, "simulate_publish_log_is_fail_click", "false");
        requireVerdict(report, "browser_failed_publish");
        refusePassWithout(report, "browser_failed_publish", "live_jar", "true",
                "browser_failed_publish=PASS requires live_jar=true");
        refusePassWithout(report, "browser_failed_publish", "live_idp", "true",
                "browser_failed_publish=PASS requires live_idp=true");
        refusePassWithout(report, "browser_failed_publish", "live_browser", "true",
                "browser_failed_publish=PASS requires live_browser=true");
        refusePassWithout(report, "browser_failed_publish", "click_observed", "true",
                "browser_failed_publish=PASS requires click_observed=true");
        refusePassWithout(report, "browser_failed_publish", "publishhook_ddl_observed", "true",
                "browser_failed_publish=PASS requires publishhook_ddl_observed=true");
        refusePassWithout(report, "browser_failed_publish", "mid_fail_abort_honest", "true",
                "browser_failed_publish=PASS requires mid_fail_abort_honest=true");
        if ("SKIP".equals(report.get("browser_failed_publish"))) {
            String reason = report.get("browser_skip_reason");
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("browser_failed_publish SKIP needs browser_skip_reason");
            }
        }
        for (String key : report.keySet()) {
            String upper = key.toUpperCase();
            if (upper.contains("PASSWORD") || upper.contains("SECRET") || upper.contains("PASSWD")) {
                throw new IllegalArgumentException("report must not contain password/secret material");
            }
        }
    }

    public static Map<String, String> honestSkipTemplate(LiveInventory live) {
        Map<String, String> report = new LinkedHashMap<>();
        report.put("schema_version", "1");
        report.put("timestamp_utc", "");
        report.put("java_feature", "21");
        report.put("boot_parent_version", "4.1.1");
        report.put("boot_parent_match", "PASS");
        report.put("docker_used", "false");
        report.put("live_jar", Boolean.toString(live.liveJar()));
        report.put("jar_url", nullToEmpty(live.jarUrl()));
        report.put("live_idp", Boolean.toString(live.liveIdp()));
        report.put("idp_url", nullToEmpty(live.idpUrl()));
        report.put("live_browser", Boolean.toString(live.liveBrowser()));
        report.put("browser_name", nullToEmpty(live.browserName()));
        report.put("click_observed", Boolean.toString(live.clickObserved()));
        report.put("publishhook_ddl_observed", Boolean.toString(live.publishHookDdlObserved()));
        report.put("mid_fail_abort_honest", Boolean.toString(live.midFailAbortHonest()));
        report.put("browser_failed_publish", "SKIP");
        report.put("browser_skip_reason", live.skipReason());
        report.put("simulate_publish_log_is_fail_click", "false");
        report.put("g5_close", "NOT_CLAIMED");
        report.put("g18_status", "closed_unchanged");
        report.put("audit_port_changed", "false");
        report.put("p0_m0_redone", "false");
        report.put("g5_94_145_redone", "false");
        return report;
    }

    private static String[] requiredKeys() {
        return new String[] {
                "schema_version", "timestamp_utc", "java_feature", "boot_parent_version",
                "boot_parent_match", "docker_used", "live_jar", "jar_url", "live_idp",
                "idp_url", "live_browser", "browser_name", "click_observed",
                "publishhook_ddl_observed", "mid_fail_abort_honest",
                "browser_failed_publish", "browser_skip_reason",
                "simulate_publish_log_is_fail_click", "g5_close", "g18_status",
                "audit_port_changed", "p0_m0_redone", "g5_94_145_redone"
        };
    }

    private static void require(Map<String, String> report, String key, String expected) {
        String actual = report.get(key);
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(key + " must be " + expected + ", got " + actual);
        }
    }

    private static void requireVerdict(Map<String, String> report, String key) {
        String value = report.get(key);
        if (!"PASS".equals(value) && !"SKIP".equals(value) && !"FAIL".equals(value)) {
            throw new IllegalArgumentException(key + " must be PASS/SKIP/FAIL");
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

    private static boolean reachable(String url) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(250))
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(400))
                    .GET()
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 500;
        } catch (Exception ex) {
            return false;
        }
    }

    private static String detectBrowser() {
        for (String name : new String[] {"google-chrome", "chromium", "chromium-browser", "chrome", "google-chrome-stable"}) {
            if (onPath(name)) {
                return name;
            }
        }
        return "";
    }

    private static boolean onPath(String name) {
        String path = Optional.ofNullable(System.getenv("PATH")).orElse("");
        for (String dir : path.split(":")) {
            if (dir.isBlank()) {
                continue;
            }
            if (Files.isExecutable(Path.of(dir, name))) {
                return true;
            }
        }
        return false;
    }

    private static String issuerDiscovery(String issuer) {
        if (issuer == null || issuer.isBlank()) {
            return "";
        }
        String trimmed = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
        return trimmed + "/.well-known/openid-configuration";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String env(String key) {
        return Optional.ofNullable(System.getenv(key)).orElse("");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
