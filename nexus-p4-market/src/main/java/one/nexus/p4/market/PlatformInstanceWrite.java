package one.nexus.p4.market;

import java.util.UUID;
import one.nexus.p1.infra.isolation.IsolationContextHolder;
import one.nexus.p1.port.IsolationGuard.IsolationSnapshot;
import one.nexus.permission.pack.ApprovedTenantWrite;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * PLB8: platform-side instance upgrade (publish-time auto upgrade). Not an HTTP body.
 * Stays valid only while the platform snapshot that minted it is still bound.
 */
final class PlatformInstanceWrite implements ApprovedTenantWrite {
    private final UUID tenantId;
    private final UUID actorId;
    private final String traceId;
    private final IsolationSnapshot snapshot;

    PlatformInstanceWrite(UUID tenantId) {
        IsolationSnapshot current = IsolationContextHolder.get();
        if (current == null || current.platform() == null || !current.platform().active() || current.actor() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "platform context required");
        }
        this.tenantId = tenantId;
        this.actorId = current.actorId();
        this.traceId = current.traceId() == null || current.traceId().isBlank() ? "plb8" : current.traceId();
        this.snapshot = current;
    }

    @Override public UUID targetTenantId() { assertActive(); return tenantId; }
    @Override public UUID actorId() { assertActive(); return actorId; }
    @Override public String traceId() { assertActive(); return traceId; }

    @Override
    public void assertActive() {
        if (IsolationContextHolder.get() != snapshot) {
            throw new SecurityException("platform instance write is outside its issuing request");
        }
    }
}
