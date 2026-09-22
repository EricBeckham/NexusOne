package one.nexus.isolation.context;

import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;

/**
 * Request/thread bound context. M0-01 binds after {@link RequestIsolationPolicy}; async work must capture a snapshot.
 */
public final class AccessContextHolder {
    private static final ThreadLocal<AccessContext> HOLDER = new ThreadLocal<>();

    private AccessContextHolder() {}

    public static void set(AccessContext context) {
        if (context == null) {
            throw new IsolationException(IsolationErrorCode.MISSING_CONTEXT, "missing-trace");
        }
        HOLDER.set(context);
    }

    public static AccessContext current() {
        AccessContext context = HOLDER.get();
        if (context == null) {
            throw new IsolationException(IsolationErrorCode.MISSING_CONTEXT, "missing-trace");
        }
        return context;
    }

    public static AccessContext currentOrNull() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static ContextSnapshot capture() {
        return ContextSnapshot.of(current());
    }

    public static <T> T call(AccessContext context, java.util.concurrent.Callable<T> task) {
        AccessContext previous = HOLDER.get();
        set(context);
        try {
            return task.call();
        } catch (IsolationException isolation) {
            throw isolation;
        } catch (Exception ex) {
            if (ex instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(ex);
        } finally {
            if (previous == null) {
                clear();
            } else {
                HOLDER.set(previous);
            }
        }
    }

    public static void run(AccessContext context, Runnable task) {
        call(context, () -> {
            task.run();
            return null;
        });
    }
}
