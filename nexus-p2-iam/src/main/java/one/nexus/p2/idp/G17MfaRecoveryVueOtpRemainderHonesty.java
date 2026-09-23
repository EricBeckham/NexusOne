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
 * G17 leftover honesty remainder <em>beyond #205 / #172 / #158</em>: MFA
 * recovery email / Vue OTP writeback PASS/SKIP. Parent leftover harness
 * fixtures stay always-green {@code PASS}. Live email delivery, live
 * Keycloak {@code recovery-authn-codes}, and live Vue otp write-back stay
 * {@code SKIP} unless those walks were actually observed.
 *
 * <p>Does not redo #96 / #118 / #133 / #154 / #158 / #172 / #205 product
 * code. Does not write otp / recovery-authn-codes from Vue. Does not claim
 * product G17 / 等保 / 密评. G18 stays closed. No P0/M0 redo.
 * {@code AuditPort} same-TX rules stay untouched.
 */
public final class G17MfaRecoveryVueOtpRemainderHonesty {
    public static final String G17_CLOSE = "NOT_CLAIMED";

    public static final String ROW_172 = "g17-mfa-recovery-email-honesty";
    public static final String ROW_158 = "g17-vue-otp-writeback";
    public static final String ROW_154 = "g17-admin-mfa-recovery-email";
    public static final String ROW_205 = "g17-mfa-recovery-vue-otp-honesty";
    public static final String ROW_THIS = "g17-mfa-recovery-vue-otp-remainder-honesty";
    public static final String ROW_S0_REM = "s0-secret-rotate-getenv-remainder-honesty";
    public static final String ROW_S0_200 = "s0-secret-rotate-without-restart-honesty";
    public static final String ROW_S0_180 = "s0-secret-getenv-snapshot-honesty";
    public static final String ROW_S0_143 = "s0-secret-rotate-without-restart";
    public static final String ROW_S0S1 = "s0s1-live-redis-minio-isolation-honesty";
    public static final String ROW_G4_REM = "g4-pkce-spa-verifier-remainder-honesty";
    public static final String ROW_G4_177 = "g4-pkce-spa-verifier-honesty";
    public static final String ROW_G17_SOD = "g17-separation-honesty";
    public static final String ROW_G17_SOD_LEFTOVER = "g17-separation-leftover-honesty";
    public static final String ROW_G15_REM = "g15-wx-dt-native-bridge-remainder";
    public static final String ROW_G15 = "g15-wx-dt-native-bridge-honesty";
    public static final String ROW_G15_BRIDGE = "g15-native-bridge-honesty";
    public static final String ROW_G15_GAP = "g15-wx-dt-prod-gap-honesty";
    public static final String ROW_G1_EXPORT = "g1-queryast-export-honesty";
    public static final String ROW_G1 = "g1-queryast-boundsql-honesty";
    public static final String ROW_G1_GUARD = "g1-queryast-boundsql-guard";
    public static final String ROW_G17_GMTLS = "g17-gm-tls-nginx-handshake-honesty";
    public static final String ROW_G5_HOOK = "g5-publishhook-ddl-honesty";
    public static final String ROW_G5 = "g5-mid-publish-failure-honesty";
    public static final String ROW_G5_BROWSER = "g5-browser-failed-publish";
    public static final String ROW_G9_SENTINEL = "g9-sentinel-cluster-remainder-honesty";
    public static final String ROW_G9_JVM_REM = "g9-live-multi-jvm-replica-remainder-honesty";
    public static final String ROW_G9_REDIS_REM = "g9-redis-down-invalidate-remainder";
    public static final String ROW_G9_REDIS_REM_H = "g9-redis-down-invalidate-remainder-honesty";
    public static final String ROW_G9_REDIS = "g9-redis-down-invalidate-honesty";
    public static final String ROW_G2_OPS = "g2-ops-pg-dump-honesty";
    public static final String ROW_G2_REPORT = "g2-report-physical-dump-honesty";
    public static final String ROW_G2_H5 = "g2-h5-every-page-form-write-honesty";
    public static final String ROW_G2_H5_REM = "g2-h5-every-page-form-write-remainder-honesty";
    public static final String ROW_G2_FORM = "g2-form-write-honesty";
    public static final String ROW_G4 = "g4-export-wizard-deny-honesty";
    public static final String ROW_G10 = "g10-leftover-honesty";
    public static final String ROW_G10_REM = "g10-leftover-honesty-remainder";
    public static final String ROW_G10_ID = "g10-identity-drift-honesty";
    public static final String ROW_G10_FILE = "g10-file-reconcile-honesty";
    public static final String ROW_G3 = "g3-outbox-consume-crash-honesty";
    public static final String ROW_G3_HONESTY = "g3-outbox-consume-idempotent-honesty";
    public static final String ROW_G3_IDEM = "g3-outbox-consume-idempotent";
    public static final String ROW_G3_CRASH = "g3-claim-crash-window";
    public static final String ROW_ARM = "linux-arm-profile-leftover-honesty";
    public static final String ROW_G4_P1 = "g4-p1-staging-audit-events-honesty";
    public static final String ROW_G6 = "g6-recovery-port-dry-run-honesty";
    public static final String ROW_G6_REM = "g6-recovery-port-dry-run-remainder-honesty";
    public static final String ROW_G4_PURGE = "g4-hot-purge-90d-honesty";
    public static final String ROW_G4_STAGING = "g4-hot-purge-staging-leftover-honesty";
    public static final String ROW_G1_MASK = "g1-fieldpolicy-mask-second-auth-honesty";
    public static final String ROW_G1_MASK_REM = "g1-fieldpolicy-mask-second-auth-remainder-honesty";
    public static final String ROW_G7_WITHDRAW = "g7-withdraw-browser-honesty";
    public static final String ROW_G7_EVIDENCE = "g7-withdraw-browser-evidence-honesty";
    public static final String ROW_G7_QUERY = "g7-query-expense-travel-honesty";
    public static final String ROW_G7_QUERY_REM = "g7-query-expense-travel-honesty-remainder";

    public static final List<String> README_ROWS = List.of(
            ROW_172,
            ROW_158,
            ROW_154,
            ROW_205,
            ROW_THIS,
            ROW_S0_REM,
            ROW_S0_200,
            ROW_S0_180,
            ROW_S0_143,
            ROW_S0S1,
            ROW_G4_REM,
            ROW_G4_177,
            ROW_G17_SOD,
            ROW_G17_SOD_LEFTOVER,
            ROW_G15_REM,
            ROW_G15,
            ROW_G15_BRIDGE,
            ROW_G15_GAP,
            ROW_G1_EXPORT,
            ROW_G1,
            ROW_G1_GUARD,
            ROW_G17_GMTLS,
            ROW_G5_HOOK,
            ROW_G5,
            ROW_G5_BROWSER,
            ROW_G9_SENTINEL,
            ROW_G9_JVM_REM,
            ROW_G9_REDIS_REM,
            ROW_G9_REDIS_REM_H,
            ROW_G9_REDIS,
            ROW_G2_OPS,
            ROW_G2_REPORT,
            ROW_G2_H5,
            ROW_G2_H5_REM,
            ROW_G2_FORM,
            ROW_G4,
            ROW_G10,
            ROW_G10_REM,
            ROW_G10_ID,
            ROW_G10_FILE,
            ROW_G3,
            ROW_G3_HONESTY,
            ROW_G3_IDEM,
            ROW_G3_CRASH,
            ROW_ARM,
            ROW_G4_P1,
            ROW_G6,
            ROW_G6_REM,
            ROW_G4_PURGE,
            ROW_G4_STAGING,
            ROW_G1_MASK,
            ROW_G1_MASK_REM,
            ROW_G7_WITHDRAW,
            ROW_G7_EVIDENCE,
            ROW_G7_QUERY,
            ROW_G7_QUERY_REM
    );

    public static final String SIBLING_ROWS = String.join(",", README_ROWS);

    public record ParentHarness(
            String leftover205,
            String leftover172,
            String leftover158,
            String leftover205Cells,
            String leftover172Cells,
            String leftover158Cells
    ) {}

    public record LiveInventory(
            boolean smtpEnabled,
            boolean smtpHostConfigured,
            boolean keycloakReachable,
            String issuer,
            String emailSkipReason,
            String recoverySkipReason,
            String writebackSkipReason
    ) {
        public String emailDelivery() {
            return "SKIP";
        }

        public String recoveryLive() {
            return "SKIP";
        }

        public String vueOtpWriteback() {
            return "SKIP";
        }
    }

    private G17MfaRecoveryVueOtpRemainderHonesty() {}

    /**
     * Parent leftover harness still verifies. This is not a redo of #205 /
     * #172 / #158 product code — it only reads the committed honesty fixtures.
     */
    public static ParentHarness proveParentHarness() {
        G17MfaRecoveryEmailHonesty.verify(
                G17MfaRecoveryEmailHonesty.parseReport(parentFixture(ROW_172, "honest-skip.env")));
        Map<String, String> leftover158 = parseReport(parentFixture(ROW_158, "honest-skip.env"));
        if (!"SKIP".equals(leftover158.get("vue_otp_writeback"))
                || !"NOT_CLAIMED".equals(leftover158.get("g17_close"))
                || !"false".equals(leftover158.get("vue_confirm_writes_keycloak"))
                || !"false".equals(leftover158.get("product_writeback_endpoint"))
                || !"false".equals(leftover158.get("otp_credential_stored"))) {
            throw new IllegalStateException("#158 leftover cells are no longer honest");
        }
        Map<String, String> leftover205 = parseReport(parentFixture(ROW_205, "honest-skip.env"));
        if (!"PASS".equals(leftover205.get("leftover_172_harness"))
                || !"PASS".equals(leftover205.get("leftover_158_harness"))
                || !"SKIP".equals(leftover205.get("email_delivery"))
                || !"SKIP".equals(leftover205.get("recovery_live"))
                || !"SKIP".equals(leftover205.get("vue_otp_writeback"))
                || !"NOT_CLAIMED".equals(leftover205.get("g17_close"))
                || !"false".equals(leftover205.get("redo_172"))
                || !"false".equals(leftover205.get("redo_158"))) {
            throw new IllegalStateException("#205 leftover cells are no longer honest");
        }
        return new ParentHarness("PASS", "PASS", "PASS", "PASS", "PASS", "PASS");
    }

    public static LiveInventory probeLive() {
        EmailMfaDeliveryProbe.Result probe = EmailMfaDeliveryProbe.probe(smtpEnabledFromEnv());
        boolean reachable = probe.keycloakReachable();
        String emailReason;
        if (!probe.smtpEnabled()) {
            emailReason = EmailMfaDeliveryProbe.SMTP_DISABLED;
        } else if (!probe.smtpHostConfigured()) {
            emailReason = EmailMfaDeliveryProbe.EMAIL_OTP_NOT_DELIVERED;
        } else if (!reachable) {
            emailReason = "keycloak_issuer_unreachable";
        } else {
            emailReason = "mailbox_not_observed";
        }
        String recoveryReason = reachable
                ? "recovery_authn_codes_not_enrolled"
                : "keycloak_issuer_unreachable";
        String writebackReason = reachable
                ? "product_vue_writeback_endpoint_absent"
                : "keycloak_issuer_unreachable";
        if ("true".equalsIgnoreCase(env("NEXUS_G17_MAILBOX_OBSERVED"))
                || "true".equalsIgnoreCase(env("NEXUS_G17_RECOVERY_CONSUMED"))
                || "true".equalsIgnoreCase(env("NEXUS_G17_OTP_STORED"))) {
            emailReason = emailReason + "; listed observation env is not a live mailbox/IdP walk";
            recoveryReason = recoveryReason + "; listed observation env is not a live recovery-authn-code walk";
            writebackReason = writebackReason + "; listed observation env is not a live Vue write-back";
        }
        return new LiveInventory(
                probe.smtpEnabled(),
                probe.smtpHostConfigured(),
                reachable,
                probe.issuer() == null ? "" : probe.issuer(),
                emailReason,
                recoveryReason,
                writebackReason);
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
        require(errors, report, "dengbao_claimed", "false");
        require(errors, report, "mipin_claimed", "false");
        require(errors, report, "hsm_claimed", "false");
        require(errors, report, "g17_close", G17_CLOSE);
        require(errors, report, "vue_writes_keycloak_otp", "false");
        require(errors, report, "g18_status", "closed_unchanged");
        require(errors, report, "audit_port_changed", "false");
        require(errors, report, "p0_m0_redone", "false");
        require(errors, report, "leftover_205_harness", "PASS");
        require(errors, report, "leftover_172_harness", "PASS");
        require(errors, report, "leftover_158_harness", "PASS");
        require(errors, report, "leftover_205_cells_honest", "PASS");
        require(errors, report, "leftover_172_cells_honest", "PASS");
        require(errors, report, "leftover_158_cells_honest", "PASS");
        require(errors, report, "leftover_rows_kept", "true");
        require(errors, report, "redo_158", "false");
        require(errors, report, "redo_172", "false");
        require(errors, report, "redo_205", "false");
        require(errors, report, "redo_96", "false");
        require(errors, report, "redo_118", "false");
        require(errors, report, "redo_133", "false");
        require(errors, report, "redo_154", "false");

        String rows = value(report, "leftover_rows");
        for (String row : README_ROWS) {
            if (!rows.contains(row)) {
                errors.add("leftover_rows must keep " + row);
            }
        }

        String email = value(report, "email_delivery");
        String recovery = value(report, "recovery_live");
        String writeback = value(report, "vue_otp_writeback");
        if (!isVerdict(email)) {
            errors.add("email_delivery must be PASS/SKIP/FAIL");
        }
        if (!isVerdict(recovery)) {
            errors.add("recovery_live must be PASS/SKIP/FAIL");
        }
        if (!isVerdict(writeback)) {
            errors.add("vue_otp_writeback must be PASS/SKIP/FAIL");
        }
        if ("PASS".equals(email)) {
            if (!"true".equals(value(report, "smtp_enabled"))) {
                errors.add("email_delivery=PASS requires smtp_enabled=true");
            }
            if (!"true".equals(value(report, "smtp_host_configured"))) {
                errors.add("email_delivery=PASS requires smtp_host_configured=true");
            }
            if (!"true".equals(value(report, "keycloak_issuer_reachable"))) {
                errors.add("email_delivery=PASS requires keycloak_issuer_reachable=true");
            }
            if (!"true".equals(value(report, "mailbox_observed"))) {
                errors.add("email_delivery=PASS requires mailbox_observed=true");
            }
        }
        if ("SKIP".equals(email)) {
            if (blank(value(report, "email_delivery_skip_reason"))) {
                errors.add("email_delivery SKIP needs email_delivery_skip_reason");
            }
            if ("true".equals(value(report, "mailbox_observed"))) {
                errors.add("SKIP cannot claim mailbox_observed=true");
            }
        }
        if ("PASS".equals(recovery)) {
            if (!"true".equals(value(report, "keycloak_issuer_reachable"))) {
                errors.add("recovery_live=PASS requires keycloak_issuer_reachable=true");
            }
            if (!"PASS".equals(value(report, "oidc_discovery"))) {
                errors.add("recovery_live=PASS requires oidc_discovery=PASS");
            }
            if (!"true".equals(value(report, "recovery_authn_codes_enrolled"))) {
                errors.add("recovery_live=PASS requires recovery_authn_codes_enrolled=true");
            }
            if (!"true".equals(value(report, "recovery_code_consumed_live"))) {
                errors.add("recovery_live=PASS requires recovery_code_consumed_live=true");
            }
        }
        if ("SKIP".equals(recovery)) {
            if (blank(value(report, "recovery_live_skip_reason"))) {
                errors.add("recovery_live SKIP needs recovery_live_skip_reason");
            }
            if ("true".equals(value(report, "recovery_code_consumed_live"))) {
                errors.add("SKIP cannot claim recovery_code_consumed_live=true");
            }
        }
        if ("PASS".equals(writeback)) {
            if (!"true".equals(value(report, "keycloak_issuer_reachable"))) {
                errors.add("vue_otp_writeback=PASS requires keycloak_issuer_reachable=true");
            }
            if (!"PASS".equals(value(report, "oidc_discovery"))) {
                errors.add("vue_otp_writeback=PASS requires oidc_discovery=PASS");
            }
            if (!"true".equals(value(report, "product_writeback_endpoint"))) {
                errors.add("vue_otp_writeback=PASS requires product_writeback_endpoint=true");
            }
            if (!"true".equals(value(report, "otp_credential_stored"))) {
                errors.add("vue_otp_writeback=PASS requires otp_credential_stored=true");
            }
        }
        if ("SKIP".equals(writeback)) {
            if (blank(value(report, "vue_otp_writeback_skip_reason"))) {
                errors.add("vue_otp_writeback SKIP needs vue_otp_writeback_skip_reason");
            }
            if ("true".equals(value(report, "otp_credential_stored"))) {
                errors.add("SKIP cannot claim otp_credential_stored=true");
            }
        }
        for (String key : report.keySet()) {
            String upper = key.toUpperCase(Locale.ROOT);
            if (upper.contains("PASSWORD") || upper.contains("SECRET") || upper.contains("PASSWD")
                    || upper.contains("TOTPSECRET")) {
                errors.add("report must not contain password/secret material");
            }
        }
        return errors;
    }

    public static Map<String, String> honestSkipTemplate(ParentHarness parent, LiveInventory live) {
        Map<String, String> report = new LinkedHashMap<>();
        report.put("schema_version", "1");
        report.put("timestamp_utc", "");
        report.put("java_feature", "21");
        report.put("boot_parent_version", "4.1.1");
        report.put("boot_parent_match", "PASS");
        report.put("docker_used", "false");
        report.put("leftover_205_harness", parent.leftover205());
        report.put("leftover_172_harness", parent.leftover172());
        report.put("leftover_158_harness", parent.leftover158());
        report.put("leftover_205_cells_honest", parent.leftover205Cells());
        report.put("leftover_172_cells_honest", parent.leftover172Cells());
        report.put("leftover_158_cells_honest", parent.leftover158Cells());
        report.put("leftover_rows_kept", "true");
        report.put("leftover_rows", SIBLING_ROWS);
        report.put("email_delivery", "SKIP");
        report.put("email_delivery_skip_reason", live.emailSkipReason());
        report.put("mailbox_observed", "false");
        report.put("smtp_enabled", Boolean.toString(live.smtpEnabled()));
        report.put("smtp_host_configured", Boolean.toString(live.smtpHostConfigured()));
        report.put("recovery_live", "SKIP");
        report.put("recovery_live_skip_reason", live.recoverySkipReason());
        report.put("recovery_authn_codes_enrolled", "false");
        report.put("recovery_code_consumed_live", "false");
        report.put("vue_otp_writeback", "SKIP");
        report.put("vue_otp_writeback_skip_reason", live.writebackSkipReason());
        report.put("product_writeback_endpoint", "false");
        report.put("otp_credential_stored", "false");
        report.put("keycloak_issuer_reachable", Boolean.toString(live.keycloakReachable()));
        report.put("oidc_discovery", live.keycloakReachable() ? "PASS" : "SKIP");
        report.put("vue_writes_keycloak_otp", "false");
        report.put("dengbao_claimed", "false");
        report.put("mipin_claimed", "false");
        report.put("hsm_claimed", "false");
        report.put("g17_close", G17_CLOSE);
        report.put("g18_status", "closed_unchanged");
        report.put("audit_port_changed", "false");
        report.put("p0_m0_redone", "false");
        report.put("redo_96", "false");
        report.put("redo_118", "false");
        report.put("redo_133", "false");
        report.put("redo_154", "false");
        report.put("redo_158", "false");
        report.put("redo_172", "false");
        report.put("redo_205", "false");
        return report;
    }

    private static String[] requiredKeys() {
        return new String[] {
                "schema_version", "timestamp_utc", "java_feature", "boot_parent_version",
                "boot_parent_match", "docker_used", "leftover_205_harness",
                "leftover_172_harness", "leftover_158_harness", "leftover_205_cells_honest",
                "leftover_172_cells_honest", "leftover_158_cells_honest", "leftover_rows_kept",
                "leftover_rows", "email_delivery", "email_delivery_skip_reason",
                "mailbox_observed", "smtp_enabled", "smtp_host_configured", "recovery_live",
                "recovery_live_skip_reason", "recovery_authn_codes_enrolled",
                "recovery_code_consumed_live", "vue_otp_writeback",
                "vue_otp_writeback_skip_reason", "product_writeback_endpoint",
                "otp_credential_stored", "keycloak_issuer_reachable", "oidc_discovery",
                "vue_writes_keycloak_otp", "dengbao_claimed", "mipin_claimed", "hsm_claimed",
                "g17_close", "g18_status", "audit_port_changed", "p0_m0_redone",
                "redo_96", "redo_118", "redo_133", "redo_154", "redo_158", "redo_172",
                "redo_205"
        };
    }

    private static Path parentFixture(String leftover, String name) {
        Path first = Path.of("scripts/" + leftover + "/fixtures/" + name);
        if (Files.isRegularFile(first)) {
            return first;
        }
        return Path.of("../scripts/" + leftover + "/fixtures/" + name);
    }

    static boolean smtpEnabledFromEnv() {
        String raw = env("NEXUS_IAM_SMTP_ENABLED");
        return "true".equalsIgnoreCase(raw) || "1".equals(raw);
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
