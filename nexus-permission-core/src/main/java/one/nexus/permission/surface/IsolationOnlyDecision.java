package one.nexus.permission.surface;

import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.support.PermissionException;

/**
 * Single constructor for the isolation-only {@code tenant_only} leftover.
 *
 * <p>{@code tenant_only} is allow <em>only</em> when no P2 {@code TenantContext}
 * is bound. When a P2 context exists, this helper fail-closes so callers cannot
 * silently skip {@link SurfaceGuard}. It does not evaluate grants and does
 * not redesign {@code PermissionEngine}.
 *
 * <p>Not a product-wide G2 close. Todo HTTP stays leftover. Physical dump is
 * the sibling leftover and also uses this constructor.
 */
public final class IsolationOnlyDecision {
    public static final String REASON = "tenant_only";
    public static final String FORBIDDEN = "tenant_only_forbidden";

    private IsolationOnlyDecision() {}

    public static boolean isolationOnly() {
        return TenantContextHolder.get() == null;
    }

    /**
     * Isolation leftover allow. Fail-closed when a P2 {@code TenantContext}
     * exists ({@code P2.PERM.0003} / {@link #FORBIDDEN}).
     */
    public static Decision allow() {
        if (!isolationOnly()) {
            throw PermissionException.denied(FORBIDDEN);
        }
        return new Decision(true, REASON);
    }
}
