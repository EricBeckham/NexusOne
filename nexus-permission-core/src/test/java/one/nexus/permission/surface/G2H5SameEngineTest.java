package one.nexus.permission.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * H5 list/detail/form/todo cells are the same PermissionEngine Decision as
 * desktop. Not a product-wide G2 close.
 */
class G2H5SameEngineTest {
    private static final String TENANT = "t-g2-h5";
    private static final Subject SELF_READER = new Subject(
            TENANT, "person-1", Set.of(RoleMatrixCatalog.SELF_READER), Set.of(), Set.of("d1"));
    private static final Subject APPROVER = new Subject(
            TENANT, "person-1", Set.of(RoleMatrixCatalog.APPROVER), Set.of(), Set.of("d1"));

    @Test
    void h5ListFormAndTodoShareDesktopCodes() {
        PolicySnapshot snapshot = snapshotFromCatalog();

        Decision list = PermissionEngine.evaluate(
                SELF_READER, TENANT, H5SurfaceCatalog.LIST_RESOURCE, H5SurfaceCatalog.LIST_READ, snapshot);
        Decision formRead = PermissionEngine.evaluate(
                SELF_READER, TENANT, H5SurfaceCatalog.FORM_RESOURCE, H5SurfaceCatalog.FORM_READ, snapshot);
        Decision formWrite = PermissionEngine.evaluate(
                SELF_READER, TENANT, H5SurfaceCatalog.FORM_RESOURCE, H5SurfaceCatalog.FORM_WRITE, snapshot);
        Decision todoRead = PermissionEngine.evaluate(
                SELF_READER, TENANT, H5SurfaceCatalog.TODO_RESOURCE, H5SurfaceCatalog.TODO_READ, snapshot);

        assertTrue(list.allowed());
        assertTrue(formRead.allowed());
        assertFalse(formWrite.allowed());
        assertEquals("no_grant", formWrite.reason());
        assertFalse(todoRead.allowed());
        assertEquals("no_grant", todoRead.reason());
        PermissionException write = assertThrows(
                PermissionException.class, () -> SurfaceGuard.requireAllowed(formWrite));
        PermissionException todo = assertThrows(
                PermissionException.class, () -> SurfaceGuard.requireAllowed(todoRead));
        assertEquals("P2.PERM.0003", write.errorCode().code());
        assertEquals("P2.PERM.0003", todo.errorCode().code());
        assertEquals("PermissionEngine", SurfaceGuard.asApi(formWrite).get("engine"));
        assertEquals(SurfaceGuard.asApi(list).get("engine"), SurfaceGuard.asApi(todoRead).get("engine"));
    }

    @Test
    void approverAllowsH5TodoCellsAndStillDeniesList() {
        PolicySnapshot snapshot = snapshotFromCatalog();
        Decision todoRead = PermissionEngine.evaluate(
                APPROVER, TENANT, H5SurfaceCatalog.TODO_RESOURCE, H5SurfaceCatalog.TODO_READ, snapshot);
        Decision approve = PermissionEngine.evaluate(
                APPROVER, TENANT, H5SurfaceCatalog.TODO_RESOURCE, H5SurfaceCatalog.TODO_APPROVE, snapshot);
        Decision reject = PermissionEngine.evaluate(
                APPROVER, TENANT, H5SurfaceCatalog.TODO_RESOURCE, H5SurfaceCatalog.TODO_REJECT, snapshot);
        Decision list = PermissionEngine.evaluate(
                APPROVER, TENANT, H5SurfaceCatalog.LIST_RESOURCE, H5SurfaceCatalog.LIST_READ, snapshot);

        SurfaceGuard.requireAllowed(todoRead);
        SurfaceGuard.requireAllowed(approve);
        SurfaceGuard.requireAllowed(reject);
        assertFalse(list.allowed());
        PermissionException denied = assertThrows(
                PermissionException.class, () -> SurfaceGuard.requireAllowed(list));
        assertEquals("P2.PERM.0003", denied.errorCode().code());
        assertEquals("no_grant", denied.getMessage());
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
        return new PolicySnapshot(TENANT, 7, grants, List.of(), Map.of(), java.time.Instant.parse("2026-09-22T00:00:00Z"));
    }
}
