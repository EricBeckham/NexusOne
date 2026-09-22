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
import one.nexus.permission.engine.PermissionEngine;
import one.nexus.permission.engine.PolicySnapshot;
import one.nexus.permission.engine.Rule;
import one.nexus.permission.engine.Subject;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.Test;

/**
 * Report query / export cells are the same PermissionEngine Decision as
 * desktop list and AuthorizedExport. Not a product-wide G2 close.
 */
class G2ReportSameEngineTest {
    private static final String TENANT = "t-g2-report";
    private static final Subject SELF_READER = new Subject(
            TENANT, "person-1", Set.of(RoleMatrixCatalog.SELF_READER), Set.of(), Set.of("d1"));
    private static final Subject MANAGER = new Subject(
            TENANT, "person-2", Set.of(RoleMatrixCatalog.DEPT_MANAGER), Set.of(), Set.of("d1"));

    @Test
    void reportQuerySharesDesktopListCodesAndExportSharesAuthorizedExport() {
        PolicySnapshot snapshot = snapshotFromCatalog();

        Decision list = PermissionEngine.evaluate(
                SELF_READER, TENANT, "ledger", "read", snapshot);
        Decision query = PermissionEngine.evaluate(
                SELF_READER, TENANT, ReportSurfaceCatalog.QUERY_RESOURCE, ReportSurfaceCatalog.QUERY_ACTION, snapshot);
        Decision exportDenied = PermissionEngine.evaluate(
                SELF_READER, TENANT, ReportSurfaceCatalog.EXPORT_RESOURCE, ReportSurfaceCatalog.EXPORT_ACTION, snapshot);
        Decision catalogExport = PermissionEngine.evaluate(
                SELF_READER, TENANT, SurfaceCatalog.EXPORT_RESOURCE, SurfaceCatalog.EXPORT_ACTION, snapshot);

        assertTrue(list.allowed());
        assertTrue(query.allowed());
        assertEquals(list.reason(), query.reason());
        assertFalse(exportDenied.allowed());
        assertEquals("no_grant", exportDenied.reason());
        assertEquals(exportDenied.reason(), catalogExport.reason());
        PermissionException export = assertThrows(
                PermissionException.class, () -> SurfaceGuard.requireAllowed(exportDenied));
        assertEquals("P2.PERM.0003", export.errorCode().code());
        assertEquals("PermissionEngine", SurfaceGuard.asApi(exportDenied).get("engine"));
        assertEquals(SurfaceGuard.asApi(list).get("engine"), SurfaceGuard.asApi(exportDenied).get("engine"));
        assertEquals(SurfaceCatalog.EXPORT_TASK_TYPE, ReportSurfaceCatalog.EXPORT_RESOURCE);
    }

    @Test
    void deptManagerAllowsReportQueryAndExportOnSameEngine() {
        PolicySnapshot snapshot = snapshotFromCatalog();
        Decision query = PermissionEngine.evaluate(
                MANAGER, TENANT, ReportSurfaceCatalog.QUERY_RESOURCE, ReportSurfaceCatalog.QUERY_ACTION, snapshot);
        Decision export = PermissionEngine.evaluate(
                MANAGER, TENANT, ReportSurfaceCatalog.EXPORT_RESOURCE, ReportSurfaceCatalog.EXPORT_ACTION, snapshot);
        Decision list = PermissionEngine.evaluate(MANAGER, TENANT, "ledger", "read", snapshot);

        SurfaceGuard.requireAllowed(query);
        SurfaceGuard.requireAllowed(export);
        assertTrue(list.allowed());
        assertEquals(list.scope(), query.scope());
        assertEquals("PermissionEngine", SurfaceGuard.asApi(export).get("engine"));
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
