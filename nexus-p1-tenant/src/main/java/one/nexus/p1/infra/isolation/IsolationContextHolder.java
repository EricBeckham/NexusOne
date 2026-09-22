package one.nexus.p1.infra.isolation;

import one.nexus.p1.port.IsolationGuard.IsolationSnapshot;

/**
 * Thread-local holder used by the M0-03 stub. M0-02 should replace this with the kernel's
 * server-issued context (request + job + outbox propagation).
 */
public final class IsolationContextHolder {

    private static final ThreadLocal<IsolationSnapshot> CTX = new ThreadLocal<>();

    private IsolationContextHolder() {
    }

    public static void set(IsolationSnapshot snapshot) {
        CTX.set(snapshot);
    }

    public static IsolationSnapshot get() {
        return CTX.get();
    }

    public static void clear() {
        CTX.remove();
    }
}
