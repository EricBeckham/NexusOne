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
 * Catalog definition cells are the same PermissionEngine Decision as
 * desktop list ({@code ledger}/{@code read|write}). Not a product-wide G2 close.
 */
class G2CatalogSameEngineTest {
    private static final String TENANT = "t-g2-catalog";
    private static final Subject SELF_READER = new Subject(
            TENANT, "person-1", Set.of(RoleMatrixCatalog.SELF_READER), Set.of(), Set.of("d1"));
    private static final Subject MANAGER = new Subject(
            TENANT, "person-2", Set.of(RoleMatrixCatalog.DEPT_MANAGER), Set.of(), Set.of("d1"));

    @Test
    void catalogReadSharesDesktopListCodesAndWriteSharesLedgerWrite() {
        PolicySnapshot snapshot = snapshotFromCatalog();

        Decision list = PermissionEngine.evaluate(
                SELF_READER, TENANT, "ledger", "read", snapshot);
        Decision catalogRead = PermissionEngine.evaluate(
                SELF_READER, TENANT, CatalogSurfaceCatalog.LEDGER_RESOURCE, CatalogSurfaceCatalog.LEDGER_READ, snapshot);
        Decision catalogWrite = PermissionEngine.evaluate(
                SELF_READER, TENANT, CatalogSurfaceCatalog.LEDGER_RESOURCE, CatalogSurfaceCatalog.LEDGER_WRITE, snapshot);
        Decision appRead = PermissionEngine.evaluate(
                SELF_READER, TENANT, CatalogSurfaceCatalog.APPLICATION_RESOURCE, CatalogSurfaceCatalog.APPLICATION_READ,
                snapshot);

        assertTrue(list.allowed());
        assertTrue(catalogRead.allowed());
        assertEquals(list.reason(), catalogRead.reason());
        assertEquals(list.reason(), appRead.reason());
        assertFalse(catalogWrite.allowed());
        assertEquals("no_grant", catalogWrite.reason());
        PermissionException denied = assertThrows(
                PermissionException.class, () -> SurfaceGuard.requireAllowed(catalogWrite));
        assertEquals("P2.PERM.0003", denied.errorCode().code());
        assertEquals("PermissionEngine", SurfaceGuard.asApi(catalogRead).get("engine"));
    }

    @Test
    void deptManagerAllowsCatalogReadAndWriteOnSameEngine() {
        PolicySnapshot snapshot = snapshotFromCatalog();
        Decision read = PermissionEngine.evaluate(
                MANAGER, TENANT, CatalogSurfaceCatalog.BUSINESS_TYPE_RESOURCE, CatalogSurfaceCatalog.BUSINESS_TYPE_READ,
                snapshot);
        Decision write = PermissionEngine.evaluate(
                MANAGER, TENANT, CatalogSurfaceCatalog.BUSINESS_TYPE_RESOURCE, CatalogSurfaceCatalog.BUSINESS_TYPE_WRITE,
                snapshot);
        Decision list = PermissionEngine.evaluate(MANAGER, TENANT, "ledger", "read", snapshot);
        SurfaceGuard.requireAllowed(read);
        SurfaceGuard.requireAllowed(write);
        assertTrue(list.allowed());
        assertEquals(list.scope(), read.scope());
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
