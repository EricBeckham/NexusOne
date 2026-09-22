package one.nexus.p3.studio.form;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import one.nexus.p3.studio.StudioIsolation;
import one.nexus.p3.studio.meta.InMemoryLedgerMetadataCatalog;

/**
 * G5 leftover honesty inventory beyond #145 / #171.
 *
 * <p>The in-memory mid-publish abort-after-SchemaService harness may
 * {@code PASS}. A live browser failed-publish click through
 * {@link SchemaServicePublishHook} toward real DDL is {@code SKIP}
 * unless JAR + IdP + browser + observed fail-click were present.
 * Does not redo #94 / #145 / #171 product suites. Does not claim
 * product G5 close. Does not reopen G18.
 */
public final class G5MidPublishFailureHonesty {
    public static final String BOTH_LEFTOVER_ROWS = "g5-mid-publish-failure,g5-browser-failed-publish";
    public static final String DEFAULT_JAR_HEALTH = "http://127.0.0.1:8080/actuator/health";
    public static final String DEFAULT_IDP_DISCOVERY =
            "http://127.0.0.1:8080/realms/nexus/.well-known/openid-configuration";
    private static final String TENANT = "11111111-1111-4111-8111-111111111111";

    public record MidPublishHarness(
            boolean runningPointerUnchanged,
            boolean snapshotUnchanged,
            boolean rolledBack,
            boolean noV2Version,
            String cause
    ) {
        public String verdict() {
            boolean pass = runningPointerUnchanged
                    && snapshotUnchanged
                    && rolledBack
                    && noV2Version
                    && cause != null
                    && cause.contains("mid-publish abort after SchemaService");
            return pass ? "PASS" : "FAIL";
        }
    }

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

    private G5MidPublishFailureHonesty() {}

    public static MidPublishHarness proveMidPublishHarness() {
        IsolationKernel kernel =
                IsolationKernel.create(new TableRegistry(), Set.of("p3.studio"), new CollectingAuditSink());
        InMemoryLedgerMetadataCatalog metadata = new InMemoryLedgerMetadataCatalog();
        metadata.seedDemoLeaveRequest();
        InMemoryFormStore store = new InMemoryFormStore();
        StudioIsolation.bindTenant(kernel, TENANT, "designer-a", "mem-g5-honesty", "trace-g5-honesty");
        try {
            UUID tenantId = UUID.fromString(TENANT);
            FormService service = new FormService(
                    store,
                    metadata,
                    new SchemaServicePublishHook(
                            FormPublishTestStubs.abortAfterNthEnsure(
                                    FormPublishTestStubs.abortOnNthConfirm(99), 2),
                            FormPublishTestStubs.leaveLedgers(tenantId),
                            FormPublishTestStubs.leaveFields(tenantId)),
                    Clock.systemUTC(),
                    null);
            FormRecord form = service.create("demo", "leave_form", "请假单", null, leaveBindings());
            service.validate(form.formId());
            service.publish(form.formId(), "v1");
            Map<String, Object> snapshot = service.running(form.formId()).snapshot().toJson();
            service.update(form.formId(), "请假单 v2", null, leaveBindings());
            service.validate(form.formId());
            try {
                service.publish(form.formId(), "v2");
                return new MidPublishHarness(false, false, false, false, "v2 publish unexpectedly succeeded");
            } catch (NexusException failed) {
                if (!ErrorCode.FORM_PUBLISH_FAILED.equals(failed.errorCode())) {
                    return new MidPublishHarness(
                            false, false, false, false, "unexpected error " + failed.errorCode());
                }
                String cause = String.valueOf(failed.details().get("cause"));
                return new MidPublishHarness(
                        service.get(form.formId()).runningVersion() == 1
                                && service.running(form.formId()).status() == VersionStatus.RUNNING,
                        snapshot.equals(service.running(form.formId()).snapshot().toJson()),
                        service.publishLog(form.formId()).stream()
                                .anyMatch(log -> "ROLLED_BACK".equals(log.outcome())),
                        service.versions(form.formId()).size() == 1,
                        cause);
            }
        } finally {
            AccessContextHolder.clear();
        }
    }

    public static LiveInventory probeLive() {
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
                    + "; #145 memory harness is not a live fail-click"
                    + "; #171 leftover is not redone"
                    + "; publish-log viewer is not a failed-publish click"
                    + "; Docker forbidden";
        } else if (!clickObserved || !ddlObserved || !abortHonest) {
            reason = "live surfaces listed but a failed Publish click through"
                    + " SchemaServicePublishHook toward real DDL was not observed"
                    + "; mid-fail/abort honesty stays on #94/#145"
                    + "; #171 leftover is not redone"
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
        require(report, "redone_94", "false");
        require(report, "redone_145", "false");
        require(report, "redone_171", "false");
        require(report, "g5_close", "NOT_CLAIMED");
        require(report, "simulate_publish_log_is_fail_click", "false");
        require(report, "mid_publish_harness", "PASS");
        require(report, "running_pointer_unchanged", "true");
        require(report, "snapshot_unchanged", "true");
        require(report, "rolled_back", "true");
        require(report, "no_v2_version", "true");
        String rows = report.get("leftover_rows_kept");
        if (rows == null
                || !rows.contains("g5-mid-publish-failure")
                || !rows.contains("g5-browser-failed-publish")) {
            throw new IllegalArgumentException(
                    "leftover_rows_kept must keep both g5-mid-publish-failure and g5-browser-failed-publish");
        }
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
            if ("true".equals(report.get("click_observed"))) {
                throw new IllegalArgumentException("SKIP cannot claim a live failed-publish click");
            }
        }
        for (String key : report.keySet()) {
            String upper = key.toUpperCase(Locale.ROOT);
            if (upper.contains("PASSWORD") || upper.contains("SECRET") || upper.contains("PASSWD")
                    || upper.contains("TOKEN")) {
                throw new IllegalArgumentException("report must not contain password/secret/token material");
            }
        }
        String joined = String.join(" ", report.values()).toLowerCase(Locale.ROOT);
        if (joined.contains("product g5 closed") || joined.contains("g5_close=closed")) {
            throw new IllegalArgumentException("report must not claim product G5 closed");
        }
    }

    public static Map<String, String> honestSkipTemplate(MidPublishHarness harness, LiveInventory live) {
        Map<String, String> report = new LinkedHashMap<>();
        report.put("schema_version", "1");
        report.put("timestamp_utc", "");
        report.put("java_feature", "21");
        report.put("boot_parent_version", "4.1.1");
        report.put("boot_parent_match", "PASS");
        report.put("docker_used", "false");
        report.put("mid_publish_harness", harness.verdict());
        report.put("running_pointer_unchanged", Boolean.toString(harness.runningPointerUnchanged()));
        report.put("snapshot_unchanged", Boolean.toString(harness.snapshotUnchanged()));
        report.put("rolled_back", Boolean.toString(harness.rolledBack()));
        report.put("no_v2_version", Boolean.toString(harness.noV2Version()));
        report.put("leftover_rows_kept", BOTH_LEFTOVER_ROWS);
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
        report.put("redone_94", "false");
        report.put("redone_145", "false");
        report.put("redone_171", "false");
        return report;
    }

    private static List<FormBinding> leaveBindings() {
        return List.of(
                new FormBinding("title", "leave_request", "title", true, false, WidgetType.TEXT, 0),
                new FormBinding("days", "leave_request", "days", true, false, WidgetType.NUMBER, 1),
                new FormBinding("reason", "leave_request", "reason", false, false, WidgetType.TEXTAREA, 2),
                new FormBinding("applicant", "leave_request", "applicant", true, false, WidgetType.PERSON, 3));
    }

    private static String[] requiredKeys() {
        return new String[] {
                "schema_version", "timestamp_utc", "java_feature", "boot_parent_version",
                "boot_parent_match", "docker_used", "mid_publish_harness",
                "running_pointer_unchanged", "snapshot_unchanged", "rolled_back",
                "no_v2_version", "leftover_rows_kept", "live_jar", "jar_url",
                "live_idp", "idp_url", "live_browser", "browser_name",
                "click_observed", "publishhook_ddl_observed", "mid_fail_abort_honest",
                "browser_failed_publish", "browser_skip_reason",
                "simulate_publish_log_is_fail_click", "g5_close", "g18_status",
                "audit_port_changed", "p0_m0_redone", "redone_94", "redone_145",
                "redone_171"
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
        for (String name : new String[] {
                "google-chrome", "chromium", "chromium-browser", "chrome", "google-chrome-stable"
        }) {
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
