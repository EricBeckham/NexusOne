package one.nexus.p4.api;

import one.nexus.permission.engine.PermissionEngine;
import one.nexus.permission.engine.PolicySnapshot;
import one.nexus.permission.engine.Subject;

/** Mini-product market skip: PermissionEngine.evaluate without SurfaceGuard. */
public final class RawEngineSkip {
    private RawEngineSkip() {}

    public static boolean allow(Subject subject, PolicySnapshot snapshot) {
        return PermissionEngine.can(subject, "ledger", "read", snapshot);
    }
}
