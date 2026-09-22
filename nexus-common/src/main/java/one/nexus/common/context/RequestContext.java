package one.nexus.common.context;

import java.util.Objects;
import java.util.Optional;

/**
 * Thread-local request/job scope. M0-02 should set and clear this on every
 * entry (HTTP, Quartz, Outbox, task queue). Never inherit a previous tenant.
 */
public final class RequestContext {
    private static final ThreadLocal<Holder> HOLDER = new ThreadLocal<>();

    private RequestContext() {}

    public record Holder(String traceId, Actor actor, TenantContext tenant, PlatformContext platform) {
        public Holder {
            if (traceId == null || traceId.isBlank()) {
                throw new IllegalArgumentException("traceId is required");
            }
        }
    }

    public static void set(Holder holder) {
        HOLDER.set(Objects.requireNonNull(holder, "holder"));
    }

    public static Optional<Holder> current() {
        return Optional.ofNullable(HOLDER.get());
    }

    public static Optional<TenantContext> tenant() {
        return current().map(Holder::tenant);
    }

    public static Optional<PlatformContext> platform() {
        return current().map(Holder::platform);
    }

    public static Optional<Actor> actor() {
        return current().map(Holder::actor);
    }

    public static String traceIdOr(String fallback) {
        return current().map(Holder::traceId).orElse(fallback);
    }

    public static void clear() {
        HOLDER.remove();
    }
}
