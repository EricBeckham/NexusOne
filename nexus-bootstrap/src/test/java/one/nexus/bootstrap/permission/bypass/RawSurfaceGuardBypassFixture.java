package one.nexus.bootstrap.permission.bypass;

import one.nexus.isolation.boundary.IsolatedExport;
import one.nexus.permission.engine.PermissionEngine;
import one.nexus.permission.engine.PolicySnapshot;
import one.nexus.permission.engine.Subject;

/**
 * Intentional ArchUnit fixture: calls PermissionEngine and IsolatedExport.
 * Not a product {@code src/main} class.
 */
public final class RawSurfaceGuardBypassFixture {
    private RawSurfaceGuardBypassFixture() {}

    public static boolean dump(Subject subject, PolicySnapshot snapshot, String tenantId) {
        IsolatedExport.submit(tenantId, "{\"ledger\":\"leave\"}");
        return PermissionEngine.can(subject, "ledger", "read", snapshot);
    }
}
