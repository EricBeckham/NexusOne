package one.nexus.isolation.context;

import java.util.Objects;
import java.util.concurrent.Callable;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;

/** Immutable capture of a server-issued context for async / queue hop. */
public final class ContextSnapshot {
    private final AccessContext context;

    private ContextSnapshot(AccessContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    public static ContextSnapshot of(AccessContext context) {
        if (context == null) {
            throw new IsolationException(IsolationErrorCode.CONTEXT_PROPAGATION_MISSING, "missing-trace");
        }
        return new ContextSnapshot(context);
    }

    public AccessContext context() {
        return context;
    }

    public <T> T call(Callable<T> task) {
        return AccessContextHolder.call(context, task);
    }

    public void run(Runnable task) {
        AccessContextHolder.run(context, task);
    }
}
