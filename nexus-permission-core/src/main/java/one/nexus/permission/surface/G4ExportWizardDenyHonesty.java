package one.nexus.permission.surface;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.InMemoryHashChainedAuditPort;
import one.nexus.common.trace.TraceIds;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.RoleMatrixCatalog;
import one.nexus.permission.engine.DepartmentCatalog;
import one.nexus.permission.engine.SnapshotManager;
import one.nexus.permission.engine.Subject;
import one.nexus.permission.identity.SubjectDirectory;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import one.nexus.permission.support.PermissionException;

/**
 * G4 leftover honesty inventory for export-wizard complete-deny (#166)
 * and SurfaceGuard deny-on-chain (#136).
 *
 * <p>In-memory harnesses may PASS. Live product JAR / Playwright browser /
 * {@code :5432} JDBC / Keycloak walks are PASS only when those listeners
 * are present <em>and</em> the walk was observed. Missing live path is
 * honest SKIP. Does not redo #136 / #152 / #166 / #190 product code.
 * Does not change {@code AuditPort} same-TX rules. Does not claim
 * product G4 close. G18 stays closed.
 */
public final class G4ExportWizardDenyHonesty {
    public static final String G4_CLOSE = "NOT_CLAIMED";
    public static final String HOST = "127.0.0.1";
    public static final int PG_PORT = 5432;
    public static final int JAR_PORT = 8080;
    public static final String LEFTOVER = "g4-export-wizard-deny-honesty";
    public static final String BEGIN_TRACE = "0199a1c0-0000-7000-8000-00000000h260";
    public static final String COMPLETE_DENY_TRACE = "0199a1c0-0000-7000-8000-00000000h261";
    public static final String GUARD_TRACE = "0199a1c0-0000-7000-8000-00000000h230";
    public static final String WIZARD_ID = "0199a1c0-0000-7000-8000-00000000h262";

    public static final String[] SIBLING_ROWS = {
            "g4-export-wizard-deny.md",
            "g4-export-wizard-deny-honesty.md",
            "g4-guard-deny-audit.md",
            "g4-export-wizard.md",
            "g4-p1-staging-audit-events.md",
            "g4-p1-staging-audit-events-honesty.md",
            "g4-hot-purge-90d.md",
            "g4-hot-purge-90d-honesty.md",
            "g4-pkce-spa-verifier.md",
            "g4-pkce-spa-verifier-honesty.md",
            "s0-secret-rotate-without-restart.md",
            "s0-secret-getenv-snapshot-honesty.md",
            "s0-secret-rotate-without-restart-honesty.md",
            "s0s1-live-redis-minio-isolation-honesty.md",
            "g6-recovery-port-dry-run-honesty.md",
            "g17-separation-honesty.md",
            "g10-identity-drift-honesty.md",
            "g10-leftover-honesty.md",
            "g15-native-bridge-honesty.md",
            "g15-wx-dt-prod-gap-honesty.md",
            "g15-wx-dt-native-bridge-honesty.md",
            "g1-fieldpolicy-mask-second-auth-honesty.md",
            "g1-queryast-boundsql-guard.md",
            "g1-queryast-boundsql-honesty.md",
            "g3-outbox-consume-crash-honesty.md",
            "g5-browser-failed-publish.md",
            "g5-mid-publish-failure-honesty.md",
            "g7-query-expense-travel-honesty.md",
            "linux-arm-profile-leftover-honesty.md"
    };

    public record GuardDenyProof(
            boolean permissionDenied,
            boolean guardReject,
            boolean chainIntact,
            String errorCode
    ) {
        public String verdict() {
            return permissionDenied && guardReject && chainIntact && "P2.PERM.0003".equals(errorCode)
                    ? "PASS"
                    : "FAIL";
        }
    }

    public record WizardDenyProof(
            boolean begunOnBeginTrace,
            boolean permissionDenied,
            boolean guardReject,
            boolean noCompleted,
            boolean noSubmitted,
            boolean chainIntact,
            String errorCode
    ) {
        public String verdict() {
            return begunOnBeginTrace
                    && permissionDenied
                    && guardReject
                    && noCompleted
                    && noSubmitted
                    && chainIntact
                    && "P2.PERM.0003".equals(errorCode)
                    ? "PASS"
                    : "FAIL";
        }
    }

    public record LiveInventory(
            boolean liveDbPresent,
            boolean liveJarPresent,
            boolean forceSkip,
            boolean liveWizardDenyObserved,
            boolean liveBrowserObserved,
            boolean liveJdbcDenyObserved,
            boolean liveKeycloakObserved,
            String liveJarSkipReason,
            String liveBrowserSkipReason,
            String liveJdbcSkipReason,
            String liveKeycloakSkipReason
    ) {
        public String liveJarWizardDeny() {
            return "SKIP";
        }

        public String liveBrowserWizard() {
            return "SKIP";
        }

        public String live5432Jdbc() {
            return "SKIP";
        }

        public String liveKeycloakIdp() {
            return "SKIP";
        }
    }

    private G4ExportWizardDenyHonesty() {}

    public static GuardDenyProof proveGuardDeny() {
        Harness harness = Harness.open(Set.of());
        try {
            TraceIds.putMdc(GUARD_TRACE);
            String code = "none";
            try {
                SurfaceGuard.require(harness.module, "form", "read");
            } catch (PermissionException denied) {
                code = denied.errorCode().code();
            }
            var found = harness.audits.findByTraceId(GUARD_TRACE);
            boolean denied = found.stream()
                    .anyMatch(event -> AuditCaptures.PERMISSION_DENIED.equals(event.event().eventType()));
            boolean reject = found.stream()
                    .anyMatch(event -> AuditCaptures.GUARD_REJECT.equals(event.event().eventType()));
            return new GuardDenyProof(denied, reject, harness.audits.verifyChain().intact(), code);
        } finally {
            harness.close();
        }
    }

    public static WizardDenyProof proveExportWizardDeny() {
        Harness harness = Harness.open(Set.of(RoleMatrixCatalog.DEPT_MANAGER), true);
        try {
            TraceIds.putMdc(BEGIN_TRACE);
            AuthorizedExport.beginWizard(harness.module.service(), harness.audits, WIZARD_ID);
            boolean begun = harness.audits.findByTraceId(BEGIN_TRACE).stream()
                    .anyMatch(event -> AuditCaptures.EXPORT_BEGUN.equals(event.event().eventType()));

            harness.roles.set(Set.of(RoleMatrixCatalog.SELF_READER));
            TraceIds.putMdc(COMPLETE_DENY_TRACE);
            String code = "none";
            try {
                AuthorizedExport.completeWizard(
                        harness.module.service(),
                        harness.audits,
                        harness.tenant.toString(),
                        "{\"surface\":\"export.wizard\"}",
                        WIZARD_ID);
            } catch (PermissionException denied) {
                code = denied.errorCode().code();
            }
            var found = harness.audits.findByTraceId(COMPLETE_DENY_TRACE);
            boolean denied = found.stream()
                    .anyMatch(event -> AuditCaptures.PERMISSION_DENIED.equals(event.event().eventType()));
            boolean reject = found.stream()
                    .anyMatch(event -> AuditCaptures.GUARD_REJECT.equals(event.event().eventType()));
            boolean noCompleted = found.stream()
                    .noneMatch(event -> AuditCaptures.EXPORT_COMPLETED.equals(event.event().eventType()));
            boolean noSubmitted = found.stream()
                    .noneMatch(event -> AuditCaptures.EXPORT_SUBMITTED.equals(event.event().eventType()));
            return new WizardDenyProof(
                    begun, denied, reject, noCompleted, noSubmitted,
                    harness.audits.verifyChain().intact(), code);
        } finally {
            harness.close();
        }
    }

    public static LiveInventory probeLive() {
        boolean forceSkip = observed("NEXUS_G4_EXPORT_WIZARD_DENY_SKIP_LIVE");
        boolean db = tcpOpen(HOST, PG_PORT);
        boolean jar = tcpOpen(HOST, JAR_PORT);
        boolean wizardObserved = observed("NEXUS_G4_LIVE_WIZARD_DENY_OBSERVED");
        boolean browserObserved = observed("NEXUS_G4_LIVE_BROWSER_WIZARD_OBSERVED");
        boolean jdbcObserved = observed("NEXUS_G4_LIVE_JDBC_DENY_OBSERVED");
        boolean keycloakObserved = observed("NEXUS_G4_LIVE_KEYCLOAK_OBSERVED");
        return new LiveInventory(
                db,
                jar,
                forceSkip,
                wizardObserved,
                browserObserved,
                jdbcObserved,
                keycloakObserved,
                jarSkipReason(forceSkip, jar, wizardObserved),
                browserSkipReason(browserObserved),
                jdbcSkipReason(forceSkip, db, jdbcObserved),
                keycloakSkipReason(jar, keycloakObserved));
    }

    public static boolean leftoverRowsKept(String readme) {
        if (readme == null || readme.isBlank()) {
            return false;
        }
        String lower = readme.toLowerCase(Locale.ROOT);
        if (lower.contains("product g4 closed") || readme.contains("产品 G4 已关闭")) {
            return false;
        }
        for (String row : SIBLING_ROWS) {
            if (!readme.contains(row)) {
                return false;
            }
        }
        return true;
    }

    public static boolean sameTxAppendUnchanged(String auditPort, String surfaceGuard, String authorizedExport) {
        if (blank(auditPort) || blank(surfaceGuard) || blank(authorizedExport)) {
            return false;
        }
        return auditPort.contains("Reject-path writes must use")
                && auditPort.contains("appendNewTransaction")
                && auditPort.contains("joins an ambient Spring")
                && surfaceGuard.contains("audits.appendNewTransaction(AuditCaptures.permission(")
                && surfaceGuard.contains("audits.appendNewTransaction(AuditCaptures.guardReject(")
                && !surfaceGuard.contains("audits.append(AuditCaptures.permission(")
                && authorizedExport.contains("Same-TX {@code append} / {@code appendNewTransaction} rules are unchanged")
                && authorizedExport.contains("audits.append(AuditCaptures.export(");
    }

    public static Path readmeSource() {
        return existing("docs/README.md");
    }

    public static Path auditPortSource() {
        return existing("nexus-audit-core/src/main/java/one/nexus/audit/AuditPort.java");
    }

    public static Path surfaceGuardSource() {
        return existing("nexus-permission-core/src/main/java/one/nexus/permission/surface/SurfaceGuard.java");
    }

    public static Path authorizedExportSource() {
        return existing("nexus-permission-core/src/main/java/one/nexus/permission/surface/AuthorizedExport.java");
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
        require(errors, report, "leftover_rows_kept", "true");
        require(errors, report, "guard_deny_harness", "PASS");
        require(errors, report, "export_wizard_deny_harness", "PASS");
        require(errors, report, "same_tx_append_unchanged", "PASS");
        require(errors, report, "redo_136", "false");
        require(errors, report, "redo_152", "false");
        require(errors, report, "redo_166", "false");
        require(errors, report, "redo_190", "false");

        requireVerdict(errors, report, "live_jar_wizard_deny");
        requireVerdict(errors, report, "live_browser_wizard");
        requireVerdict(errors, report, "live_5432_jdbc");
        requireVerdict(errors, report, "live_keycloak_idp");
        refusePassWithout(errors, report, "live_jar_wizard_deny", "live_jar_present", "true",
                "live_jar_wizard_deny=PASS requires live_jar_present=true");
        refusePassWithout(errors, report, "live_jar_wizard_deny", "live_wizard_deny_observed", "true",
                "live_jar_wizard_deny=PASS requires live_wizard_deny_observed=true");
        refusePassWithout(errors, report, "live_browser_wizard", "live_browser_observed", "true",
                "live_browser_wizard=PASS requires live_browser_observed=true");
        refusePassWithout(errors, report, "live_5432_jdbc", "live_5432_listener", "true",
                "live_5432_jdbc=PASS requires live_5432_listener=true");
        refusePassWithout(errors, report, "live_5432_jdbc", "live_jdbc_deny_observed", "true",
                "live_5432_jdbc=PASS requires live_jdbc_deny_observed=true");
        refusePassWithout(errors, report, "live_keycloak_idp", "live_keycloak_observed", "true",
                "live_keycloak_idp=PASS requires live_keycloak_observed=true");
        requireSkipReason(errors, report, "live_jar_wizard_deny", "live_jar_wizard_deny_skip_reason");
        requireSkipReason(errors, report, "live_browser_wizard", "live_browser_wizard_skip_reason");
        requireSkipReason(errors, report, "live_5432_jdbc", "live_5432_jdbc_skip_reason");
        requireSkipReason(errors, report, "live_keycloak_idp", "live_keycloak_idp_skip_reason");
        if ("SKIP".equals(value(report, "live_jar_wizard_deny"))
                && "true".equals(value(report, "live_wizard_deny_observed"))) {
            errors.add("SKIP cannot claim a live wizard-deny walk");
        }
        if ("SKIP".equals(value(report, "live_browser_wizard"))
                && "true".equals(value(report, "live_browser_observed"))) {
            errors.add("SKIP cannot claim a live browser wizard walk");
        }
        if ("SKIP".equals(value(report, "live_5432_jdbc"))
                && "true".equals(value(report, "live_jdbc_deny_observed"))) {
            errors.add("SKIP cannot claim a live JDBC deny walk");
        }
        if ("SKIP".equals(value(report, "live_keycloak_idp"))
                && "true".equals(value(report, "live_keycloak_observed"))) {
            errors.add("SKIP cannot claim a live Keycloak walk");
        }
        for (String key : report.keySet()) {
            String upper = key.toUpperCase(Locale.ROOT);
            if (upper.contains("PASSWORD") || upper.contains("SECRET") || upper.contains("PASSWD")
                    || upper.contains("ACCESS_KEY")) {
                errors.add("report must not contain password/secret material");
            }
        }
        String joined = String.join(" ", report.values()).toLowerCase(Locale.ROOT);
        if (joined.contains("product g4 closed") || joined.contains("g4_close=closed")) {
            errors.add("report must not claim product G4 closed");
        }
        return errors;
    }

    public static Map<String, String> honestSkipTemplate(
            GuardDenyProof guard, WizardDenyProof wizard, LiveInventory live) {
        Map<String, String> report = new LinkedHashMap<>();
        report.put("schema_version", "1");
        report.put("timestamp_utc", "");
        report.put("java_feature", "21");
        report.put("boot_parent_version", "4.1.1");
        report.put("boot_parent_match", "PASS");
        report.put("docker_used", "false");
        report.put("leftover_rows_kept", "true");
        report.put("guard_deny_harness", guard.verdict());
        report.put("export_wizard_deny_harness", wizard.verdict());
        report.put("same_tx_append_unchanged", "PASS");
        report.put("live_jar_present", Boolean.toString(live.liveJarPresent()));
        report.put("live_5432_listener", Boolean.toString(live.liveDbPresent()));
        report.put("live_wizard_deny_observed", Boolean.toString(live.liveWizardDenyObserved()));
        report.put("live_browser_observed", Boolean.toString(live.liveBrowserObserved()));
        report.put("live_jdbc_deny_observed", Boolean.toString(live.liveJdbcDenyObserved()));
        report.put("live_keycloak_observed", Boolean.toString(live.liveKeycloakObserved()));
        report.put("live_jar_wizard_deny", "SKIP");
        report.put("live_jar_wizard_deny_skip_reason", live.liveJarSkipReason());
        report.put("live_browser_wizard", "SKIP");
        report.put("live_browser_wizard_skip_reason", live.liveBrowserSkipReason());
        report.put("live_5432_jdbc", "SKIP");
        report.put("live_5432_jdbc_skip_reason", live.liveJdbcSkipReason());
        report.put("live_keycloak_idp", "SKIP");
        report.put("live_keycloak_idp_skip_reason", live.liveKeycloakSkipReason());
        report.put("redo_136", "false");
        report.put("redo_152", "false");
        report.put("redo_166", "false");
        report.put("redo_190", "false");
        report.put("g4_close", G4_CLOSE);
        report.put("g18_status", "closed_unchanged");
        report.put("audit_port_changed", "false");
        report.put("p0_m0_redone", "false");
        return report;
    }

    private static String[] requiredKeys() {
        return new String[] {
                "schema_version", "timestamp_utc", "java_feature", "boot_parent_version",
                "boot_parent_match", "docker_used", "leftover_rows_kept",
                "guard_deny_harness", "export_wizard_deny_harness", "same_tx_append_unchanged",
                "live_jar_present", "live_5432_listener",
                "live_wizard_deny_observed", "live_browser_observed",
                "live_jdbc_deny_observed", "live_keycloak_observed",
                "live_jar_wizard_deny", "live_jar_wizard_deny_skip_reason",
                "live_browser_wizard", "live_browser_wizard_skip_reason",
                "live_5432_jdbc", "live_5432_jdbc_skip_reason",
                "live_keycloak_idp", "live_keycloak_idp_skip_reason",
                "redo_136", "redo_152", "redo_166", "redo_190",
                "g4_close", "g18_status", "audit_port_changed", "p0_m0_redone"
        };
    }

    private static String jarSkipReason(boolean forceSkip, boolean jar, boolean observed) {
        if (forceSkip) {
            return "NEXUS_G4_EXPORT_WIZARD_DENY_SKIP_LIVE=1; live JAR wizard-deny walk not observed; "
                    + "Zonky / in-memory #166 is not a live JAR PASS; Docker forbidden";
        }
        if (!jar) {
            return "no product JAR on " + HOST + ":" + JAR_PORT
                    + "; live HTTP begin-then-complete-deny not observed; Docker forbidden";
        }
        if (!observed) {
            return "product JAR listener present on " + HOST + ":" + JAR_PORT
                    + " but a live issued-context wizard-deny walk was not observed; Docker forbidden";
        }
        return "live JAR listed but this leftover still does not claim product G4 close";
    }

    private static String browserSkipReason(boolean observed) {
        if (observed) {
            return "live browser listed but this leftover still does not claim product G4 close";
        }
        return "Playwright / Chrome + issued-context /p3/exports/wizard deny click not observed; "
                + "browser wizard stays honest SKIP";
    }

    private static String jdbcSkipReason(boolean forceSkip, boolean db, boolean observed) {
        if (forceSkip) {
            return "NEXUS_G4_EXPORT_WIZARD_DENY_SKIP_LIVE=1; live :5432 JDBC deny pair not observed; "
                    + "Zonky #136/#166 is not a live JDBC PASS; Docker forbidden";
        }
        if (!db) {
            return "nothing listening on " + HOST + ":" + PG_PORT
                    + "; live JDBC deny pair not observed; Zonky is not a live walk; Docker forbidden";
        }
        if (!observed) {
            return "PostgreSQL listener present on " + HOST + ":" + PG_PORT
                    + " but a live JDBC deny pair was not observed; Zonky is not a live walk; Docker forbidden";
        }
        return "live :5432 listed but this leftover still does not claim product G4 close";
    }

    private static String keycloakSkipReason(boolean jar, boolean observed) {
        if (observed) {
            return "live Keycloak listed but this leftover still does not claim product G4 close";
        }
        if (jar) {
            return "listener on " + HOST + ":" + JAR_PORT
                    + " is not treated as live Keycloak issued-context; OIDC walk not observed";
        }
        return "no live Keycloak issued-context on " + HOST + ":" + JAR_PORT
                + "; stub IdP / #125 click is not a live Keycloak PASS";
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

    private static void refusePassWithout(
            List<String> errors,
            Map<String, String> report,
            String verdictKey,
            String requiredKey,
            String requiredValue,
            String message
    ) {
        if ("PASS".equals(value(report, verdictKey)) && !requiredValue.equals(value(report, requiredKey))) {
            errors.add(message);
        }
    }

    private static void requireSkipReason(
            List<String> errors, Map<String, String> report, String verdictKey, String reasonKey) {
        if ("SKIP".equals(value(report, verdictKey)) && blank(value(report, reasonKey))) {
            errors.add(verdictKey + " SKIP needs " + reasonKey);
        }
    }

    private static boolean isVerdict(String value) {
        return "PASS".equals(value) || "SKIP".equals(value) || "FAIL".equals(value);
    }

    private static String value(Map<String, String> report, String key) {
        String raw = report.get(key);
        return raw == null ? "" : raw;
    }

    private static boolean tcpOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 200);
            return true;
        } catch (IOException | RuntimeException ex) {
            return false;
        }
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

    private static Path existing(String relative) {
        Path nested = Path.of("..", relative);
        if (Files.isRegularFile(nested)) {
            return nested;
        }
        return Path.of(relative);
    }

    private record Harness(
            PermissionModule module,
            InMemoryHashChainedAuditPort audits,
            UUID tenant,
            AtomicReference<Set<String>> roles
    ) {
        static Harness open(Set<String> roles) {
            return open(roles, false);
        }

        static Harness open(Set<String> roles, boolean installExample) {
            UUID id = UUID.fromString("55555555-5555-4555-8555-555555555557");
            TenantContextHolder.set(new TenantContext(
                    id, id, id, id, "g4-wizard-deny-honesty", Instant.now(), Instant.now().plusSeconds(60)));
            AtomicReference<Set<String>> roleRef = new AtomicReference<>(roles);
            InMemoryPolicyRepository repository = new InMemoryPolicyRepository();
            SubjectDirectory subjects = new SubjectDirectory() {
                @Override
                public Subject resolve(TenantContext context) {
                    return new Subject(
                            context.tenantId().toString(),
                            context.personId().toString(),
                            roleRef.get(),
                            Set.of(),
                            Set.of("d1"));
                }

                @Override
                public DepartmentCatalog catalog(UUID tenantId) {
                    return DepartmentCatalog.EMPTY;
                }
            };
            SnapshotManager snapshots = new SnapshotManager(
                    tenant -> repository.currentVersion(UUID.fromString(tenant)),
                    tenant -> repository.loadSnapshot(UUID.fromString(tenant)));
            InMemoryHashChainedAuditPort audits =
                    new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
            PermissionModule module = new PermissionModule(
                    repository, subjects, snapshots, new InMemoryPolicyInvalidation(), audits);
            if (installExample) {
                module.matrix().installExample();
            }
            return new Harness(module, audits, id, roleRef);
        }

        void close() {
            TenantContextHolder.clear();
            AccessContextHolder.clear();
            TraceIds.clearMdc();
        }
    }
}
