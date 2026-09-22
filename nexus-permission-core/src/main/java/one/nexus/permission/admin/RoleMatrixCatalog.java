package one.nexus.permission.admin;

import java.util.List;
import java.util.Map;
import one.nexus.permission.engine.DataScopeKind;
import one.nexus.permission.engine.FieldRule;
import one.nexus.permission.engine.Mask;
import one.nexus.permission.surface.SurfaceCatalog;

/**
 * G2 example role matrix. Distinct from the demo {@code tenant_member} ALL-scope
 * seed: these cells carry SELF / DEPT_AND_CHILDREN DataScope and field policy.
 * Installing the catalog does not assign roles to the current person.
 */
public final class RoleMatrixCatalog {
    public static final String SELF_READER = "g2_self_reader";
    public static final String DEPT_MANAGER = "g2_dept_manager";
    public static final String APPROVER = "g2_approver";
    public static final String FORM_DESIGNER = "g2_form_designer";

    public static final String SALARY_FIELD = "salary";

    public static final String COMPLETENESS_NOTE =
            "tenant_member ALL-scope seed is demo bootstrap only. G2 requires a role "
                    + "matrix with DataScope and field policy, evaluated by PermissionEngine "
                    + "for list, detail, flow, form studio, form fill / POST submissions, "
                    + "path submit-start (form.write + process.instance/start when form_id is "
                    + "present), H5 /m/* list/detail/form/todo, report query/export, export Task type, Job/Task submit, "
                    + "and export cache. Physical ledger dump / raw export HTTP is on "
                    + "SurfaceGuard; product G2 remains open (H5 every page / allowlist "
                    + "/ pg_dump ops). Not full product G2.";

    private RoleMatrixCatalog() {}

    public record RoleSpec(String code, String name) {}

    public record CellSpec(
            String roleCode,
            String effect,
            String resource,
            String action,
            DataScopeKind scopeKind,
            Map<String, FieldRule> fields
    ) {}

    public static List<RoleSpec> exampleRoles() {
        return List.of(
                new RoleSpec(SELF_READER, "G2 self reader"),
                new RoleSpec(DEPT_MANAGER, "G2 department manager"),
                new RoleSpec(APPROVER, "G2 process approver"),
                new RoleSpec(FORM_DESIGNER, "G2 form designer"));
    }

    public static List<CellSpec> exampleCells() {
        FieldRule salaryHidden = new FieldRule(false, false, Mask.FULL, false);
        FieldRule salaryMasked = new FieldRule(true, false, Mask.PARTIAL, false);
        return List.of(
                new CellSpec(SELF_READER, "GRANT", "ledger", "read", DataScopeKind.SELF,
                        Map.of(SALARY_FIELD, salaryHidden)),
                new CellSpec(SELF_READER, "GRANT", "form", "read", DataScopeKind.SELF, Map.of()),
                new CellSpec(DEPT_MANAGER, "GRANT", "ledger", "read", DataScopeKind.DEPT_AND_CHILDREN,
                        Map.of(SALARY_FIELD, salaryMasked)),
                new CellSpec(DEPT_MANAGER, "GRANT", "ledger", "write", DataScopeKind.DEPT_AND_CHILDREN,
                        Map.of(SALARY_FIELD, salaryMasked)),
                new CellSpec(DEPT_MANAGER, "GRANT", "form", "read", DataScopeKind.DEPT_AND_CHILDREN, Map.of()),
                new CellSpec(DEPT_MANAGER, "GRANT", "form", "write", DataScopeKind.DEPT_AND_CHILDREN, Map.of()),
                new CellSpec(DEPT_MANAGER, "GRANT", SurfaceCatalog.EXPORT_RESOURCE, SurfaceCatalog.EXPORT_ACTION,
                        DataScopeKind.DEPT_AND_CHILDREN, Map.of()),
                new CellSpec(DEPT_MANAGER, "GRANT", SurfaceCatalog.JOB_RESOURCE, SurfaceCatalog.JOB_ACTION,
                        DataScopeKind.DEPT_AND_CHILDREN, Map.of()),
                new CellSpec(DEPT_MANAGER, "GRANT", SurfaceCatalog.TASK_RESOURCE, SurfaceCatalog.TASK_ACTION,
                        DataScopeKind.DEPT_AND_CHILDREN, Map.of()),
                new CellSpec(APPROVER, "GRANT", "process.task", "read", DataScopeKind.ALL, Map.of()),
                new CellSpec(APPROVER, "GRANT", "process.task", "approve", DataScopeKind.ALL, Map.of()),
                new CellSpec(APPROVER, "GRANT", "process.task", "reject", DataScopeKind.ALL, Map.of()),
                new CellSpec(APPROVER, "GRANT", "process.instance", "start", DataScopeKind.ALL, Map.of()),
                new CellSpec(FORM_DESIGNER, "GRANT", "form", "read", DataScopeKind.ALL, Map.of()),
                new CellSpec(FORM_DESIGNER, "GRANT", "form", "write", DataScopeKind.ALL, Map.of()));
    }
}
