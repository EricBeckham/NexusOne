package one.nexus.permission.scan.fixtures;

import one.nexus.permission.engine.Decision;

/** Intentional fixture: invents an allow Decision without SurfaceGuard. */
public final class AlwaysAllow {
    private AlwaysAllow() {}

    public static Decision skip() {
        return new Decision(true, "always_allow");
    }
}
