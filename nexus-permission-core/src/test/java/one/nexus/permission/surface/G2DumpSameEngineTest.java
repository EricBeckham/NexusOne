package one.nexus.permission.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import one.nexus.permission.admin.RoleMatrixCatalog;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.engine.FieldRule;
import one.nexus.permission.engine.Mask;
import one.nexus.permission.engine.PermissionEngine;
import one.nexus.permission.engine.PolicySnapshot;
import one.nexus.permission.engine.Rule;
import one.nexus.permission.engine.Subject;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.Test;

/**
 * Physical dump / raw export cells are the same PermissionEngine Decision as
 * desktop list and AuthorizedExport. Not a product-wide G2 close.
 */
class G2DumpSameEngineTest {
    private static final String TENANT = "t-g2-dump";
    private static final Subject SELF_READER = new Subject(
            TENANT, "person-1", Set.of(RoleMatrixCatalog.SELF_READER), Set.of(), Set.of("d1"));
    private static final Subject MANAGER = new Subject(
            TENANT, "person-2", Set.of(RoleMatrixCatalog.DEPT_MANAGER), Set.of(), Set.of("d1"));

    @Test
    void dumpSharesDesktopExportCodesAndReadSharesList() {
        PolicySnapshot snapshot = snapshotFromCatalog();

        Decision list = PermissionEngine.evaluate(
                SELF_READER, TENANT, "ledger", "read", snapshot);
        Decision read = PermissionEngine.evaluate(
                SELF_READER, TENANT, DumpSurfaceCatalog.READ_RESOURCE, DumpSurfaceCatalog.READ_ACTION, snapshot);
        Decision dumpDenied = PermissionEngine.evaluate(
                SELF_READER, TENANT, DumpSurfaceCatalog.DUMP_RESOURCE, DumpSurfaceCatalog.DUMP_ACTION, snapshot);
        Decision catalogExport = PermissionEngine.evaluate(
                SELF_READER, TENANT, SurfaceCatalog.EXPORT_RESOURCE, SurfaceCatalog.EXPORT_ACTION, snapshot);

        assertTrue(list.allowed());
        assertTrue(read.allowed());
        assertEquals(list.reason(), read.reason());
        assertFalse(dumpDenied.allowed());
        assertEquals("no_grant", dumpDenied.reason());
        assertEquals(dumpDenied.reason(), catalogExport.reason());
        PermissionException dump = assertThrows(
                PermissionException.class, () -> SurfaceGuard.requireAllowed(dumpDenied));
        assertEquals("P2.PERM.0003", dump.errorCode().code());
        assertEquals("PermissionEngine", SurfaceGuard.asApi(dumpDenied).get("engine"));
        assertEquals(SurfaceCatalog.EXPORT_TASK_TYPE, DumpSurfaceCatalog.DUMP_RESOURCE);
    }

    @Test
    void deptManagerAllowsDumpOnSameEngineAndDropsHiddenSalary() {
        PolicySnapshot snapshot = snapshotFromCatalog();
        Decision dump = PermissionEngine.evaluate(
                MANAGER, TENANT, DumpSurfaceCatalog.DUMP_RESOURCE, DumpSurfaceCatalog.DUMP_ACTION, snapshot);
        Decision read = PermissionEngine.evaluate(
                MANAGER, TENANT, DumpSurfaceCatalog.READ_RESOURCE, DumpSurfaceCatalog.READ_ACTION, snapshot);
        Decision list = PermissionEngine.evaluate(MANAGER, TENANT, "ledger", "read", snapshot);

        SurfaceGuard.requireAllowed(dump);
        SurfaceGuard.requireAllowed(read);
        assertTrue(list.allowed());
        assertEquals(list.scope(), read.scope());
        assertEquals("PermissionEngine", SurfaceGuard.asApi(dump).get("engine"));

        List<String> columns = DumpFieldPolicies.visibleColumns(
                read, List.of(Map.of("id", "r1", "salary", "9", "tenant_id", TENANT)));
        assertTrue(columns.contains("id"));
        assertTrue(columns.contains("salary"), "dept manager salary is masked, still visible");
        assertFalse(columns.contains("tenant_id"));
    }

    @Test
    void selfReaderDumpDropsHiddenSalaryFromProjection() {
        PolicySnapshot snapshot = snapshotFromCatalog();
        Decision read = PermissionEngine.evaluate(
                SELF_READER, TENANT, DumpSurfaceCatalog.READ_RESOURCE, DumpSurfaceCatalog.READ_ACTION, snapshot);
        FieldRule salary = read.fields().get(RoleMatrixCatalog.SALARY_FIELD);
        assertFalse(salary.visible());
        List<String> columns = DumpFieldPolicies.visibleColumns(
                read, List.of(Map.of("id", "r1", "salary", "9")));
        assertEquals(List.of("id"), columns);
        assertEquals(Mask.FULL, salary.mask());
    }

    private static PolicySnapshot snapshotFromCatalog() {
        List<Rule> grants = RoleMatrixCatalog.exampleCells().stream()
                .filter(cell -> "GRANT".equalsIgnoreCase(cell.effect()))
                .map(cell -> Rule.scoped(
                        "role",
                        cell.roleCode(),
                        cell.resource(),
                        cell.action(),
                        cell.scopeKind(),
                        List.of(),
                        cell.fields()))
                .toList();
        return new PolicySnapshot(TENANT, 9, grants, List.of(), Map.of(), Instant.parse("2026-09-22T00:00:00Z"));
    }
}
