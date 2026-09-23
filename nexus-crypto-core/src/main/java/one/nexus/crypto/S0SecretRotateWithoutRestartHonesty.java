package one.nexus.crypto;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * S0 leftover honesty inventory for rotate-without-restart / OS
 * {@code System.getenv()} startup-snapshot PASS/SKIP beyond #143 / #180.
 *
 * <p>#143 already proves a mutable env map is re-read on the next
 * {@code get}. #180 already is the getenv-snapshot honesty leftover.
 * This leftover does not redo either package. Product
 * {@link EnvSecretAdapter#system(CryptoSuite)} uses {@code System::getenv},
 * which on Java 21 is an unmodifiable process-start snapshot. An OS environ
 * rotate of a running JVM is not a supported API. Mutable env-map rotate
 * stays always-green {@code PASS}. OS getenv rotate is {@code PASS} only when
 * the process map is mutable <em>and</em> a live OS rotate was observed.
 * Otherwise honest {@code SKIP}. Does not introduce OpenBao / KMS. Does not
 * claim a product S0 / SecretPort close. G18 stays closed. P0 / M0 are not
 * redone.
 */
public final class S0SecretRotateWithoutRestartHonesty {
    public static final String S0_CLOSE = "NOT_CLAIMED";
    public static final String SECRET_PORT_CLOSE = "NOT_CLAIMED";
    public static final String BOTH_LEFTOVER_ROWS =
            "s0-secret-rotate-without-restart,s0-secret-getenv-snapshot-honesty";
    public static final String PROBE_ENV = "NEXUS_S0_ROTATE_HONESTY_PROBE";
    public static final String OBSERVED_ENV = "NEXUS_S0_OS_ENV_ROTATE_OBSERVED";

    public record EnvMapRotate(boolean pickedUp, String verdict) {}

    public record OsGetenvInventory(
            boolean getenvUnmodifiable,
            boolean getenvMutable,
            boolean osRotateObserved,
            String skipReason
    ) {
        public String osGetenvRotate() {
            return "SKIP";
        }
    }

    private S0SecretRotateWithoutRestartHonesty() {}

    /**
     * Thin in-process probe: {@link EnvSecretAdapter} re-reads a mutable map.
     * This is not a redo of the #143 suite or the #180 leftover.
     */
    public static EnvMapRotate probeEnvMapRotate() {
        Map<String, String> env = new HashMap<>();
        env.put("S3_ACCESS_KEY", "before-rotate-honesty");
        SecretPort secrets = new EnvSecretAdapter(
                CryptoSuite.INTERNATIONAL, SecretNames.envBindings(), env::get);
        String before = secrets.getUtf8(SecretNames.S3_ACCESS_KEY);
        env.put("S3_ACCESS_KEY", "after-rotate-honesty");
        String after = secrets.getUtf8(SecretNames.S3_ACCESS_KEY);
        boolean pickedUp = "before-rotate-honesty".equals(before) && "after-rotate-honesty".equals(after);
        return new EnvMapRotate(pickedUp, pickedUp ? "PASS" : "FAIL");
    }

    public static OsGetenvInventory probeOsGetenv() {
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
        boolean observed = mutable && "true".equalsIgnoreCase(System.getenv(OBSERVED_ENV));
        String reason;
        if (!mutable) {
            reason = "Java 21 System.getenv() is an unmodifiable process-start snapshot; "
                    + "OS environ rotate of a running JVM is not a supported API; "
                    + "EnvSecretAdapter.system() uses System::getenv; restart is required; "
                    + "#143/#180 leftover stay siblings; not OpenBao/KMS; "
                    + "not a product S0 / SecretPort close";
        } else if (!observed) {
            reason = "this JVM allowed System.getenv() mutation but a live OS environ rotate "
                    + "was not observed; leftover does not claim a supported in-process "
                    + "OS env rotate API; #143/#180 not redone; not OpenBao/KMS";
        } else {
            reason = "OS getenv mutation listed but this leftover still does not claim "
                    + "product S0 / SecretPort close or OpenBao/KMS";
        }
        return new OsGetenvInventory(!mutable, mutable, observed, reason);
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
        require(errors, report, "openbao_introduced", "false");
        require(errors, report, "kms_introduced", "false");
        require(errors, report, "s0_close", S0_CLOSE);
        require(errors, report, "secret_port_close", SECRET_PORT_CLOSE);
        require(errors, report, "env_map_rotate", "PASS");

        String rows = value(report, "leftover_rows_kept");
        if (!rows.contains("s0-secret-rotate-without-restart")) {
            errors.add("leftover_rows_kept must keep s0-secret-rotate-without-restart");
        }
        if (!rows.contains("s0-secret-getenv-snapshot-honesty")) {
            errors.add("leftover_rows_kept must keep s0-secret-getenv-snapshot-honesty");
        }

        String os = value(report, "os_getenv_rotate");
        if (!isVerdict(os)) {
            errors.add("os_getenv_rotate must be PASS/SKIP/FAIL");
        }
        if ("PASS".equals(os)) {
            if (!"true".equals(value(report, "os_getenv_mutable"))) {
                errors.add("os_getenv_rotate=PASS requires os_getenv_mutable=true");
            }
            if (!"true".equals(value(report, "os_getenv_rotate_observed"))) {
                errors.add("os_getenv_rotate=PASS requires os_getenv_rotate_observed=true");
            }
        }
        if ("SKIP".equals(os)) {
            if (blank(value(report, "os_getenv_skip_reason"))) {
                errors.add("os_getenv_rotate SKIP needs os_getenv_skip_reason");
            }
            if ("true".equals(value(report, "os_getenv_rotate_observed"))) {
                errors.add("SKIP cannot claim os_getenv_rotate_observed=true");
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

    public static Map<String, String> honestSkipTemplate(EnvMapRotate envMap, OsGetenvInventory os) {
        Map<String, String> report = new LinkedHashMap<>();
        report.put("schema_version", "1");
        report.put("timestamp_utc", "");
        report.put("java_feature", "21");
        report.put("boot_parent_version", "4.1.1");
        report.put("boot_parent_match", "PASS");
        report.put("docker_used", "false");
        report.put("env_map_rotate", envMap.verdict());
        report.put("os_getenv_unmodifiable", Boolean.toString(os.getenvUnmodifiable()));
        report.put("os_getenv_mutable", Boolean.toString(os.getenvMutable()));
        report.put("os_getenv_rotate_observed", Boolean.toString(os.osRotateObserved()));
        report.put("os_getenv_rotate", "SKIP");
        report.put("os_getenv_skip_reason", os.skipReason());
        report.put("leftover_rows_kept", BOTH_LEFTOVER_ROWS);
        report.put("openbao_introduced", "false");
        report.put("kms_introduced", "false");
        report.put("redo_143", "false");
        report.put("redo_180", "false");
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
                "boot_parent_match", "docker_used", "env_map_rotate",
                "os_getenv_unmodifiable", "os_getenv_mutable", "os_getenv_rotate_observed",
                "os_getenv_rotate", "os_getenv_skip_reason", "leftover_rows_kept",
                "openbao_introduced", "kms_introduced", "redo_143", "redo_180",
                "s0_close", "secret_port_close", "g18_status", "audit_port_changed",
                "p0_m0_redone"
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

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
