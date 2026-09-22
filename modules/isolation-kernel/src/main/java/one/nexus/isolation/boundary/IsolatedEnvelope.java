package one.nexus.isolation.boundary;

import java.util.Objects;
import java.util.function.Consumer;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.context.AccessContextHolder;

/** Outbox / task envelope. Tenant and trace must travel; mismatch is rejected before dispatch. */
public record IsolatedEnvelope(String tenantId, String traceId, String payloadType, String payload) {
    public IsolatedEnvelope {
        tenantId = require(tenantId, "tenantId");
        traceId = require(traceId, "traceId");
        payloadType = require(payloadType, "payloadType");
        Objects.requireNonNull(payload, "payload");
    }

    public static IsolatedEnvelope fromCurrent(String payloadType, String payload) {
        AccessContext context = AccessContextHolder.current();
        String tenant = context instanceof TenantContext tenantContext
                ? tenantContext.tenantId()
                : "platform";
        return new IsolatedEnvelope(tenant, context.traceId(), payloadType, payload);
    }

    public void dispatch(Consumer<IsolatedEnvelope> dispatcher) {
        AccessContext current = AccessContextHolder.currentOrNull();
        if (current == null) {
            throw new IsolationException(IsolationErrorCode.CONTEXT_PROPAGATION_MISSING, traceId);
        }
        if (current instanceof TenantContext tenant && !tenant.tenantId().equals(tenantId)) {
            throw new IsolationException(IsolationErrorCode.CROSS_TENANT_ASYNC, current.traceId());
        }
        dispatcher.accept(this);
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IsolationException(IsolationErrorCode.CONTEXT_PROPAGATION_MISSING, "missing-trace", name + " required");
        }
        return value;
    }
}
