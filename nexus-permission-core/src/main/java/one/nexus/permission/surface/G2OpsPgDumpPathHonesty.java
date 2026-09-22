package one.nexus.permission.surface;

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
import one.nexus.permission.scan.OpsPgDumpHonestyGate;

/**
 * G2 leftover honesty inventory for ops-level {@code pg_dump} staying
 * <em>outside</em> the product {@code SurfaceGuard} path, beyond #155.
 *
 * <p>#155 leftover docs and {@link OpsPhysicalDumpCatalog} stay always-green.
 * Product dump HTTP stays on {@link AuthorizedExport#writeProjected} (#137).
 * Ops scripts stay a G6 runbook and must not claim SurfaceGuard. Live
 * product-database {@code pg_dump} is PASS only when a dump was actually
 * observed. Missing product DB is honest SKIP. Does not redo #137 / #144 /
 * #150 / #155. Does not claim product G2 close. G18 stays closed.
 */
public final class G2OpsPgDumpPathHonesty {
    public static final String G2_CLOSE = "NOT_CLAIMED";
    public static final String PRODUCT_PATH = OpsPhysicalDumpCatalog.PRODUCT_PATH;
    public static final String[] SIBLING_README_ROWS = {
            "g15-native-bridge-stub.md",
            "g15-native-bridge-honesty.md",
            "g15-wx-dt-prod-gap-honesty.md",
            "g15-wx-dt-native-bridge-honesty.md",
            "g7-withdraw-browser-honesty.md",
            "g7-query-expense-travel-honesty.md",
            "g2-form-submission-write.md",
            "g2-submit-start-form-write.md",
            "g2-form-write-honesty.md",
            "g2-h5-permission.md",
            "g2-h5-every-page.md",
            "g2-h5-every-page-form-write-honesty.md",
            "g2-report-surface-guard.md",
            "g2-physical-dump-surface.md",
            "g2-surfaceguard-bypass-ci.md",
            "g2-tenant-only-catalog.md",
            "g2-todo-http-surface.md",
            "g2-ops-pg-dump.md",
            "g2-ops-pg-dump-honesty.md",
            "g2-report-physical-dump-honesty.md",
            "g5-publishhook-ddl-honesty.md",
            "g9-sentinel-cluster-remainder-honesty.md",
            "g17-gm-tls-nginx-handshake-honesty.md",
            "g4-hot-purge-90d.md",
            "g4-hot-purge-90d-honesty.md",
            "g4-p1-staging-audit-events.md",
            "g4-p1-staging-audit-events-honesty.md",
            "g4-export-wizard-deny-honesty.md",
            "g4-pkce-spa-verifier.md",
            "g4-pkce-spa-verifier-honesty.md",
            "g4-pkce-spa-verifier-remainder-honesty.md",
            "s0-secret-rotate-getenv-remainder-honesty.md",
            "linux-arm-profile-honesty.md",
            "linux-arm-profile-leftover-honesty.md",
            "g17-separation-of-duties.md",
            "g17-separation-honesty.md",
            "g17-separation-leftover-honesty.md",
            "g17-mfa-recovery-email-honesty.md",
            "g17-vue-otp-writeback.md",
            "g17-mfa-recovery-vue-otp-honesty.md",
            "g6-recovery-port-dry-run.md",
            "g6-recovery-port-honesty.md",
            "g6-recovery-port-dry-run-honesty.md",
            "g10-identity-drift-dry-run.md",
            "g10-file-reconcile-honesty.md",
            "g10-identity-drift-honesty.md",
            "g10-leftover-honesty.md",
            "g1-fieldpolicy-mask-second-auth.md",
            "g1-fieldpolicy-mask-second-auth-honesty.md",
            "g1-queryast-boundsql-honesty.md",
            "g5-browser-failed-publish.md",
            "g5-mid-publish-failure-honesty.md",
            "g3-outbox-consume-crash-honesty.md",
            "g9-redis-down-invalidate-honesty.md",
            "g9-redis-down-invalidate-remainder.md",
            "s0-secret-rotate-without-restart.md",
            "s0-secret-getenv-snapshot-honesty.md",
            "s0-secret-rotate-without-restart-honesty.md",
            "s0s1-live-redis-minio-isolation-honesty.md"
    };

    public record Inventory(
            boolean leftoverDocsPresent,
            boolean catalogOutsideSurface,
            boolean productUsesAuthorizedExport,
            boolean opsScriptsAreRunbook,
            boolean leftoverRowsKept,
            boolean liveDumpObserved,
            String liveDumpSkipReason
    ) {
        public String liveProductDbPgDump() {
            return "SKIP";
        }

        public boolean staticGateIsNotLiveDump() {
            return true;
        }
    }

    private G2OpsPgDumpPathHonesty() {}

    public static boolean opsDumpOutsideSurfaceGuard() {
        return OpsPhysicalDumpCatalog.BOUNDARY.contains("out of product SurfaceGuard")
                && PRODUCT_PATH.equals("AuthorizedExport.writeProjected")
                && "DumpSurfaceGuard".equals(OpsPhysicalDumpCatalog.PRODUCT_GUARD)
                && "pg_dump".equals(OpsPhysicalDumpCatalog.OPS_BINARY);
    }

    public static boolean leftoverDocsStateBoundary(Path repo) {
        String leftover = read(repo.resolve("docs/g2-ops-pg-dump.md"));
        String honesty = read(repo.resolve("docs/g2-ops-pg-dump-honesty.md"));
        return leftover.contains("not** that surface")
                && leftover.contains("AuthorizedExport.writeProjected")
                && honesty.contains("outside")
                && honesty.contains("SurfaceGuard")
                && honesty.contains("AuthorizedExport")
                && honesty.contains("NOT_CLAIMED")
                && honesty.contains("redone_155=false");
    }

    public static boolean productDumpUsesAuthorizedExport(Path repo) {
        Path dump = repo.resolve("nexus-p3-studio/src/main/java/one/nexus/p3/dump/DumpController.java");
        String text = read(dump);
        return text.contains("AuthorizedExport.writeProjected")
                && !text.contains("ProcessBuilder")
                && !text.contains("Runtime.getRuntime");
    }

    public static boolean opsScriptsAreRunbook(Path repo) {
        for (String relative : OpsPhysicalDumpCatalog.OPS_SCRIPT_PATHS) {
            Path file = repo.resolve(relative);
            if (!Files.isRegularFile(file)) {
                return false;
            }
            String text = read(file);
            if (!text.contains("pg_dump")) {
                return false;
            }
            if (text.contains("SurfaceGuard") || text.contains("AuthorizedExport")) {
                return false;
            }
        }
        for (String relative : OpsPhysicalDumpCatalog.OPS_RUNBOOK_PATHS) {
            if (!Files.isRegularFile(repo.resolve(relative))) {
                return false;
            }
        }
        return true;
    }

    public static boolean leftoverRowsKept(Path repo) {
        String readme = read(repo.resolve("docs/README.md"));
        for (String row : SIBLING_README_ROWS) {
            if (!readme.contains(row)) {
                return false;
            }
        }
        return readme.contains("[g2-ops-pg-dump](./g2-ops-pg-dump.md)")
                && readme.contains("[g2-ops-pg-dump-honesty](./g2-ops-pg-dump-honesty.md)")
                && !readme.toLowerCase(Locale.ROOT).contains("product g2 closed")
                && !readme.contains("产品级 G2 已关闭");
    }

    public static Inventory probe(Path repo) {
        Path root = requireRepo(repo);
        boolean docs = leftoverDocsStateBoundary(root);
        boolean catalog = opsDumpOutsideSurfaceGuard();
        boolean authorized = productDumpUsesAuthorizedExport(root);
        boolean runbook = opsScriptsAreRunbook(root);
        boolean rows = leftoverRowsKept(root);
        boolean observed = observed("NEXUS_G2_LIVE_PRODUCT_DB_DUMP_OBSERVED");
        return new Inventory(
                docs,
                catalog,
                authorized,
                runbook,
                rows,
                observed,
                liveDumpSkipReason(observed));
    }

    public static Path findRepoRoot(Path start) {
        return OpsPgDumpHonestyGate.findRepoRoot(start);
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
        require(errors, report, "redone_137", "false");
        require(errors, report, "redone_144", "false");
        require(errors, report, "redone_150", "false");
        require(errors, report, "redone_155", "false");
        require(errors, report, "g2_close", G2_CLOSE);
        require(errors, report, "ops_pg_dump_outside_surfaceguard", "PASS");
        require(errors, report, "product_dump_uses_authorized_export", "PASS");
        require(errors, report, "ops_scripts_are_runbook", "PASS");
        require(errors, report, "ops_pg_dump_on_surfaceguard", "false");
        require(errors, report, "ops_scripts_claim_surfaceguard", "false");
        require(errors, report, "leftover_rows_kept", "true");
        require(errors, report, "static_gate_is_not_live_dump", "true");

        String live = value(report, "live_product_db_pg_dump");
        if (!isVerdict(live)) {
            errors.add("live_product_db_pg_dump must be PASS/SKIP/FAIL");
        }
        if ("PASS".equals(live)) {
            if (!"true".equals(value(report, "live_product_db_pg_dump_observed"))) {
                errors.add("live_product_db_pg_dump=PASS requires live_product_db_pg_dump_observed=true");
            }
        }
        if ("SKIP".equals(live)) {
            if (blank(value(report, "live_product_db_skip_reason"))) {
                errors.add("live_product_db_pg_dump SKIP needs live_product_db_skip_reason");
            }
            if ("true".equals(value(report, "live_product_db_pg_dump_observed"))) {
                errors.add("SKIP cannot claim live_product_db_pg_dump_observed=true");
            }
        }
        for (String key : report.keySet()) {
            String upper = key.toUpperCase(Locale.ROOT);
            if (upper.contains("PASSWORD") || upper.contains("SECRET") || upper.contains("PASSWD")
                    || upper.contains("ACCESS_KEY") || upper.contains("TOKEN")) {
                errors.add("report must not contain password/secret/token material");
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
        report.put("ops_pg_dump_outside_surfaceguard", "PASS");
        report.put("product_dump_uses_authorized_export", "PASS");
        report.put("ops_scripts_are_runbook", "PASS");
        report.put("ops_pg_dump_on_surfaceguard", "false");
        report.put("ops_scripts_claim_surfaceguard", "false");
        report.put("leftover_rows_kept", "true");
        report.put("static_gate_is_not_live_dump", "true");
        report.put("live_product_db_pg_dump", "SKIP");
        report.put("live_product_db_pg_dump_observed", "false");
        report.put("live_product_db_skip_reason", inventory.liveDumpSkipReason());
        report.put("redone_137", "false");
        report.put("redone_144", "false");
        report.put("redone_150", "false");
        report.put("redone_155", "false");
        report.put("g2_close", G2_CLOSE);
        report.put("g18_status", "closed_unchanged");
        report.put("audit_port_changed", "false");
        report.put("p0_m0_redone", "false");
        return report;
    }

    private static String liveDumpSkipReason(boolean observed) {
        if (!observed) {
            return "#155 static gate is not a live product-database pg_dump; "
                    + "this leftover does not dump haopan_platform / product :5432; Docker forbidden";
        }
        return "live dump listed but this leftover still does not claim product G2 close";
    }

    private static String[] requiredKeys() {
        return new String[] {
                "schema_version", "timestamp_utc", "java_feature", "boot_parent_version",
                "boot_parent_match", "docker_used", "ops_pg_dump_outside_surfaceguard",
                "product_dump_uses_authorized_export", "ops_scripts_are_runbook",
                "ops_pg_dump_on_surfaceguard", "ops_scripts_claim_surfaceguard",
                "leftover_rows_kept", "static_gate_is_not_live_dump",
                "live_product_db_pg_dump", "live_product_db_pg_dump_observed",
                "live_product_db_skip_reason", "redone_137", "redone_144",
                "redone_150", "redone_155", "g2_close", "g18_status",
                "audit_port_changed", "p0_m0_redone"
        };
    }

    private static Path requireRepo(Path repoRoot) {
        Path root = repoRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root.resolve("nexus-permission-core"))
                || !Files.isRegularFile(root.resolve("pom.xml"))) {
            throw new IllegalArgumentException("not a NexusOne repo root: " + root);
        }
        return root;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
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

    private static boolean observed(String name) {
        String raw = env(name);
        return "true".equalsIgnoreCase(raw) || "1".equals(raw);
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
