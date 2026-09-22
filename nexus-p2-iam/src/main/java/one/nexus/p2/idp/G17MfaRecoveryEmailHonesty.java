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
 * G17 leftover honesty inventory around admin MFA recovery-code / email
 * delivery beyond #154.
 *
 * <p>#154 local recovery codes and the email request contract stay always-green.
 * Live SMTP / Keycloak {@code recovery-authn-codes} is PASS only when delivery
 * or enrollment was actually observed. Missing SMTP or Keycloak recovery
 * required-action is honest SKIP. Does not redo #96/#118/#133/#154/#158/#160.
 * Does not claim product G17 / 等保 / 密评. G18 stays closed.
 */
public final class G17MfaRecoveryEmailHonesty {
    public static final String G17_CLOSE = "NOT_CLAIMED";

    public record Inventory(
            boolean smtpEnabled,
            boolean smtpHostConfigured,
            boolean keycloakReachable,
            String issuer,
            String emailSkipReason,
            String recoverySkipReason
    ) {}

    private G17MfaRecoveryEmailHonesty() {}

    public static Inventory probeLive() {
        EmailMfaDeliveryProbe.Result probe = EmailMfaDeliveryProbe.probe(smtpEnabledFromEnv());
        return new Inventory(
                probe.smtpEnabled(),
                probe.smtpHostConfigured(),
                probe.keycloakReachable(),
                probe.issuer() == null ? "" : probe.issuer(),
                emailSkipReason(probe),
                recoverySkipReason(probe));
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
        require(errors, report, "vue_writes_keycloak_otp", "false");
        require(errors, report, "dengbao_claimed", "false");
        require(errors, report, "mipin_claimed", "false");
        require(errors, report, "hsm_claimed", "false");
        require(errors, report, "g17_close", G17_CLOSE);
        require(errors, report, "redo_96", "false");
        require(errors, report, "redo_118", "false");
        require(errors, report, "redo_133", "false");
        require(errors, report, "redo_154", "false");
        require(errors, report, "redo_158", "false");
        require(errors, report, "redo_160", "false");
        require(errors, report, "local_recovery_codes", "PASS");
        require(errors, report, "email_request_contract", "PASS");

        String email = value(report, "email_delivery");
        if (!isVerdict(email)) {
            errors.add("email_delivery must be PASS/SKIP/FAIL");
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

        String recovery = value(report, "recovery_live");
        if (!isVerdict(recovery)) {
            errors.add("recovery_live must be PASS/SKIP/FAIL");
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
        for (String key : report.keySet()) {
            String upper = key.toUpperCase(Locale.ROOT);
            if (upper.contains("PASSWORD") || upper.contains("SECRET") || upper.contains("PASSWD")
                    || upper.contains("TOTPSECRET")) {
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
        report.put("local_recovery_codes", "PASS");
        report.put("email_request_contract", "PASS");
        report.put("email_delivery", "SKIP");
        report.put("email_delivery_skip_reason", inventory.emailSkipReason());
        report.put("mailbox_observed", "false");
        report.put("smtp_enabled", Boolean.toString(inventory.smtpEnabled()));
        report.put("smtp_host_configured", Boolean.toString(inventory.smtpHostConfigured()));
        report.put("recovery_live", "SKIP");
        report.put("recovery_live_skip_reason", inventory.recoverySkipReason());
        report.put("recovery_authn_codes_enrolled", "false");
        report.put("recovery_code_consumed_live", "false");
        report.put("keycloak_issuer_reachable", Boolean.toString(inventory.keycloakReachable()));
        report.put("oidc_discovery", inventory.keycloakReachable() ? "PASS" : "SKIP");
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
        report.put("redo_160", "false");
        return report;
    }

    static boolean smtpEnabledFromEnv() {
        String raw = env("NEXUS_IAM_SMTP_ENABLED");
        return "true".equalsIgnoreCase(raw) || "1".equals(raw);
    }

    private static String emailSkipReason(EmailMfaDeliveryProbe.Result probe) {
        if (!probe.smtpEnabled()) {
            return EmailMfaDeliveryProbe.SMTP_DISABLED;
        }
        if (!probe.smtpHostConfigured() || !probe.keycloakReachable()) {
            return EmailMfaDeliveryProbe.EMAIL_OTP_NOT_DELIVERED;
        }
        return "mailbox_not_observed";
    }

    private static String recoverySkipReason(EmailMfaDeliveryProbe.Result probe) {
        if (!probe.keycloakReachable()) {
            return "keycloak_issuer_unreachable";
        }
        return "recovery_authn_codes_not_enrolled";
    }

    private static String[] requiredKeys() {
        return new String[] {
                "schema_version", "timestamp_utc", "java_feature", "boot_parent_version",
                "boot_parent_match", "docker_used", "local_recovery_codes",
                "email_request_contract", "email_delivery", "email_delivery_skip_reason",
                "mailbox_observed", "smtp_enabled", "smtp_host_configured",
                "recovery_live", "recovery_live_skip_reason", "recovery_authn_codes_enrolled",
                "recovery_code_consumed_live", "keycloak_issuer_reachable", "oidc_discovery",
                "vue_writes_keycloak_otp", "dengbao_claimed", "mipin_claimed", "hsm_claimed",
                "g17_close", "g18_status", "audit_port_changed", "p0_m0_redone",
                "redo_96", "redo_118", "redo_133", "redo_154", "redo_158", "redo_160"
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
