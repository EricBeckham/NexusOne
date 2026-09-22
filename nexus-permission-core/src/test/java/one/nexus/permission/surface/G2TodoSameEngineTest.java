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
 * Todo list HTTP cells are the same PermissionEngine Decision as H5
 * {@code process.task}/{@code read}. Not a product-wide G2 close.
 */
class G2TodoSameEngineTest {
    private static final String TENANT = "t-g2-todo";
    private static final Subject SELF_READER = new Subject(
            TENANT, "person-1", Set.of(RoleMatrixCatalog.SELF_READER), Set.of(), Set.of("d1"));
    private static final Subject APPROVER = new Subject(
            TENANT, "person-2", Set.of(RoleMatrixCatalog.APPROVER), Set.of(), Set.of("d1"));

    @Test
    void todoReadSharesH5TodoCodesAndDeniesSelfReader() {
        PolicySnapshot snapshot = snapshotFromCatalog();

        Decision h5Todo = PermissionEngine.evaluate(
                SELF_READER, TENANT, H5SurfaceCatalog.TODO_RESOURCE, H5SurfaceCatalog.TODO_READ, snapshot);
        Decision todoRead = PermissionEngine.evaluate(
                SELF_READER, TENANT, TodoSurfaceCatalog.TODO_RESOURCE, TodoSurfaceCatalog.TODO_READ, snapshot);
        Decision list = PermissionEngine.evaluate(SELF_READER, TENANT, "ledger", "read", snapshot);

        assertTrue(list.allowed());
        assertFalse(h5Todo.allowed());
        assertFalse(todoRead.allowed());
        assertEquals(h5Todo.reason(), todoRead.reason());
        assertEquals("no_grant", todoRead.reason());
        PermissionException denied = assertThrows(
                PermissionException.class, () -> SurfaceGuard.requireAllowed(todoRead));
        assertEquals("P2.PERM.0003", denied.errorCode().code());
        assertEquals("PermissionEngine", SurfaceGuard.asApi(todoRead).get("engine"));
        assertEquals(H5SurfaceCatalog.TODO_RESOURCE, TodoSurfaceCatalog.TODO_RESOURCE);
        assertEquals(H5SurfaceCatalog.TODO_READ, TodoSurfaceCatalog.TODO_READ);
    }

    @Test
    void approverAllowsTodoReadOnSameEngineAndStillDeniesList() {
        PolicySnapshot snapshot = snapshotFromCatalog();
        Decision todoRead = PermissionEngine.evaluate(
                APPROVER, TENANT, TodoSurfaceCatalog.TODO_RESOURCE, TodoSurfaceCatalog.TODO_READ, snapshot);
        Decision h5Todo = PermissionEngine.evaluate(
                APPROVER, TENANT, H5SurfaceCatalog.TODO_RESOURCE, H5SurfaceCatalog.TODO_READ, snapshot);
        Decision list = PermissionEngine.evaluate(APPROVER, TENANT, "ledger", "read", snapshot);

        SurfaceGuard.requireAllowed(todoRead);
        SurfaceGuard.requireAllowed(h5Todo);
        assertEquals(h5Todo.reason(), todoRead.reason());
        assertFalse(list.allowed());
        PermissionException denied = assertThrows(
                PermissionException.class, () -> SurfaceGuard.requireAllowed(list));
        assertEquals("P2.PERM.0003", denied.errorCode().code());
        assertEquals("PermissionEngine", SurfaceGuard.asApi(todoRead).get("engine"));
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
