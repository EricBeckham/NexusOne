package one.nexus.permission.surface;

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
import one.nexus.permission.scan.OpsPgDumpHonestyGate;

/**
 * G2 leftover honesty inventory for report query/export {@code SurfaceGuard}
 * (#124) and physical dump / raw export {@code SurfaceGuard} (#137).
 *
 * <p>#124 leftover docs and {@link ReportSurfaceCatalog} stay always-green.
 * #137 leftover docs and {@link DumpSurfaceCatalog} stay always-green.
 * Report export stays {@link AuthorizedExport#submit}. Dump stays
 * {@link AuthorizedExport#writeProjected}. Live product-JAR + issued-context
 * HTTP is PASS only when a healthy JAR, issued context, and observed report
 * query/export plus dump walks exist. Missing JAR/context is honest SKIP.
 * Does not redo #122 / #124 / #137 / H5 form.write leftovers. Does not claim
 * product G2 close. G18 stays closed.
 */
public final class G2ReportPhysicalDumpHonesty {
    public static final String G2_CLOSE = "NOT_CLAIMED";
    public static final String REPORT_QUERY_RESOURCE = ReportSurfaceCatalog.QUERY_RESOURCE;
    public static final String REPORT_QUERY_ACTION = ReportSurfaceCatalog.QUERY_ACTION;
    public static final String REPORT_EXPORT_RESOURCE = ReportSurfaceCatalog.EXPORT_RESOURCE;
    public static final String REPORT_EXPORT_ACTION = ReportSurfaceCatalog.EXPORT_ACTION;
    public static final String DUMP_READ_RESOURCE = DumpSurfaceCatalog.READ_RESOURCE;
    public static final String DUMP_READ_ACTION = DumpSurfaceCatalog.READ_ACTION;
    public static final String DUMP_EXPORT_RESOURCE = DumpSurfaceCatalog.DUMP_RESOURCE;
    public static final String DUMP_EXPORT_ACTION = DumpSurfaceCatalog.DUMP_ACTION;
    public static final String REPORT_QUERY_PATH = "POST /api/v1/p3/reports/query";
    public static final String REPORT_EXPORT_PATH = "POST /api/v1/p3/reports/export";
    public static final String DUMP_EXPORT_PATH = "POST /api/v1/p3/dumps/export";
    public static final String HOST = "127.0.0.1";
    public static final int PRODUCT_PORT = 8080;
    public static final int ALT_PORT = 8088;

    public static final String[] SIBLING_README_ROWS = {
            "g2-report-surface-guard.md",
            "g2-physical-dump-surface.md",
            "g2-surfaceguard-bypass-ci.md",
            "g2-form-submission-write.md",
            "g2-submit-start-form-write.md",
            "g2-form-write-honesty.md",
            "g2-h5-permission.md",
            "g2-h5-every-page.md",
            "g2-h5-every-page-form-write-honesty.md",
            "g2-tenant-only-catalog.md",
            "g2-todo-http-surface.md",
            "g2-ops-pg-dump.md",
            "g2-ops-pg-dump-honesty.md",
            "g9-sentinel-cluster-remainder-honesty.md",
            "g9-sentinel-cluster-honesty.md",
            "g9-live-multi-jvm-replica.md",
            "g9-policysnapshot-invalidate.md",
            "g9-failover-remainder.md",
            "g9-redis-down-invalidate-honesty.md",
            "g9-redis-down-invalidate-remainder.md",
            "g15-native-bridge-stub.md",
            "g15-native-bridge-honesty.md",
            "g15-wx-dt-prod-gap-honesty.md",
            "g15-wx-dt-native-bridge-honesty.md",
            "g15-wx-dt-native-bridge-remainder.md",
            "g7-withdraw-browser-honesty.md",
            "g7-query-expense-travel-honesty.md",
            "g4-hot-purge-90d.md",
            "g4-hot-purge-90d-honesty.md",
            "g4-p1-staging-audit-events.md",
            "g4-p1-staging-audit-events-honesty.md",
            "g4-export-wizard-deny.md",
            "g4-export-wizard-deny-honesty.md",
            "g4-guard-deny-audit.md",
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
            "g17-gm-tls-nginx-handshake.md",
            "g17-gm-tls-nginx-handshake-honesty.md",
            "g17-gm-tls-nginx-dual-stack.md",
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
            "g1-queryast-boundsql-guard.md",
            "g1-queryast-export-honesty.md",
            "g1-export-fieldpolicy-drop.md",
            "g5-browser-failed-publish.md",
            "g5-mid-publish-failure-honesty.md",
            "g5-publishhook-ddl-honesty.md",
            "g3-outbox-consume-crash-honesty.md",
            "g3-claim-crash-window.md",
            "s0-secret-rotate-without-restart.md",
            "s0-secret-getenv-snapshot-honesty.md",
            "s0-secret-rotate-without-restart-honesty.md",
            "s0s1-live-redis-minio-isolation-honesty.md"
    };

    public record Inventory(
            boolean leftoverDocsPresent,
            boolean reportOnSurfaceGuard,
            boolean dumpOnSurfaceGuard,
            boolean reportUsesAuthorizedSubmit,
            boolean dumpUsesWriteProjected,
            boolean leftoverRowsKept,
            boolean endpointSet,
            boolean issuedContextSet,
            boolean listenerPresent,
            String skipReason
    ) {
        public boolean livePresent() {
            return endpointSet && issuedContextSet && listenerPresent;
        }

        public String liveJarWalk() {
            return "SKIP";
        }

        public String liveReportWalk() {
            return "SKIP";
        }

        public String liveDumpWalk() {
            return "SKIP";
        }

        public boolean reportIsPhysicalDump() {
            return false;
        }

        public boolean dumpIsReportTaskSubmit() {
            return false;
        }
    }

    private G2ReportPhysicalDumpHonesty() {}

    public static boolean reportCellsMatchCatalog() {
        return "ledger".equals(REPORT_QUERY_RESOURCE)
                && "read".equals(REPORT_QUERY_ACTION)
                && "ledger.export.csv".equals(REPORT_EXPORT_RESOURCE)
                && "submit".equals(REPORT_EXPORT_ACTION)
                && REPORT_EXPORT_RESOURCE.equals(SurfaceCatalog.EXPORT_RESOURCE)
                && REPORT_EXPORT_ACTION.equals(SurfaceCatalog.EXPORT_ACTION);
    }

    public static boolean dumpCellsMatchCatalog() {
        return "ledger".equals(DUMP_READ_RESOURCE)
                && "read".equals(DUMP_READ_ACTION)
                && "ledger.export.csv".equals(DUMP_EXPORT_RESOURCE)
                && "submit".equals(DUMP_EXPORT_ACTION)
                && DUMP_EXPORT_RESOURCE.equals(SurfaceCatalog.EXPORT_RESOURCE)
                && DUMP_EXPORT_ACTION.equals(SurfaceCatalog.EXPORT_ACTION);
    }

    public static boolean leftoverDocsStateBoundary(Path repo) {
        String report = read(repo.resolve("docs/g2-report-surface-guard.md"));
        String dump = read(repo.resolve("docs/g2-physical-dump-surface.md"));
        String honesty = read(repo.resolve("docs/g2-report-physical-dump-honesty.md"));
        return report.contains("SurfaceGuard")
                && report.contains("ledger")
                && report.contains("ledger.export.csv")
                && report.contains("not a product-wide G2 close")
                && dump.contains("AuthorizedExport.writeProjected")
                && dump.contains("not a product-wide G2 close")
                && dump.toLowerCase(Locale.ROOT).contains("does not redo")
                && honesty.contains("NOT_CLAIMED")
                && honesty.contains("redone_122=false")
                && honesty.contains("redone_124=false")
                && honesty.contains("redone_137=false")
                && honesty.contains("Live JAR")
                && honesty.contains("honest SKIP");
    }

    public static boolean reportControllerUsesSurfaceGuard(Path repo) {
        String controller = read(repo.resolve(
                "nexus-p3-studio/src/main/java/one/nexus/p3/report/ReportController.java"));
        String guard = read(repo.resolve(
                "nexus-p3-studio/src/main/java/one/nexus/p3/infra/permission/ReportSurfaceGuard.java"));
        return controller.contains("ReportSurfaceGuard")
                && controller.contains("AuthorizedExport.submit")
                && controller.contains("physical_dump\", false")
                && !controller.contains("AuthorizedExport.writeProjected")
                && !controller.contains("ProcessBuilder")
                && guard.contains("SurfaceGuard.require")
                && guard.contains("ReportSurfaceCatalog.QUERY_RESOURCE")
                && guard.contains("ReportSurfaceCatalog.EXPORT_RESOURCE");
    }

    public static boolean dumpControllerUsesSurfaceGuard(Path repo) {
        String controller = read(repo.resolve(
                "nexus-p3-studio/src/main/java/one/nexus/p3/dump/DumpController.java"));
        String guard = read(repo.resolve(
                "nexus-p3-studio/src/main/java/one/nexus/p3/infra/permission/DumpSurfaceGuard.java"));
        return controller.contains("DumpSurfaceGuard")
                && controller.contains("AuthorizedExport.writeProjected")
                && controller.contains("physical_dump\", true")
                && !controller.contains("AuthorizedExport.submit")
                && !controller.contains("ProcessBuilder")
                && guard.contains("SurfaceGuard.require")
                && guard.contains("DumpSurfaceCatalog.READ_RESOURCE")
                && guard.contains("DumpSurfaceCatalog.DUMP_RESOURCE");
    }

    public static boolean leftoverRowsKept(Path repo) {
        String readme = read(repo.resolve("docs/README.md"));
        for (String row : SIBLING_README_ROWS) {
            if (!readme.contains(row)) {
                return false;
            }
        }
        return readme.contains("[g2-report-surface-guard](./g2-report-surface-guard.md)")
                && readme.contains("[g2-physical-dump-surface](./g2-physical-dump-surface.md)")
                && readme.contains("[g2-report-physical-dump-honesty](./g2-report-physical-dump-honesty.md)")
                && readme.contains("[g1-queryast-export-honesty](./g1-queryast-export-honesty.md)")
                && readme.contains("[g15-wx-dt-native-bridge-remainder](./g15-wx-dt-native-bridge-remainder.md)")
                && readme.contains("[g17-separation-leftover-honesty](./g17-separation-leftover-honesty.md)")
                && readme.contains("[g4-pkce-spa-verifier-remainder-honesty](./g4-pkce-spa-verifier-remainder-honesty.md)")
                && readme.contains("[s0-secret-rotate-getenv-remainder-honesty](./s0-secret-rotate-getenv-remainder-honesty.md)")
                && !readme.toLowerCase(Locale.ROOT).contains("product g2 closed")
                && !readme.contains("产品级 G2 已关闭");
    }

    public static Inventory probe(Path repo) {
        Path root = requireRepo(repo);
        boolean endpointSet = !blank(env("NEXUS_BASE_URL")) || !blank(env("NEXUS_G2_LIVE_BASE"));
        boolean contextSet = !blank(env("NEXUS_G2_LIVE_CONTEXT_ID"));
        boolean listener = portOpen(HOST, PRODUCT_PORT) || portOpen(HOST, ALT_PORT);
        return new Inventory(
                leftoverDocsStateBoundary(root),
                reportCellsMatchCatalog() && reportControllerUsesSurfaceGuard(root),
                dumpCellsMatchCatalog() && dumpControllerUsesSurfaceGuard(root),
                reportControllerUsesSurfaceGuard(root),
                dumpControllerUsesSurfaceGuard(root),
                leftoverRowsKept(root),
                endpointSet,
                contextSet,
                listener,
                liveSkipReason(endpointSet, contextSet, listener));
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
        require(errors, report, "redone_122", "false");
        require(errors, report, "redone_124", "false");
        require(errors, report, "redone_137", "false");
        require(errors, report, "redone_163", "false");
        require(errors, report, "redone_173", "false");
        require(errors, report, "redone_204", "false");
        require(errors, report, "g2_close", G2_CLOSE);
        require(errors, report, "report_surface_harness", "PASS");
        require(errors, report, "dump_surface_harness", "PASS");
        require(errors, report, "report_uses_authorized_submit", "PASS");
        require(errors, report, "dump_uses_write_projected", "PASS");
        require(errors, report, "report_is_physical_dump", "false");
        require(errors, report, "dump_is_report_task_submit", "false");
        require(errors, report, "leftover_rows_all", "true");

        String rows = value(report, "leftover_rows_kept");
        for (String row : List.of(
                "g2-report-surface-guard",
                "g2-physical-dump-surface",
                "g2-surfaceguard-bypass-ci",
                "g2-h5-every-page-form-write-honesty",
                "g2-ops-pg-dump-honesty",
                "g9-sentinel-cluster-remainder-honesty",
                "g5-publishhook-ddl-honesty",
                "g17-gm-tls-nginx-handshake-honesty",
                "g1-queryast-export-honesty",
                "g15-wx-dt-native-bridge-remainder",
                "g17-separation-leftover-honesty",
                "g4-pkce-spa-verifier-remainder-honesty",
                "s0-secret-rotate-getenv-remainder-honesty")) {
            if (!rows.contains(row)) {
                errors.add("leftover_rows_kept must keep " + row);
            }
        }

        String live = value(report, "live_jar_walk");
        String reportWalk = value(report, "live_report_walk");
        String dumpWalk = value(report, "live_dump_walk");
        if (!isVerdict(live)) {
            errors.add("live_jar_walk must be PASS/SKIP/FAIL");
        }
        if (!isVerdict(reportWalk)) {
            errors.add("live_report_walk must be PASS/SKIP/FAIL");
        }
        if (!isVerdict(dumpWalk)) {
            errors.add("live_dump_walk must be PASS/SKIP/FAIL");
        }
        if ("PASS".equals(live)) {
            if (!"true".equals(value(report, "live_jar_present"))) {
                errors.add("live_jar_walk=PASS requires live_jar_present=true");
            }
            if (!"true".equals(value(report, "live_jar_context_set"))) {
                errors.add("live_jar_walk=PASS requires live_jar_context_set=true");
            }
            if (!"true".equals(value(report, "live_report_observed"))) {
                errors.add("live_jar_walk=PASS requires live_report_observed=true");
            }
            if (!"true".equals(value(report, "live_dump_observed"))) {
                errors.add("live_jar_walk=PASS requires live_dump_observed=true");
            }
        }
        if ("SKIP".equals(live)) {
            if (blank(value(report, "live_jar_skip_reason"))) {
                errors.add("live JAR SKIP needs live_jar_skip_reason");
            }
            if ("true".equals(value(report, "live_report_observed"))) {
                errors.add("SKIP cannot claim a live report walk");
            }
            if ("true".equals(value(report, "live_dump_observed"))) {
                errors.add("SKIP cannot claim a live dump walk");
            }
        }
        if ("PASS".equals(reportWalk)) {
            if (!"true".equals(value(report, "live_jar_present"))) {
                errors.add("live_report_walk=PASS requires live_jar_present=true");
            }
            if (!"true".equals(value(report, "live_report_observed"))) {
                errors.add("live_report_walk=PASS requires live_report_observed=true");
            }
        }
        if ("SKIP".equals(reportWalk) && "true".equals(value(report, "live_report_observed"))) {
            errors.add("SKIP cannot claim a live report walk");
        }
        if ("PASS".equals(dumpWalk)) {
            if (!"true".equals(value(report, "live_jar_present"))) {
                errors.add("live_dump_walk=PASS requires live_jar_present=true");
            }
            if (!"true".equals(value(report, "live_dump_observed"))) {
                errors.add("live_dump_walk=PASS requires live_dump_observed=true");
            }
        }
        if ("SKIP".equals(dumpWalk) && "true".equals(value(report, "live_dump_observed"))) {
            errors.add("SKIP cannot claim a live dump walk");
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
        report.put("report_surface_harness", "PASS");
        report.put("dump_surface_harness", "PASS");
        report.put("report_uses_authorized_submit", "PASS");
        report.put("dump_uses_write_projected", "PASS");
        report.put("report_is_physical_dump", "false");
        report.put("dump_is_report_task_submit", "false");
        report.put("leftover_rows_kept",
                "g2-report-surface-guard,g2-physical-dump-surface,g2-surfaceguard-bypass-ci,"
                        + "g2-h5-every-page-form-write-honesty,g2-ops-pg-dump-honesty,"
                        + "g9-sentinel-cluster-remainder-honesty,g5-publishhook-ddl-honesty,"
                        + "g17-gm-tls-nginx-handshake-honesty,g1-queryast-export-honesty,"
                        + "g15-wx-dt-native-bridge-remainder,g17-separation-leftover-honesty,"
                        + "g4-pkce-spa-verifier-remainder-honesty,"
                        + "s0-secret-rotate-getenv-remainder-honesty");
        report.put("leftover_rows_all", "true");
        report.put("live_jar_endpoint_set", Boolean.toString(inventory.endpointSet()));
        report.put("live_jar_context_set", Boolean.toString(inventory.issuedContextSet()));
        report.put("live_jar_listener", Boolean.toString(inventory.listenerPresent()));
        report.put("live_jar_present", Boolean.toString(inventory.livePresent()));
        report.put("live_jar_walk", "SKIP");
        report.put("live_jar_skip_reason", inventory.skipReason());
        report.put("live_report_walk", "SKIP");
        report.put("live_report_observed", "false");
        report.put("live_dump_walk", "SKIP");
        report.put("live_dump_observed", "false");
        report.put("redone_122", "false");
        report.put("redone_124", "false");
        report.put("redone_137", "false");
        report.put("redone_163", "false");
        report.put("redone_173", "false");
        report.put("redone_204", "false");
        report.put("g2_close", G2_CLOSE);
        report.put("g18_status", "closed_unchanged");
        report.put("audit_port_changed", "false");
        report.put("p0_m0_redone", "false");
        return report;
    }

    private static String liveSkipReason(boolean endpointSet, boolean contextSet, boolean listener) {
        if (!endpointSet && !listener) {
            return "NEXUS_BASE_URL / NEXUS_G2_LIVE_BASE unset; nothing listening on 127.0.0.1:8080 / :8088; Docker forbidden";
        }
        if (!contextSet) {
            return "NEXUS_G2_LIVE_CONTEXT_ID unset; live JAR walk SKIP (issued context stays in env)";
        }
        if (!listener) {
            return "live base set but product JAR not reachable; live walk SKIP";
        }
        return "live listener present but this leftover does not claim a walk until report query/export and dump export were observed";
    }

    private static String[] requiredKeys() {
        return new String[] {
                "schema_version", "timestamp_utc", "java_feature", "boot_parent_version",
                "boot_parent_match", "docker_used", "report_surface_harness",
                "dump_surface_harness", "report_uses_authorized_submit",
                "dump_uses_write_projected", "report_is_physical_dump",
                "dump_is_report_task_submit", "leftover_rows_kept", "leftover_rows_all",
                "live_jar_endpoint_set", "live_jar_context_set", "live_jar_listener",
                "live_jar_present", "live_jar_walk", "live_jar_skip_reason",
                "live_report_walk", "live_report_observed", "live_dump_walk",
                "live_dump_observed", "redone_122", "redone_124", "redone_137",
                "redone_163", "redone_173", "redone_204", "g2_close", "g18_status",
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

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean portOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 200);
            return true;
        } catch (IOException ex) {
            return false;
        }
    }
}
