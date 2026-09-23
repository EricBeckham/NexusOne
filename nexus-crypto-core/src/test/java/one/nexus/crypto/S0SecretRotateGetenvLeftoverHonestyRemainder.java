package one.nexus.crypto;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import one.nexus.crypto.S0SecretRotateGetenvLeftoverHonesty.LiveRotateRestart;
import one.nexus.crypto.S0SecretRotateGetenvLeftoverHonesty.LocalThenEnvHarness;

/**
 * S0 leftover honesty remainder <em>beyond #241 / #223</em>: SecretPort
 * rotate / getenv / {@link LocalThenEnvSecretPort} leftover honesty PASS/SKIP.
 * Parent leftover honesty fixtures stay always-green {@code PASS}. Live OS
 * getenv rotate, live JVM restart, live {@code LocalThenEnvSecretPort.system()}
 * getenv, JDBC runtime SecretPort, and S3/MinIO client rebuild stay
 * {@code SKIP} unless that walk was observed.
 *
 * <p>Does not redo #143 / #180 / #200 / #223 / #241 product code. Does not
 * introduce OpenBao / KMS. Does not claim a product S0 / SecretPort close.
 * G18 stays closed. No P0/M0 redo. {@code AuditPort} same-TX rules stay
 * untouched.
 */
public final class S0SecretRotateGetenvLeftoverHonestyRemainder {
    public static final String S0_CLOSE = "NOT_CLAIMED";
    public static final String SECRET_PORT_CLOSE = "NOT_CLAIMED";

    public static final String ROW_143 = "s0-secret-rotate-without-restart";
    public static final String ROW_180 = "s0-secret-getenv-snapshot-honesty";
    public static final String ROW_200 = "s0-secret-rotate-without-restart-honesty";
    public static final String ROW_223 = "s0-secret-rotate-getenv-remainder-honesty";
    public static final String ROW_241 = "s0-secret-rotate-getenv-leftover-honesty";
    public static final String ROW_THIS = "s0-secret-rotate-getenv-leftover-honesty-remainder";
    public static final String ROW_G17_SOD_REM = "g17-separation-leftover-honesty-remainder";
    public static final String ROW_G9_LEFTOVER = "g9-leftover-honesty-remainder";
    public static final String ROW_G2_REM = "g2-h5-every-page-form-write-report-remainder-honesty";
    public static final String ROW_G15_REM = "g15-h5-pkce-miniprogram-remainder-honesty";
    public static final String ROW_ARM_REM = "linux-arm-profile-remainder-honesty";
    public static final String ROW_G4_PKCE_REM = "g4-hot-purge-staging-pkce-remainder-honesty";
    public static final String ROW_G10_OBJ = "g10-file-object-reconcile-remainder-honesty";

    public static final List<String> README_ROWS = List.of(
            ROW_143,
            ROW_180,
            ROW_200,
            ROW_223,
            ROW_241,
            ROW_THIS,
            ROW_G17_SOD_REM,
            ROW_G9_LEFTOVER,
            ROW_G15_REM,
            "g15-h5-pkce-browser",
            "g15-miniprogram-webview",
            "g15-wx-dt-prod-gap-honesty",
            "s0s1-live-redis-minio-isolation-honesty",
            "g7-withdraw-browser-evidence-honesty",
            "g4-hot-purge-staging-leftover-honesty",
            ROW_G4_PKCE_REM,
            "g9-live-multi-jvm-replica-remainder-honesty",
            "g3-outbox-consume-idempotent-honesty",
            "g10-leftover-honesty-remainder",
            ROW_G10_OBJ,
            "g2-report-physical-dump-honesty",
            "g2-h5-every-page-form-write-remainder-honesty",
            ROW_G2_REM,
            "g6-recovery-port-dry-run-remainder-honesty",
            "g9-sentinel-cluster-remainder-honesty",
            "g9-sentinel-cluster-honesty",
            "g9-live-multi-jvm-replica",
            "g9-redis-down-invalidate-remainder",
            "g9-redis-down-invalidate-remainder-honesty",
            "g9-redis-down-invalidate-honesty",
            "g17-mfa-recovery-vue-otp-honesty",
            "g17-mfa-recovery-email-honesty",
            "g17-gm-tls-nginx-handshake-honesty",
            "g2-ops-pg-dump-honesty",
            "g2-h5-every-page-form-write-honesty",
            "g4-export-wizard-deny-honesty",
            "g10-leftover-honesty",
            "g5-mid-publish-failure-honesty",
            "g5-publishhook-ddl-honesty",
            "g3-outbox-consume-crash-honesty",
            "g1-queryast-boundsql-honesty",
            "g1-queryast-export-honesty",
            "g15-wx-dt-native-bridge-honesty",
            "g15-wx-dt-native-bridge-remainder",
            "linux-arm-profile-honesty",
            "linux-arm-profile-leftover-honesty",
            ROW_ARM_REM,
            "g4-p1-staging-audit-events-honesty",
            "g6-recovery-port-dry-run-honesty",
            "g17-separation-honesty",
            "g17-separation-leftover-honesty",
            "g4-hot-purge-90d-honesty",
            "g1-fieldpolicy-mask-second-auth-honesty",
            "g10-identity-drift-honesty",
            "g15-native-bridge-honesty",
            "g4-pkce-spa-verifier-honesty",
            "g4-pkce-spa-verifier-remainder-honesty",
            "g5-browser-failed-publish",
            "g7-withdraw-browser-honesty",
            "g7-query-expense-travel-honesty",
            "g7-query-expense-travel-honesty-remainder",
            "g1-fieldpolicy-mask-second-auth-remainder-honesty",
            "g17-mfa-recovery-vue-otp-remainder-honesty",
            "g5-publishhook-ddl-remainder-honesty",
            "g2-form-write-honesty",
            "g10-file-reconcile-honesty",
            "g7-withdraw-browser");

    public static final String SIBLING_ROWS = String.join(",", README_ROWS);

    public record ParentHarness(
            String leftover180,
            String leftover200,
            String leftover223,
            String leftover241,
            String leftover180Cells,
            String leftover200Cells,
            String leftover223Cells,
            String leftover241Cells) {}

    private S0SecretRotateGetenvLeftoverHonestyRemainder() {}

    /**
     * Parent leftover harness still verifies. This is not a redo of #180 /
     * #200 / #223 / #241 product code — it only reads the committed honesty
     * fixtures.
     */
    public static ParentHarness proveParentHarness() {
        S0SecretRotateGetenvLeftoverHonesty.ParentHarness leftover =
                S0SecretRotateGetenvLeftoverHonesty.proveParentHarness();
        S0SecretRotateGetenvLeftoverHonesty.verify(
                S0SecretRotateGetenvLeftoverHonesty.parseReport(parentFixture(ROW_241, "honest-skip.env")));
        Map<String, String> leftover241 = S0SecretRotateGetenvLeftoverHonesty.parseReport(
                parentFixture(ROW_241, "honest-skip.env"));
        if (!"PASS".equals(leftover241.get("leftover_180_harness"))
                || !"PASS".equals(leftover241.get("leftover_200_harness"))
                || !"PASS".equals(leftover241.get("leftover_223_harness"))
                || !"PASS".equals(leftover241.get("env_map_rotate"))
                || !"PASS".equals(leftover241.get("local_then_env_map_rotate"))
                || !"PASS".equals(leftover241.get("local_then_env_overlay_wins"))
                || !"SKIP".equals(leftover241.get("live_os_getenv_rotate"))
                || !"SKIP".equals(leftover241.get("live_restart"))
                || !"SKIP".equals(leftover241.get("live_local_then_env_system_getenv"))
                || !"SKIP".equals(leftover241.get("live_jdbc_runtime_secretport"))
                || !"SKIP".equals(leftover241.get("live_s3_client_rotate"))
                || !"NOT_CLAIMED".equals(leftover241.get("s0_close"))
                || !"NOT_CLAIMED".equals(leftover241.get("secret_port_close"))
                || !"false".equals(leftover241.get("redo_143"))
                || !"false".equals(leftover241.get("redo_180"))
                || !"false".equals(leftover241.get("redo_200"))
                || !"false".equals(leftover241.get("redo_223"))
                || !"false".equals(leftover241.get("openbao_introduced"))
                || !"false".equals(leftover241.get("kms_introduced"))) {
            throw new IllegalStateException("#241 leftover cells are no longer honest");
        }
        return new ParentHarness(
                leftover.leftover180(),
                leftover.leftover200(),
                leftover.leftover223(),
                "PASS",
                leftover.leftover180Cells(),
                leftover.leftover200Cells(),
                leftover.leftover223Cells(),
                "PASS");
    }

    public static LocalThenEnvHarness proveLocalThenEnv() {
        return S0SecretRotateGetenvLeftoverHonesty.proveLocalThenEnv();
    }

    public static LiveRotateRestart probeLiveRotateRestart() {
        return S0SecretRotateGetenvLeftoverHonesty.probeLiveRotateRestart();
    }

    public static Map<String, String> parseReport(Path file) {
        return S0SecretRotateGetenvLeftoverHonesty.parseReport(file);
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
        require(errors, report, "leftover_241_harness", "PASS");
        require(errors, report, "leftover_241_cells_honest", "PASS");
        require(errors, report, "leftover_259_kept", "true");
        require(errors, report, "leftover_249_kept", "true");
        require(errors, report, "leftover_254_kept", "true");
        require(errors, report, "leftover_240_kept", "true");
        require(errors, report, "leftover_236_kept", "true");
        require(errors, report, "leftover_251_kept", "true");
        require(errors, report, "leftover_243_kept", "true");
        require(errors, report, "leftover_241_kept", "true");
        require(errors, report, "redo_241", "false");
        require(errors, report, "s0_close", S0_CLOSE);
        require(errors, report, "secret_port_close", SECRET_PORT_CLOSE);
        require(errors, report, "g18_status", "closed_unchanged");
        require(errors, report, "audit_port_changed", "false");
        require(errors, report, "p0_m0_redone", "false");

        String rows = value(report, "leftover_rows");
        for (String row : README_ROWS) {
            if (!rows.contains(row)) {
                errors.add("leftover_rows must keep " + row);
            }
        }

        Map<String, String> subset = new LinkedHashMap<>(report);
        subset.putIfAbsent("leftover_223_harness", value(report, "leftover_223_harness"));
        errors.addAll(S0SecretRotateGetenvLeftoverHonesty.verifyErrors(subsetForParent(report)));
        return errors;
    }

    public static Map<String, String> honestSkipTemplate(
            ParentHarness parent, LocalThenEnvHarness lte, LiveRotateRestart live) {
        Map<String, String> report = S0SecretRotateGetenvLeftoverHonesty.honestSkipTemplate(
                new S0SecretRotateGetenvLeftoverHonesty.ParentHarness(
                        parent.leftover180(),
                        parent.leftover200(),
                        parent.leftover223(),
                        parent.leftover180Cells(),
                        parent.leftover200Cells(),
                        parent.leftover223Cells()),
                lte,
                live);
        report.put("leftover_241_harness", parent.leftover241());
        report.put("leftover_241_cells_honest", parent.leftover241Cells());
        report.put("leftover_rows", SIBLING_ROWS);
        report.put("leftover_259_kept", "true");
        report.put("leftover_249_kept", "true");
        report.put("leftover_254_kept", "true");
        report.put("leftover_240_kept", "true");
        report.put("leftover_236_kept", "true");
        report.put("leftover_251_kept", "true");
        report.put("leftover_243_kept", "true");
        report.put("leftover_241_kept", "true");
        report.put("redo_241", "false");
        report.put("s0_close", S0_CLOSE);
        report.put("secret_port_close", SECRET_PORT_CLOSE);
        return report;
    }

    private static Map<String, String> subsetForParent(Map<String, String> report) {
        Map<String, String> subset = new LinkedHashMap<>(report);
        if (!subset.containsKey("leftover_rows") || !subset.get("leftover_rows").contains(ROW_241)) {
            subset.put("leftover_rows", SIBLING_ROWS);
        }
        return subset;
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

    private static String value(Map<String, String> report, String key) {
        String raw = report.get(key);
        return raw == null ? "" : raw;
    }
}
