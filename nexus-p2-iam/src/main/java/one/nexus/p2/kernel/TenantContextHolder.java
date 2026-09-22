package one.nexus.p2.kernel;

/**
 * Request-scoped holder. M0-02 owns propagation across jobs/cache/files;
 * this module only sets it after membership-checked issuance.
 */
public final class TenantContextHolder {
    private static final ThreadLocal<TenantContext> CURRENT = new ThreadLocal<>();

    private TenantContextHolder() {}

    public static void set(TenantContext context) {
        CURRENT.set(context);
    }

    public static TenantContext get() {
        return CURRENT.get();
    }

    public static TenantContext require() {
        TenantContext context = CURRENT.get();
        if (context == null) {
            throw one.nexus.p2.support.DomainException.unauthorized("tenant context is not issued");
        }
        return context;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
