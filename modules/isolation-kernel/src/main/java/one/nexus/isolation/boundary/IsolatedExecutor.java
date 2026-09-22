package one.nexus.isolation.boundary;

import java.util.Objects;
import java.util.concurrent.Executor;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.context.ContextSnapshot;

/**
 * Captures tenant/platform context at submit time and restores it before the task body.
 * Missing context is rejected before the delegate executor runs the work.
 */
public final class IsolatedExecutor implements Executor {
    private final Executor delegate;

    public IsolatedExecutor(Executor delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void execute(Runnable command) {
        ContextSnapshot snapshot = AccessContextHolder.capture();
        delegate.execute(() -> snapshot.run(command));
    }

    public static Runnable wrap(Runnable command) {
        ContextSnapshot snapshot = AccessContextHolder.capture();
        return () -> snapshot.run(command);
    }

    public static void requirePayloadTenant(AccessContext current, String payloadTenantId) {
        if (current == null) {
            throw new IsolationException(IsolationErrorCode.CONTEXT_PROPAGATION_MISSING, "missing-trace");
        }
        SideEffectGuard.assertSameTenant(current, payloadTenantId);
    }
}
