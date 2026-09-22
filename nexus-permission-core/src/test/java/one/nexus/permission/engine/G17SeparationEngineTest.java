package one.nexus.permission.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import one.nexus.permission.admin.PlatformDutyCatalog;
import one.nexus.permission.surface.SurfaceGuard;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.Test;

/**
 * G17 slice: three mutually exclusive platform duties evaluated by
 * {@link PermissionEngine}. Audit-admin can read; write/delete/admin are deny.
 */
class G17SeparationEngineTest {
    private static final String TENANT = "t-g17";
    private static final Subject SYSTEM = subject(PlatformDutyCatalog.SYSTEM_ADMIN);
    private static final Subject SECURITY = subject(PlatformDutyCatalog.SECURITY_ADMIN);
    private static final Subject AUDIT = subject(PlatformDutyCatalog.AUDIT_ADMIN);
    private static final Subject CONFLICTED = new Subject(
            TENANT, "person-x", Set.of(PlatformDutyCatalog.SYSTEM_ADMIN, PlatformDutyCatalog.AUDIT_ADMIN),
            Set.of(), Set.of());

    @Test
    void catalogGrantSetsAreDisjointAndAuditAdminCannotMutateAudit() {
        PolicySnapshot snapshot = snapshotFromCatalog();

        Decision tenantCreate = PermissionEngine.evaluate(SYSTEM, TENANT, "tenant", "create", snapshot);
        Decision securityPolicy = PermissionEngine.evaluate(SECURITY, TENANT, "security.policy", "write", snapshot);
        Decision auditRead = PermissionEngine.evaluate(AUDIT, TENANT, "audit", "read", snapshot);
        Decision auditWrite = PermissionEngine.evaluate(AUDIT, TENANT, "audit", "write", snapshot);
        Decision auditDelete = PermissionEngine.evaluate(AUDIT, TENANT, "audit", "delete", snapshot);
        Decision auditAdmin = PermissionEngine.evaluate(AUDIT, TENANT, "audit", "admin", snapshot);
        Decision auditConfig = PermissionEngine.evaluate(AUDIT, TENANT, "audit.config", "write", snapshot);
        Decision auditMute = PermissionEngine.evaluate(AUDIT, TENANT, "audit.chain", "mute", snapshot);

        assertTrue(tenantCreate.allowed());
        assertTrue(securityPolicy.allowed());
        assertTrue(auditRead.allowed());
        assertEquals("allowed", auditRead.reason());
        assertFalse(auditWrite.allowed());
        assertEquals("explicit_deny", auditWrite.reason());
        assertEquals("explicit_deny", auditDelete.reason());
        assertEquals("explicit_deny", auditAdmin.reason());
        assertEquals("explicit_deny", auditConfig.reason());
        assertEquals("explicit_deny", auditMute.reason());

        assertFalse(PermissionEngine.evaluate(SYSTEM, TENANT, "audit", "read", snapshot).allowed());
        assertFalse(PermissionEngine.evaluate(SECURITY, TENANT, "tenant", "create", snapshot).allowed());
        assertFalse(PermissionEngine.evaluate(AUDIT, TENANT, "permission.grant", "write", snapshot).allowed());
        SurfaceGuard.requireAllowed(auditRead);
        PermissionException denied = assertThrows(
                PermissionException.class, () -> SurfaceGuard.requireAllowed(auditWrite));
        assertEquals("P2.PERM.0002", denied.errorCode().code());
    }

    @Test
    void conflictingDutyPairIsDeniedOnEvaluate() {
        PolicySnapshot snapshot = snapshotFromCatalog();
        Decision read = PermissionEngine.evaluate(CONFLICTED, TENANT, "audit", "read", snapshot);
        Decision create = PermissionEngine.evaluate(CONFLICTED, TENANT, "tenant", "create", snapshot);

        assertFalse(read.allowed());
        assertEquals("duty_conflict", read.reason());
        assertEquals("duty_conflict", create.reason());
        PermissionException denied = assertThrows(
                PermissionException.class, () -> SurfaceGuard.requireAllowed(read));
        assertEquals("P2.PERM.0014", denied.errorCode().code());
        assertEquals(403, denied.httpStatus());
    }

    @Test
    void exclusivePairsCoverEveryAdminCombination() {
        assertEquals(3, PlatformDutyCatalog.exclusivePairs().size());
        assertTrue(PlatformDutyCatalog.conflicting(
                Set.of(PlatformDutyCatalog.SYSTEM_ADMIN, PlatformDutyCatalog.SECURITY_ADMIN)));
        assertTrue(PlatformDutyCatalog.conflicting(
                Set.of(PlatformDutyCatalog.SYSTEM_ADMIN, PlatformDutyCatalog.AUDIT_ADMIN)));
        assertTrue(PlatformDutyCatalog.conflicting(
                Set.of(PlatformDutyCatalog.SECURITY_ADMIN, PlatformDutyCatalog.AUDIT_ADMIN)));
        assertFalse(PlatformDutyCatalog.conflicting(Set.of(PlatformDutyCatalog.AUDIT_ADMIN)));
        assertFalse(PlatformDutyCatalog.conflicting(Set.of("tenant_member")));
    }

    private static Subject subject(String role) {
        return new Subject(TENANT, "person-" + role, Set.of(role), Set.of(), Set.of());
    }

    private static PolicySnapshot snapshotFromCatalog() {
        List<Rule> grants = PlatformDutyCatalog.cells().stream()
                .filter(cell -> "GRANT".equalsIgnoreCase(cell.effect()))
                .map(cell -> Rule.scoped(
                        "role", cell.roleCode(), cell.resource(), cell.action(),
                        cell.scopeKind(), List.of(), Map.of()))
                .toList();
        List<Rule> denies = PlatformDutyCatalog.cells().stream()
                .filter(cell -> "DENY".equalsIgnoreCase(cell.effect()))
                .map(cell -> Rule.scoped(
                        "role", cell.roleCode(), cell.resource(), cell.action(),
                        cell.scopeKind(), List.of(), Map.of()))
                .toList();
        return new PolicySnapshot(TENANT, 3, grants, denies, Map.of(), Instant.parse("2026-09-22T00:00:00Z"));
    }
}
