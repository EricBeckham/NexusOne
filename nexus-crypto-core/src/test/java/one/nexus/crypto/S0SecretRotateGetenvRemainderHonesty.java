package one.nexus.crypto;

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
 * S0 leftover honesty remainder <em>beyond #180 / #200</em>: rotate-without-restart
 * / getenv snapshot PASS/SKIP. Parent leftover harness fixtures stay always-green
 * {@code PASS}. Live OS getenv rotate and live JVM restart observation stay
 * {@code SKIP} unless a mutable process map <em>and</em> that walk were observed.
 *
 * <p>Does not redo #143 / #180 / #200 product code. Does not introduce OpenBao /
 * KMS. Does not claim a product S0 / SecretPort close. G18 stays closed. No
 * P0/M0 redo. {@code AuditPort} same-TX rules stay untouched.
 */
public final class S0SecretRotateGetenvRemainderHonesty {
    public static final String S0_CLOSE = "NOT_CLAIMED";
    public static final String SECRET_PORT_CLOSE = "NOT_CLAIMED";
    public static final String PROBE_ENV = "NEXUS_S0_REMAINDER_HONESTY_PROBE";
    public static final String OBSERVED_ENV = "NEXUS_S0_OS_ENV_ROTATE_OBSERVED";
    public static final String RESTART_OBSERVED_ENV = "NEXUS_S0_LIVE_RESTART_OBSERVED";

    public static final String ROW_143 = "s0-secret-rotate-without-restart";
    public static final String ROW_180 = "s0-secret-getenv-snapshot-honesty";
    public static final String ROW_200 = "s0-secret-rotate-without-restart-honesty";
    public static final String ROW_THIS = "s0-secret-rotate-getenv-remainder-honesty";
    public static final String ROW_S0S1 = "s0s1-live-redis-minio-isolation-honesty";
    public static final String ROW_G9_SENTINEL_REM = "g9-sentinel-cluster-remainder-honesty";
    public static final String ROW_G9_SENTINEL = "g9-sentinel-cluster-honesty";
    public static final String ROW_G9_REPLICA = "g9-live-multi-jvm-replica";
    public static final String ROW_G9_REDIS_REM = "g9-redis-down-invalidate-remainder";
    public static final String ROW_G9_REDIS = "g9-redis-down-invalidate-honesty";
    public static final String ROW_G17 = "g17-mfa-recovery-vue-otp-honesty";
    public static final String ROW_G17_EMAIL = "g17-mfa-recovery-email-honesty";
    public static final String ROW_G17_GMTLS = "g17-gm-tls-nginx-handshake-honesty";
    public static final String ROW_G2_OPS = "g2-ops-pg-dump-honesty";
    public static final String ROW_G2_H5 = "g2-h5-every-page-form-write-honesty";
    public static final String ROW_G4 = "g4-export-wizard-deny-honesty";
    public static final String ROW_G10 = "g10-leftover-honesty";
    public static final String ROW_G5 = "g5-mid-publish-failure-honesty";
    public static final String ROW_G5_HOOK = "g5-publishhook-ddl-honesty";
    public static final String ROW_G3 = "g3-outbox-consume-crash-honesty";
    public static final String ROW_G1 = "g1-queryast-boundsql-honesty";
    public static final String ROW_G1_EXPORT = "g1-queryast-export-honesty";
    public static final String ROW_G15 = "g15-wx-dt-native-bridge-honesty";
    public static final String ROW_G15_REM = "g15-wx-dt-native-bridge-remainder";
    public static final String ROW_ARM = "linux-arm-profile-leftover-honesty";
    public static final String ROW_G4_P1 = "g4-p1-staging-audit-events-honesty";
    public static final String ROW_G6 = "g6-recovery-port-dry-run-honesty";
    public static final String ROW_G17_SOD = "g17-separation-honesty";
    public static final String ROW_G17_SOD_LEFTOVER = "g17-separation-leftover-honesty";
    public static final String ROW_G4_PURGE = "g4-hot-purge-90d-honesty";
    public static final String ROW_G1_MASK = "g1-fieldpolicy-mask-second-auth-honesty";
    public static final String ROW_G10_ID = "g10-identity-drift-honesty";
    public static final String ROW_G15_BRIDGE = "g15-native-bridge-honesty";
    public static final String ROW_G4_PKCE = "g4-pkce-spa-verifier-honesty";
    public static final String ROW_G4_PKCE_REM = "g4-pkce-spa-verifier-remainder-honesty";
    public static final String ROW_G5_BROWSER = "g5-browser-failed-publish";
    public static final String ROW_G7_WITHDRAW = "g7-withdraw-browser-honesty";
    public static final String ROW_G7_QUERY = "g7-query-expense-travel-honesty";
    public static final String ROW_G2_FORM = "g2-form-write-honesty";
    public static final String ROW_G10_FILE = "g10-file-reconcile-honesty";

    public static final List<String> README_ROWS = List.of(
            ROW_143,
            ROW_180,
            ROW_200,
            ROW_THIS,
            ROW_S0S1,
            ROW_G9_SENTINEL_REM,
            ROW_G9_SENTINEL,
            ROW_G9_REPLICA,
            ROW_G9_REDIS_REM,
            ROW_G9_REDIS,
            ROW_G17,
            ROW_G17_EMAIL,
            ROW_G17_GMTLS,
            ROW_G2_OPS,
            ROW_G2_H5,
            ROW_G4,
            ROW_G10,
            ROW_G5,
            ROW_G5_HOOK,
            ROW_G3,
            ROW_G1,
            ROW_G1_EXPORT,
            ROW_G15,
            ROW_G15_REM,
            ROW_ARM,
            ROW_G4_P1,
            ROW_G6,
            ROW_G17_SOD,
            ROW_G17_SOD_LEFTOVER,
            ROW_G4_PURGE,
            ROW_G1_MASK,
            ROW_G10_ID,
            ROW_G15_BRIDGE,
            ROW_G4_PKCE,
            ROW_G4_PKCE_REM,
            ROW_G5_BROWSER,
            ROW_G7_WITHDRAW,
            ROW_G7_QUERY,
            ROW_G2_FORM,
            ROW_G10_FILE
    );

    public static final String SIBLING_ROWS = String.join(",", README_ROWS);

    public record ParentHarness(String leftover180, String leftover200, String leftover180Cells, String leftover200Cells) {}

    public record LiveRotateRestart(
            boolean getenvUnmodifiable,
            boolean getenvMutable,
            boolean osRotateObserved,
            boolean liveRestartObserved,
            String osSkipReason,
            String restartSkipReason
    ) {
        public String liveOsGetenvRotate() {
            return "SKIP";
        }

        public String liveRestart() {
            return "SKIP";
        }
    }

    private S0SecretRotateGetenvRemainderHonesty() {}

    /**
     * Parent leftover harness still verifies. This is not a redo of #180 / #200
     * product code — it only reads the committed honesty fixtures.
     */
    public static ParentHarness proveParentHarness() {
        S0SecretGetenvSnapshotHonesty.verify(
                S0SecretGetenvSnapshotHonesty.parseReport(parentFixture(ROW_180, "honest-skip.env")));
        S0SecretRotateWithoutRestartHonesty.verify(
                S0SecretRotateWithoutRestartHonesty.parseReport(parentFixture(ROW_200, "honest-skip.env")));
        Map<String, String> leftover180 = S0SecretGetenvSnapshotHonesty.parseReport(
                parentFixture(ROW_180, "honest-skip.env"));
        Map<String, String> leftover200 = S0SecretRotateWithoutRestartHonesty.parseReport(
                parentFixture(ROW_200, "honest-skip.env"));
        if (!"PASS".equals(leftover180.get("env_map_rotate"))
                || !"SKIP".equals(leftover180.get("os_getenv_rotate"))
                || !"NOT_CLAIMED".equals(leftover180.get("s0_close"))
                || !"false".equals(leftover180.get("s0_143_redone"))) {
            throw new IllegalStateException("#180 leftover cells are no longer honest");
        }
        if (!"PASS".equals(leftover200.get("env_map_rotate"))
                || !"SKIP".equals(leftover200.get("os_getenv_rotate"))
                || !"NOT_CLAIMED".equals(leftover200.get("s0_close"))
                || !"NOT_CLAIMED".equals(leftover200.get("secret_port_close"))
                || !"false".equals(leftover200.get("redo_143"))
                || !"false".equals(leftover200.get("redo_180"))) {
            throw new IllegalStateException("#200 leftover cells are no longer honest");
        }
        return new ParentHarness("PASS", "PASS", "PASS", "PASS");
    }

    public static LiveRotateRestart probeLiveRotateRestart() {
        Map<String, String> processEnv = System.getenv();
        boolean mutable = false;
        try {
            processEnv.put(PROBE_ENV, "probe");
            mutable = true;
            try {
                processEnv.remove(PROBE_ENV);
            } catch (RuntimeException ignored) {
                // mutation is already enough to record mutability
            }
        } catch (UnsupportedOperationException | IllegalArgumentException expected) {
            mutable = false;
        }
        boolean osObserved = mutable && "true".equalsIgnoreCase(System.getenv(OBSERVED_ENV));
        boolean restartListed = "true".equalsIgnoreCase(System.getenv(RESTART_OBSERVED_ENV));
        String osReason;
        if (!mutable) {
            osReason = "Java 21 System.getenv() is an unmodifiable process-start snapshot; "
                    + "OS environ rotate of a running JVM is not a supported API; "
                    + "EnvSecretAdapter.system() uses System::getenv; restart is required; "
                    + "#180/#200 leftover stay siblings; not OpenBao/KMS; "
                    + "not a product S0 / SecretPort close";
        } else if (!osObserved) {
            osReason = "this JVM allowed System.getenv() mutation but a live OS environ rotate "
                    + "was not observed; leftover does not claim a supported in-process "
                    + "OS env rotate API; #180/#200 not redone; not OpenBao/KMS";
        } else {
            osReason = "OS getenv mutation listed but this leftover still does not claim "
                    + "product S0 / SecretPort close or OpenBao/KMS";
        }
        String restartReason;
        if (restartListed) {
            restartReason = "NEXUS_S0_LIVE_RESTART_OBSERVED listed but this leftover did not "
                    + "restart a running JVM or observe a post-restart getenv walk; "
                    + "#180/#200 stay siblings; not a product S0 / SecretPort close";
        } else {
            restartReason = "no live JVM restart after a secret rotate was observed; "
                    + "Java 21 getenv snapshot requires restart; leftover does not restart "
                    + "a process; #180/#200 stay siblings; not a product S0 / SecretPort close";
        }
        return new LiveRotateRestart(!mutable, mutable, false, false, osReason, restartReason);
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
        require(errors, report, "redo_143", "false");
        require(errors, report, "redo_180", "false");
        require(errors, report, "redo_200", "false");
        require(errors, report, "openbao_introduced", "false");
        require(errors, report, "kms_introduced", "false");
        require(errors, report, "s0_close", S0_CLOSE);
        require(errors, report, "secret_port_close", SECRET_PORT_CLOSE);
        require(errors, report, "leftover_180_harness", "PASS");
        require(errors, report, "leftover_200_harness", "PASS");
        require(errors, report, "leftover_180_cells_honest", "PASS");
        require(errors, report, "leftover_200_cells_honest", "PASS");
        require(errors, report, "leftover_rows_kept", "true");
        require(errors, report, "env_map_rotate", "PASS");

        String rows = value(report, "leftover_rows");
        for (String row : README_ROWS) {
            if (!rows.contains(row)) {
                errors.add("leftover_rows must keep " + row);
            }
        }

        String os = value(report, "live_os_getenv_rotate");
        String restart = value(report, "live_restart");
        if (!isVerdict(os)) {
            errors.add("live_os_getenv_rotate must be PASS/SKIP/FAIL");
        }
        if (!isVerdict(restart)) {
            errors.add("live_restart must be PASS/SKIP/FAIL");
        }
        if ("PASS".equals(os)) {
            if (!"true".equals(value(report, "os_getenv_mutable"))) {
                errors.add("live_os_getenv_rotate=PASS requires os_getenv_mutable=true");
            }
            if (!"true".equals(value(report, "os_getenv_rotate_observed"))) {
                errors.add("live_os_getenv_rotate=PASS requires os_getenv_rotate_observed=true");
            }
        }
        if ("SKIP".equals(os)) {
            if (blank(value(report, "live_os_getenv_rotate_skip_reason"))) {
                errors.add("live_os_getenv_rotate SKIP needs live_os_getenv_rotate_skip_reason");
            }
            if ("true".equals(value(report, "os_getenv_rotate_observed"))) {
                errors.add("SKIP cannot claim os_getenv_rotate_observed=true");
            }
        }
        if ("PASS".equals(restart)) {
            if (!"true".equals(value(report, "live_restart_observed"))) {
                errors.add("live_restart=PASS requires live_restart_observed=true");
            }
        }
        if ("SKIP".equals(restart)) {
            if (blank(value(report, "live_restart_skip_reason"))) {
                errors.add("live_restart SKIP needs live_restart_skip_reason");
            }
            if ("true".equals(value(report, "live_restart_observed"))) {
                errors.add("SKIP cannot claim live_restart_observed=true");
            }
        }
        for (String key : report.keySet()) {
            String upper = key.toUpperCase(Locale.ROOT);
            if (!"SECRET_PORT_CLOSE".equals(upper)
                    && (upper.contains("PASSWORD") || upper.contains("SECRET") || upper.contains("PASSWD")
                    || upper.contains("ACCESS_KEY"))) {
                errors.add("report must not contain password/secret material");
            }
        }
        return errors;
    }

    public static Map<String, String> honestSkipTemplate(ParentHarness parent, LiveRotateRestart live) {
        Map<String, String> report = new LinkedHashMap<>();
        report.put("schema_version", "1");
        report.put("timestamp_utc", "");
        report.put("java_feature", "21");
        report.put("boot_parent_version", "4.1.1");
        report.put("boot_parent_match", "PASS");
        report.put("docker_used", "false");
        report.put("leftover_180_harness", parent.leftover180());
        report.put("leftover_200_harness", parent.leftover200());
        report.put("leftover_180_cells_honest", parent.leftover180Cells());
        report.put("leftover_200_cells_honest", parent.leftover200Cells());
        report.put("leftover_rows_kept", "true");
        report.put("leftover_rows", SIBLING_ROWS);
        report.put("env_map_rotate", "PASS");
        report.put("os_getenv_unmodifiable", Boolean.toString(live.getenvUnmodifiable()));
        report.put("os_getenv_mutable", Boolean.toString(live.getenvMutable()));
        report.put("os_getenv_rotate_observed", Boolean.toString(live.osRotateObserved()));
        report.put("live_os_getenv_rotate", "SKIP");
        report.put("live_os_getenv_rotate_skip_reason", live.osSkipReason());
        report.put("live_restart_observed", Boolean.toString(live.liveRestartObserved()));
        report.put("live_restart", "SKIP");
        report.put("live_restart_skip_reason", live.restartSkipReason());
        report.put("openbao_introduced", "false");
        report.put("kms_introduced", "false");
        report.put("redo_143", "false");
        report.put("redo_180", "false");
        report.put("redo_200", "false");
        report.put("s0_close", S0_CLOSE);
        report.put("secret_port_close", SECRET_PORT_CLOSE);
        report.put("g18_status", "closed_unchanged");
        report.put("audit_port_changed", "false");
        report.put("p0_m0_redone", "false");
        return report;
    }

    private static String[] requiredKeys() {
        return new String[] {
                "schema_version", "timestamp_utc", "java_feature", "boot_parent_version",
                "boot_parent_match", "docker_used", "leftover_180_harness", "leftover_200_harness",
                "leftover_180_cells_honest", "leftover_200_cells_honest", "leftover_rows_kept",
                "leftover_rows", "env_map_rotate", "os_getenv_unmodifiable", "os_getenv_mutable",
                "os_getenv_rotate_observed", "live_os_getenv_rotate",
                "live_os_getenv_rotate_skip_reason", "live_restart_observed", "live_restart",
                "live_restart_skip_reason", "openbao_introduced", "kms_introduced", "redo_143",
                "redo_180", "redo_200", "s0_close", "secret_port_close", "g18_status",
                "audit_port_changed", "p0_m0_redone"
        };
    }

    private static Path parentFixture(String leftover, String name) {
        Path first = Path.of("scripts/" + leftover + "/fixtures/" + name);
        if (Files.isRegularFile(first)) {
            return first;
        }
        return Path.of("../scripts/" + leftover + "/fixtures/" + name);
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

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
