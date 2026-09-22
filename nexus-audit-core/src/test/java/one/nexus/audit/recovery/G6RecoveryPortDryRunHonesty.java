package one.nexus.audit.recovery;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * G6 leftover honesty inventory beyond #135 / #164.
 *
 * <p>Dry-run contract stays always-green {@code PASS}. Minute-level RPO,
 * product HA, and executing restore are {@code PASS} only when actually
 * observed. This leftover does not mutate a product database, does not
 * touch {@code haopan_platform}, does not redo #135 / #164, and does not
 * claim product G6 / RecoveryPort / HA close.
 */
public final class G6RecoveryPortDryRunHonesty {
    public static final String BOTH_LEFTOVER_ROWS = "g6-recovery-port-dry-run,g6-recovery-port-honesty";

    public record Inventory(
            boolean executed,
            boolean profileAccepted,
            String dryRunStatus,
            boolean minuteRpoObserved,
            boolean productHaObserved,
            boolean executingRestoreObserved,
            String minuteRpoSkipReason,
            String productHaSkipReason,
            String executingRestoreSkipReason
    ) {
        public String dryRunContract() {
            return executed || profileAccepted ? "FAIL" : "PASS";
        }

        public String minuteLevelRpo() {
            return "SKIP";
        }

        public String productHa() {
            return "SKIP";
        }

        public String executingRestore() {
            return "SKIP";
        }

        public boolean dryRunIsNotProductHa() {
            return true;
        }

        public boolean dryRunIsNotMinuteRpo() {
            return true;
        }
    }

    private G6RecoveryPortDryRunHonesty() {}

    public static Inventory probeDryRun() {
        RecoveryPort port = new DryRunRecoveryPort();
        RecoveryDryRunResult pg = port.dryRunPostgres();
        RecoveryDryRunResult objects = port.dryRunObjectStore();
        boolean executed = pg.executed() || objects.executed();
        boolean profileAccepted = pg.profileAccepted()
                || objects.profileAccepted()
                || port.postgresWholeDatabasePlan().profileAccepted()
                || port.objectStorePlan().profileAccepted();
        return fromDryRun(pg.status(), executed, profileAccepted);
    }

    public static Inventory fromDryRun(String dryRunStatus, boolean executed, boolean profileAccepted) {
        boolean minuteObserved = "true".equalsIgnoreCase(System.getenv("NEXUS_G6_MINUTE_RPO_OBSERVED"));
        boolean haObserved = "true".equalsIgnoreCase(System.getenv("NEXUS_G6_PRODUCT_HA_OBSERVED"));
        boolean restoreObserved = "true".equalsIgnoreCase(System.getenv("NEXUS_G6_EXECUTING_RESTORE_OBSERVED"));
        String rpoReason = minuteObserved
                ? "minute-level RPO listed but this leftover still does not claim product G6 / HA close"
                : "no WAL archive / streaming replica / delivery-grade RPO checklist observed; "
                        + "#135 dry-run and #164 honesty leftover are not minute-level RPO; "
                        + "haopan_platform / :5432 not touched; Docker forbidden";
        String haReason = haObserved
                ? "product HA listed but this leftover still does not claim product G6 / HA close"
                : "no failover / standby promotion / multi-AZ walk observed; "
                        + "#135 dry-run and #164 honesty leftover are not product HA; "
                        + "haopan_platform / :5432 not touched; Docker forbidden";
        String restoreReason = restoreObserved
                ? "executing restore listed but RecoveryPort stays dry-run; not a product close"
                : "DryRunRecoveryPort still only probes --version; dump/restore was not executed; "
                        + "product DB not mutated; haopan_platform not touched";
        return new Inventory(
                executed,
                profileAccepted,
                dryRunStatus == null ? "" : dryRunStatus,
                minuteObserved,
                haObserved,
                restoreObserved,
                rpoReason,
                haReason,
                restoreReason);
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
        require(report, "redone_135", "false");
        require(report, "redone_164", "false");
        require(report, "g6_close", "NOT_CLAIMED");
        require(report, "recovery_port_close", "NOT_CLAIMED");
        require(report, "dry_run_contract", "PASS");
        require(report, "recovery_port_executed", "false");
        require(report, "profile_accepted", "false");
        require(report, "dry_run_is_not_product_ha", "true");
        require(report, "dry_run_is_not_minute_rpo", "true");
        require(report, "haopan_platform_touched", "false");
        require(report, "product_db_mutated", "false");
        require(report, "product_5432_targeted", "false");
        String rows = report.get("leftover_rows_kept");
        if (rows == null
                || !rows.contains("g6-recovery-port-dry-run")
                || !rows.contains("g6-recovery-port-honesty")) {
            throw new IllegalArgumentException(
                    "leftover_rows_kept must keep both g6-recovery-port-dry-run and g6-recovery-port-honesty");
        }
        requireVerdict(report, "minute_level_rpo");
        requireVerdict(report, "product_ha");
        requireVerdict(report, "executing_restore");
        refusePassWithout(report, "minute_level_rpo", "minute_rpo_observed", "true",
                "minute_level_rpo=PASS requires minute_rpo_observed=true");
        refusePassWithout(report, "product_ha", "product_ha_observed", "true",
                "product_ha=PASS requires product_ha_observed=true");
        refusePassWithout(report, "executing_restore", "executing_restore_observed", "true",
                "executing_restore=PASS requires executing_restore_observed=true");
        requireSkipReason(report, "minute_level_rpo", "minute_level_rpo_skip_reason");
        requireSkipReason(report, "product_ha", "product_ha_skip_reason");
        requireSkipReason(report, "executing_restore", "executing_restore_skip_reason");
        if ("SKIP".equals(report.get("minute_level_rpo"))
                && "true".equals(report.get("minute_rpo_observed"))) {
            throw new IllegalArgumentException("SKIP cannot claim a minute-level RPO walk");
        }
        if ("SKIP".equals(report.get("product_ha"))
                && "true".equals(report.get("product_ha_observed"))) {
            throw new IllegalArgumentException("SKIP cannot claim a product HA walk");
        }
        if ("SKIP".equals(report.get("executing_restore"))
                && "true".equals(report.get("executing_restore_observed"))) {
            throw new IllegalArgumentException("SKIP cannot claim an executing restore walk");
        }
        for (String key : report.keySet()) {
            String upper = key.toUpperCase(Locale.ROOT);
            if (upper.contains("PASSWORD") || upper.contains("SECRET") || upper.contains("PASSWD")
                    || upper.contains("TOKEN")) {
                throw new IllegalArgumentException("report must not contain password/secret/token material");
            }
        }
    }

    public static Map<String, String> honestSkipTemplate(Inventory inventory) {
        Map<String, String> report = new LinkedHashMap<>();
        report.put("schema_version", "1");
        report.put("timestamp_utc", "");
        report.put("java_feature", "21");
        report.put("boot_parent_version", "4.1.1");
        report.put("boot_parent_match", "PASS");
        report.put("docker_used", "false");
        report.put("dry_run_contract", inventory.dryRunContract());
        report.put("recovery_port_executed", Boolean.toString(inventory.executed()));
        report.put("profile_accepted", Boolean.toString(inventory.profileAccepted()));
        report.put("dry_run_is_not_product_ha", "true");
        report.put("dry_run_is_not_minute_rpo", "true");
        report.put("leftover_rows_kept", BOTH_LEFTOVER_ROWS);
        report.put("haopan_platform_touched", "false");
        report.put("product_db_mutated", "false");
        report.put("product_5432_targeted", "false");
        report.put("minute_rpo_observed", Boolean.toString(inventory.minuteRpoObserved()));
        report.put("minute_level_rpo", "SKIP");
        report.put("minute_level_rpo_skip_reason", inventory.minuteRpoSkipReason());
        report.put("product_ha_observed", Boolean.toString(inventory.productHaObserved()));
        report.put("product_ha", "SKIP");
        report.put("product_ha_skip_reason", inventory.productHaSkipReason());
        report.put("executing_restore_observed", Boolean.toString(inventory.executingRestoreObserved()));
        report.put("executing_restore", "SKIP");
        report.put("executing_restore_skip_reason", inventory.executingRestoreSkipReason());
        report.put("g6_close", "NOT_CLAIMED");
        report.put("recovery_port_close", "NOT_CLAIMED");
        report.put("g18_status", "closed_unchanged");
        report.put("audit_port_changed", "false");
        report.put("p0_m0_redone", "false");
        report.put("redone_135", "false");
        report.put("redone_164", "false");
        return report;
    }

    private static String[] requiredKeys() {
        return new String[] {
                "schema_version", "timestamp_utc", "java_feature", "boot_parent_version",
                "boot_parent_match", "docker_used", "dry_run_contract", "recovery_port_executed",
                "profile_accepted", "dry_run_is_not_product_ha", "dry_run_is_not_minute_rpo",
                "leftover_rows_kept", "haopan_platform_touched", "product_db_mutated",
                "product_5432_targeted", "minute_rpo_observed", "minute_level_rpo",
                "minute_level_rpo_skip_reason", "product_ha_observed", "product_ha",
                "product_ha_skip_reason", "executing_restore_observed", "executing_restore",
                "executing_restore_skip_reason", "g6_close", "recovery_port_close",
                "g18_status", "audit_port_changed", "p0_m0_redone", "redone_135", "redone_164"
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

    private static void requireSkipReason(Map<String, String> report, String verdictKey, String reasonKey) {
        if ("SKIP".equals(report.get(verdictKey))) {
            String reason = report.get(reasonKey);
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException(verdictKey + " SKIP needs " + reasonKey);
            }
        }
    }
}
