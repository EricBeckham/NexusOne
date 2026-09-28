package one.nexus.permission.scan;

import java.util.List;

/**
 * Documented allowlist for the G2 SurfaceGuard / PermissionEngine bypass CI
 * gate.
 *
 * <p>Surfaces listed here may interpret a {@code Decision} or call the engine
 * because they <em>are</em> the engine, the single {@code SurfaceGuard}
 * interpretation, or a documented leftover that is not yet on this engine.
 * They are not a license to skip {@code SurfaceGuard} from a new P3 API.
 *
 * <p>See {@code docs/g2-surfaceguard-bypass-ci.md}.
 */
public final class SurfaceGuardBypassAllowlist {
    private SurfaceGuardBypassAllowlist() {}

    public record Module(String artifactId, String reason, List<String> pathPrefixes) {}

    /**
     * Files that may call {@code PermissionEngine.evaluate} / {@code can}.
     * Product surfaces go through {@code SurfaceGuard} / {@code PermissionService}.
     */
    public static final List<String> ENGINE_PATHS = List.of(
            "nexus-permission-core/src/main/java/one/nexus/permission/engine/PermissionEngine.java",
            "nexus-permission-core/src/main/java/one/nexus/permission/PermissionService.java");

    /**
     * Files that may call IsolatedExport sink methods. Product export goes
     * through {@code AuthorizedExport} (SurfaceGuard first).
     */
    public static final List<String> EXPORT_PATHS = List.of(
            "modules/isolation-kernel/src/main/java/one/nexus/isolation/boundary/IsolatedExport.java",
            "nexus-permission-core/src/main/java/one/nexus/permission/surface/AuthorizedExport.java");

    /**
     * Files that may construct {@code new Decision(true, …)}. Engine allow and
     * documented isolation-only {@code tenant_only} leftovers.
     */
    public static final List<String> DECISION_TRUE_PATHS = List.of(
            "nexus-permission-core/src/main/java/one/nexus/permission/engine/PermissionEngine.java",
            "nexus-permission-core/src/main/java/one/nexus/permission/surface/IsolationOnlyDecision.java",
            "nexus-p3-studio/src/main/java/one/nexus/p3/infra/permission/TenantOnlyDataScopeHook.java");

    /**
     * No unguarded P3 surface remains (Todo HTTP goes through {@code TodoSurfaceGuard}). The entries below are P4
     * controllers the path regex matches by word only — they are not P3 surfaces and are authorized in their service:
     * <ul>
     *   <li>{@code /installations/{id}/process-configuration} matches {@code /process}; the service calls
     *       {@code ProcessDefinitionSurfaceGuard.requireRead/requireWrite} on every method.</li>
     *   <li>{@code /installations/{id}/upgrade-steps/{step}/records} matches {@code /records}; the service requires a
     *       platform context with {@code SYSTEM_ADMIN} and a named operator.</li>
     * </ul>
     */
    public static final List<String> UNGUARDED_P3_LEFTOVER_PATHS = List.of(
            "nexus-p4-market/src/main/java/one/nexus/p4/market/InstallationProcessConfigurationController.java",
            "nexus-p4-market/src/main/java/one/nexus/p4/market/UpgradeStepRecordController.java");

    /**
     * Tokens that count as going through SurfaceGuard / the same Decision.
     * A P3 surface RestController that mentions one of these is guarded.
     */
    public static final List<String> GUARD_TOKENS = List.of(
            "SurfaceGuard",
            "FormSurfaceGuard",
            "H5SurfaceGuard",
            "ReportSurfaceGuard",
            "DumpSurfaceGuard",
            "CatalogSurfaceGuard",
            "TodoSurfaceGuard",
            "ProcessDefinitionSurfaceGuard",
            "PermissionDataScopeHook",
            "AuthorizedExport",
            "AuthorizedSubmit",
            "SequentialApprovalService",
            "LedgerRecordService",
            "BusinessPathService");

    /**
     * Documented SurfaceGuard wrappers and the evaluate / check API. These
     * files are the intended callers, not bypasses.
     */
    public static final List<Module> SURFACE_MODULES = List.of(
            new Module(
                    "nexus-permission-core",
                    "PermissionEngine + PermissionService + SurfaceGuard / AuthorizedExport / check API",
                    List.of(
                            "nexus-permission-core/src/main/java/one/nexus/permission/engine/",
                            "nexus-permission-core/src/main/java/one/nexus/permission/PermissionService.java",
                            "nexus-permission-core/src/main/java/one/nexus/permission/surface/",
                            "nexus-permission-core/src/main/java/one/nexus/permission/api/PermissionController.java")),
            new Module(
                    "nexus-p3-studio",
                    "Form / H5 / report / dump / catalog / todo / list-detail / flow / path wrappers around SurfaceGuard",
                    List.of(
                            "nexus-p3-studio/src/main/java/one/nexus/p3/infra/permission/",
                            "nexus-p3-studio/src/main/java/one/nexus/p3/flow/SequentialApprovalService.java",
                            "nexus-p3-studio/src/main/java/one/nexus/p3/flow/api/TodoController.java",
                            "nexus-p3-studio/src/main/java/one/nexus/p3/flow/api/ProcessDefinitionDraftController.java",
                            "nexus-p3-studio/src/main/java/one/nexus/p3/flow/api/ProcessDefinitionValidationController.java",
                            "nexus-p3-studio/src/main/java/one/nexus/p3/path/BusinessPathService.java",
                            "nexus-p3-studio/src/main/java/one/nexus/p3/studio/api/FormController.java",
                            "nexus-p3-studio/src/main/java/one/nexus/p3/report/ReportController.java",
                            "nexus-p3-studio/src/main/java/one/nexus/p3/report/ExportWizardController.java",
                            "nexus-p3-studio/src/main/java/one/nexus/p3/dump/DumpController.java",
                            "nexus-p3-studio/src/main/java/one/nexus/p3/application/LedgerRecordService.java")),
            new Module(
                    "nexus-bootstrap",
                    "audit.read SurfaceGuard + Job/Task AuthorizedSubmit wiring",
                    List.of(
                            "nexus-bootstrap/src/main/java/one/nexus/bootstrap/web/AuditTraceController.java",
                            "nexus-bootstrap/src/main/java/one/nexus/bootstrap/permission/PermissionSurfaceConfiguration.java")),
            new Module(
                    "isolation-kernel",
                    "IsolatedExport isolation-only export surface; product callers use AuthorizedExport",
                    List.of("modules/isolation-kernel/src/main/java/one/nexus/isolation/boundary/IsolatedExport.java")));

    /** Product trees scanned by the gate. {@code poc/} and {@code web/} stay out. */
    public static final List<String> PRODUCT_SOURCE_ROOTS = List.of(
            "modules/isolation-kernel",
            "nexus-common",
            "nexus-data-core",
            "nexus-permission-core",
            "nexus-expression-core",
            "nexus-event-core",
            "nexus-task-core",
            "nexus-cache-core",
            "nexus-crypto-core",
            "nexus-file-core",
            "nexus-notification-core",
            "nexus-audit-core",
            "nexus-p1-tenant",
            "nexus-p2-iam",
            "nexus-p3-studio",
            "nexus-p4-market",
            "nexus-p5-connect",
            "nexus-bootstrap");

    public static List<String> surfacePathPrefixes() {
        return SURFACE_MODULES.stream().flatMap(module -> module.pathPrefixes().stream()).toList();
    }
}
