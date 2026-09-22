package one.nexus.p1.infra.isolation;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import one.nexus.p1.domain.DomainException;
import one.nexus.p1.domain.ErrorCode;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p1.port.PlatformContext;

@Component
public class ThreadLocalIsolationGuard implements IsolationGuard {

    @Override
    public PlatformContext requirePlatformContext(String operation, PlatformDuty duty) {
        IsolationSnapshot snapshot = current();
        PlatformContext platform = snapshot.platform();
        if (platform == null || !platform.active()) {
            throw new DomainException(
                    ErrorCode.CONTEXT_PLATFORM_REQUIRED,
                    "platform context required for " + operation,
                    Map.of("operation", operation));
        }
        if (platform.reason() == null || platform.reason().isBlank()) {
            throw new DomainException(
                    ErrorCode.CONTEXT_PLATFORM_REQUIRED,
                    "platform context requires a reason",
                    Map.of("operation", operation));
        }
        if (snapshot.actor() == null || !snapshot.actor().has(duty)) {
            throw new DomainException(
                    ErrorCode.DUTY_DENIED,
                    "platform duty " + duty + " required for " + operation,
                    Map.of("operation", operation, "duty", duty.name()));
        }
        return platform;
    }

    @Override
    public void requireTenantOrPlatform(UUID tenantId, String operation) {
        IsolationSnapshot snapshot = current();
        if (snapshot.platform() != null && snapshot.platform().active()) {
            requirePlatformContext(operation, PlatformDuty.SYSTEM_ADMIN);
            return;
        }
        if (snapshot.tenantId() == null || !snapshot.tenantId().equals(tenantId)) {
            throw new DomainException(
                    ErrorCode.CONTEXT_TENANT_MISMATCH,
                    "tenant context does not match target tenant",
                    Map.of("operation", operation, "tenant_id", tenantId.toString()));
        }
    }

    @Override
    public IsolationSnapshot current() {
        IsolationSnapshot snapshot = IsolationContextHolder.get();
        if (snapshot == null) {
            return new IsolationSnapshot(null, PlatformContext.inactive(), null, null);
        }
        return snapshot;
    }
}
