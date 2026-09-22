package one.nexus.permission.scan.fixtures;

import one.nexus.permission.PermissionService;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.surface.SurfaceGuard;

/** Positive fixture: the same SurfaceGuard interpretation as product surfaces. */
public final class Guarded {
    private Guarded() {}

    public static Decision read(PermissionService service) {
        return SurfaceGuard.require(service, "ledger", "read");
    }
}
