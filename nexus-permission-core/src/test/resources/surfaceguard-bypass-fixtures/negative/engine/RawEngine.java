package one.nexus.permission.scan.fixtures;

import one.nexus.permission.engine.PermissionEngine;
import one.nexus.permission.engine.PolicySnapshot;
import one.nexus.permission.engine.Subject;

/** Intentional fixture: product surface calls PermissionEngine.evaluate. */
public final class RawEngine {
    private RawEngine() {}

    public static boolean allow(Subject subject, PolicySnapshot snapshot) {
        return PermissionEngine.evaluate(subject, "t1", "ledger", "read", snapshot).allowed();
    }
}
