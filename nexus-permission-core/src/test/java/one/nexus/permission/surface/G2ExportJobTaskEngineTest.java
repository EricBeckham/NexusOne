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
import one.nexus.permission.engine.DepartmentCatalog;
import one.nexus.permission.engine.PermissionEngine;
import one.nexus.permission.engine.PolicySnapshot;
import one.nexus.permission.engine.Predicate;
import one.nexus.permission.engine.Rule;
import one.nexus.permission.engine.Subject;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.Test;

/**
 * G2 remainder: export Task type + Job/Task submit share the same
 * PermissionEngine Decision / SurfaceGuard as list/detail/flow.
 */
class G2ExportJobTaskEngineTest {
    private static final String TENANT = "t-g2-export";
    private static final Subject SELF_READER = new Subject(
            TENANT, "person-1", Set.of(RoleMatrixCatalog.SELF_READER), Set.of(), Set.of("d1"));
    private static final Subject MANAGER = new Subject(
            TENANT, "person-2", Set.of(RoleMatrixCatalog.DEPT_MANAGER), Set.of(), Set.of("d1"));

    @Test
    void exportJobAndTaskShareSurfaceGuardWithListDetailFlow() {
        PolicySnapshot snapshot = snapshotFromCatalog();

        Decision listRead = PermissionEngine.evaluate(
                SELF_READER, TENANT, "ledger", "read", snapshot, List.of(), Predicate.TRUE, DepartmentCatalog.EMPTY);
        Decision exportDenied = PermissionEngine.evaluate(
                SELF_READER, TENANT, SurfaceCatalog.EXPORT_RESOURCE, SurfaceCatalog.EXPORT_ACTION, snapshot);
        Decision jobDenied = PermissionEngine.evaluate(
                SELF_READER, TENANT, SurfaceCatalog.JOB_RESOURCE, SurfaceCatalog.JOB_ACTION, snapshot);
        Decision taskDenied = PermissionEngine.evaluate(
                SELF_READER, TENANT, SurfaceCatalog.TASK_RESOURCE, SurfaceCatalog.TASK_ACTION, snapshot);
        Decision flowDenied = PermissionEngine.evaluate(
                SELF_READER, TENANT, "process.instance", "start", snapshot);

        assertTrue(listRead.allowed());
        assertFalse(exportDenied.allowed());
        assertEquals("no_grant", exportDenied.reason());
        assertEquals(exportDenied.reason(), jobDenied.reason());
        assertEquals(exportDenied.reason(), taskDenied.reason());
        assertEquals(exportDenied.reason(), flowDenied.reason());
        PermissionException export = assertThrows(
                PermissionException.class, () -> SurfaceGuard.requireAllowed(exportDenied));
        PermissionException job = assertThrows(
                PermissionException.class, () -> SurfaceGuard.requireAllowed(jobDenied));
        PermissionException task = assertThrows(
                PermissionException.class, () -> SurfaceGuard.requireAllowed(taskDenied));
        assertEquals("P2.PERM.0003", export.errorCode().code());
        assertEquals(export.errorCode().code(), job.errorCode().code());
        assertEquals(export.errorCode().code(), task.errorCode().code());
        assertEquals("PermissionEngine", SurfaceGuard.asApi(exportDenied).get("engine"));
        assertEquals(SurfaceGuard.asApi(listRead).get("engine"), SurfaceGuard.asApi(exportDenied).get("engine"));
    }

    @Test
    void deptManagerAllowsExportJobAndTaskOnSameEngine() {
        PolicySnapshot snapshot = snapshotFromCatalog();

        Decision export = PermissionEngine.evaluate(
                MANAGER, TENANT, SurfaceCatalog.EXPORT_RESOURCE, SurfaceCatalog.EXPORT_ACTION, snapshot);
        Decision job = PermissionEngine.evaluate(
                MANAGER, TENANT, SurfaceCatalog.JOB_RESOURCE, SurfaceCatalog.JOB_ACTION, snapshot);
        Decision task = PermissionEngine.evaluate(
                MANAGER, TENANT, SurfaceCatalog.TASK_RESOURCE, SurfaceCatalog.TASK_ACTION, snapshot);
        Decision read = PermissionEngine.evaluate(MANAGER, TENANT, "ledger", "read", snapshot);

        assertTrue(export.allowed());
        assertTrue(job.allowed());
        assertTrue(task.allowed());
        assertEquals(read.scope(), export.scope());
        SurfaceGuard.requireAllowed(export);
        SurfaceGuard.requireAllowed(job);
        SurfaceGuard.requireAllowed(task);
        assertEquals("PermissionEngine", SurfaceGuard.asApi(export).get("engine"));
        assertEquals(SurfaceCatalog.EXPORT_TASK_TYPE, SurfaceCatalog.EXPORT_RESOURCE);
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
        return new PolicySnapshot(TENANT, 8, grants, List.of(), Map.of(), Instant.parse("2026-09-22T00:00:00Z"));
    }
}
