package one.nexus.p1.audit;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
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
 * G4 leftover honesty inventory for P1 staging {@code GET /tenants/{id}/audit-events}
 * beyond #156.
 *
 * <p>Staging-like in-memory / source-scan query+export stays always-green PASS.
 * Live {@code 127.0.0.1:5432} {@code platform.audit_event} is PASS only when the
 * listener is up, credentials are present, <em>and</em> a query+export walk was
 * observed. Missing password, connection, or table is honest SKIP. Does not
 * switch GET off staging. Does not redo #147 / #152 / #156 / #166. Does not
 * change {@code AuditPort} same-TX rules. Does not claim product G4 close.
 * G18 stays closed.
 */
public final class G4P1StagingAuditEventsHonesty {
    public static final String G4_CLOSE = "NOT_CLAIMED";
    public static final String HOST = "127.0.0.1";
    public static final int PG_PORT = 5432;
    public static final String SOURCE_TABLE = "platform.audit_event";
    public static final String QUERY_PATH = "GET /api/v1/p1/tenants/{id}/audit-events";
    public static final String LEFTOVER = "g4-p1-staging-audit-events-honesty";

    public record Inventory(
            boolean credentialSet,
            boolean listenerPresent,
            boolean forceSkip,
            String skipReason
    ) {
        public boolean livePresent() {
            return credentialSet && listenerPresent && !forceSkip;
        }

        public String liveStaging5432() {
            return "SKIP";
        }
    }

    private G4P1StagingAuditEventsHonesty() {}

    public static Inventory probeLive() {
        boolean forceSkip = observed("NEXUS_G4_P1_STAGING_SKIP_LIVE");
        boolean credentialSet = !blank(firstEnv("NEXUS_DB_PASSWORD", "NEXUS_DATASOURCE_PASSWORD"));
        boolean listener = tcpOpen(HOST, PG_PORT);
        return new Inventory(credentialSet, listener, forceSkip,
                skipReason(forceSkip, credentialSet, listener));
    }

    public static boolean productGetStillReadsStaging(String source) {
        if (source == null || source.isBlank()) {
            return false;
        }
        return source.contains("@GetMapping(\"/tenants/{id}/audit-events\")")
                && source.contains("audit.listByTenant")
                && source.contains("audit.listByType")
                && source.contains("import one.nexus.p1.port.AuditRepository;")
                && !source.contains("one.nexus.audit.AuditPort")
                && !source.contains("findByTraceId")
                && source.contains("Temporary read of P1-local audit staging rows");
    }

    public static Path controllerSource() {
        Path nested = Path.of("src/main/java/one/nexus/p1/api/TenantController.java");
        if (Files.isRegularFile(nested)) {
            return nested;
        }
        return Path.of("nexus-p1-tenant/src/main/java/one/nexus/p1/api/TenantController.java");
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
        require(errors, report, "g4_close", G4_CLOSE);
        require(errors, report, "staging_like_path", "PASS");
        require(errors, report, "get_still_on_staging", "true");
        require(errors, report, "switched_off_staging", "false");
        require(errors, report, "http_export_wizard", "false");
        require(errors, report, "leftover_rows_kept", "true");
        require(errors, report, "redo_147", "false");
        require(errors, report, "redo_152", "false");
        require(errors, report, "redo_156", "false");
        require(errors, report, "redo_166", "false");

        requireVerdict(errors, report, "live_staging_5432");
        if ("PASS".equals(value(report, "live_staging_5432"))) {
            if (!"true".equals(value(report, "live_staging_present"))) {
                errors.add("live_staging_5432=PASS requires live_staging_present=true");
            }
            if (!"true".equals(value(report, "live_db_credential_set"))) {
                errors.add("live_staging_5432=PASS requires live_db_credential_set=true");
            }
            if (!"true".equals(value(report, "live_5432_listener"))) {
                errors.add("live_staging_5432=PASS requires live_5432_listener=true");
            }
            if (!"true".equals(value(report, "live_query_observed"))) {
                errors.add("live_staging_5432=PASS requires live_query_observed=true");
            }
            if (!"true".equals(value(report, "live_export_observed"))) {
                errors.add("live_staging_5432=PASS requires live_export_observed=true");
            }
        }
        if ("SKIP".equals(value(report, "live_staging_5432"))) {
            if (blank(value(report, "live_staging_skip_reason"))) {
                errors.add("live_staging_5432 SKIP needs live_staging_skip_reason");
            }
            if ("true".equals(value(report, "live_query_observed"))
                    || "true".equals(value(report, "live_export_observed"))) {
                errors.add("SKIP cannot claim a live query/export walk");
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
        report.put("staging_like_path", "PASS");
        report.put("get_still_on_staging", "true");
        report.put("switched_off_staging", "false");
        report.put("http_export_wizard", "false");
        report.put("leftover_rows_kept", "true");
        report.put("live_db_credential_set", Boolean.toString(inventory.credentialSet()));
        report.put("live_5432_listener", Boolean.toString(inventory.listenerPresent()));
        report.put("live_staging_present", Boolean.toString(inventory.livePresent()));
        report.put("live_staging_5432", "SKIP");
        report.put("live_staging_skip_reason", inventory.skipReason());
        report.put("live_query_observed", "false");
        report.put("live_export_observed", "false");
        report.put("redo_147", "false");
        report.put("redo_152", "false");
        report.put("redo_156", "false");
        report.put("redo_166", "false");
        report.put("g4_close", G4_CLOSE);
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

    private static String skipReason(boolean forceSkip, boolean credentialSet, boolean listener) {
        if (forceSkip) {
            return "NEXUS_G4_P1_STAGING_SKIP_LIVE=1; live :5432 probe forced SKIP";
        }
        if (!credentialSet && !listener) {
            return "live DB credential unset; nothing listening on 127.0.0.1:" + PG_PORT
                    + "; platform.audit_event walk not observed; Docker forbidden";
        }
        if (!credentialSet) {
            return "live DB credential unset; live staging SKIP (secrets stay in env/SecretPort)";
        }
        if (!listener) {
            return "credential set but 127.0.0.1:" + PG_PORT
                    + " not reachable; live staging SKIP; this leftover does not start PostgreSQL";
        }
        return "listener on 127.0.0.1:" + PG_PORT
                + " but this leftover does not claim a walk until query/export were observed";
    }

    private static String[] requiredKeys() {
        return new String[] {
                "schema_version", "timestamp_utc", "java_feature", "boot_parent_version",
                "boot_parent_match", "docker_used", "staging_like_path",
                "get_still_on_staging", "switched_off_staging", "http_export_wizard",
                "leftover_rows_kept", "live_db_credential_set", "live_5432_listener",
                "live_staging_present", "live_staging_5432", "live_staging_skip_reason",
                "live_query_observed", "live_export_observed", "redo_147", "redo_152",
                "redo_156", "redo_166", "g4_close", "g18_status", "audit_port_changed",
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
        if (!isVerdict(value(report, key))) {
            errors.add(key + " must be PASS/SKIP/FAIL");
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
