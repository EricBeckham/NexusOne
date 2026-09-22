package one.nexus.event.isolation;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;
import one.nexus.isolation.api.PlatformContext;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.isolation.boundary.IsolatedExecutor;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.context.ContextSnapshot;
import one.nexus.isolation.context.RequestIsolationPolicy;
import one.nexus.isolation.query.TableRegistry;

/**
 * Execution-channel binding that only calls M0-02 APIs. Job/task/outbox code must
 * not invent another tenant holder.
 */
public final class ExecutionIsolation {
    public static final String RECONCILE_PATH = "exec.reconcile";
    public static final String QUARTZ_PATH = "exec.quartz";
    public static final String OUTBOX_PATH = "exec.outbox";

    private final IsolationKernel kernel;

    public ExecutionIsolation(IsolationKernel kernel) {
        this.kernel = Objects.requireNonNull(kernel, "kernel");
    }

    public static ExecutionIsolation create() {
        return new ExecutionIsolation(IsolationKernel.create(
                new TableRegistry(),
                Set.of(RECONCILE_PATH, QUARTZ_PATH, OUTBOX_PATH),
                new Slf4jIsolationAuditSink()));
    }

    public static ExecutionIsolation createForTests(CollectingAuditSink sink) {
        return new ExecutionIsolation(IsolationKernel.create(
                new TableRegistry(),
                Set.of(RECONCILE_PATH, QUARTZ_PATH, OUTBOX_PATH),
                sink));
    }

    public IsolationKernel kernel() {
        return kernel;
    }

    public RequestIsolationPolicy requests() {
        return kernel.requests();
    }

    public AccessContext requireBound() {
        return kernel.requests().requireBound();
    }

    public ContextSnapshot capture() {
        return AccessContextHolder.capture();
    }

    public IsolatedEnvelope envelopeFromCurrent(String payloadType, String payload) {
        requireBound();
        return IsolatedEnvelope.fromCurrent(payloadType, payload);
    }

    public StoredMembership requireTenantMembership() {
        AccessContext context = requireBound();
        if (!(context instanceof TenantContext tenant)) {
            throw new IsolationException(IsolationErrorCode.MISSING_CONTEXT, context.traceId());
        }
        return StoredMembership.from(tenant);
    }

    public TenantContext restoreTenant(StoredMembership stored, String traceId) {
        return kernel.requests().bindTenant(
                stored.toMembership(),
                stored.tenantId(),
                Optional.empty(),
                traceId);
    }

    public PlatformContext restorePlatform(String path, Actor actor, String reason, String traceId) {
        return kernel.platform().issue(path, actor, reason, traceId);
    }

    public void runRestored(StoredMembership stored, IsolatedEnvelope envelope, Consumer<IsolatedEnvelope> body) {
        TenantContext context = restoreTenant(stored, envelope.traceId());
        AccessContextHolder.run(context, () -> {
            IsolatedExecutor.requirePayloadTenant(AccessContextHolder.current(), envelope.tenantId());
            envelope.dispatch(body);
        });
    }

    public TenantContext issueAndBind(StoredMembership stored, String traceId) {
        TenantContext context = restoreTenant(stored, traceId);
        AccessContextHolder.set(context);
        return context;
    }
}
