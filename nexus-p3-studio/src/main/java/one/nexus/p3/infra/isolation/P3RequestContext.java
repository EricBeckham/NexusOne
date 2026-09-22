package one.nexus.p3.infra.isolation;

import java.util.UUID;
import one.nexus.p3.domain.P3Exception;

public final class P3RequestContext {
    private static final ThreadLocal<Snapshot> HOLDER = new ThreadLocal<>();

    private P3RequestContext() {}

    public static void set(Snapshot snapshot) {
        HOLDER.set(snapshot);
    }

    public static Snapshot get() {
        return HOLDER.get();
    }

    public static Snapshot require() {
        Snapshot snapshot = HOLDER.get();
        if (snapshot == null || snapshot.tenantId() == null || snapshot.tenantCode() == null) {
            throw P3Exception.missingTenant();
        }
        return snapshot;
    }

    public static void clear() {
        HOLDER.remove();
    }

    public record Snapshot(UUID tenantId, String tenantCode, String actorId, String traceId) {}
}
