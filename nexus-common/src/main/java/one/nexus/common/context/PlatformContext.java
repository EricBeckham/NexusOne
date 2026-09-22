package one.nexus.common.context;

import java.util.Objects;

/**
 * Cross-tenant platform path. Must be issued server-side on a whitelist,
 * with actor, reason, and trace, and must be audited (M0-02 / M0-11).
 */
public record PlatformContext(String path, Actor actor, String reason, String traceId) {
    public PlatformContext {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        Objects.requireNonNull(actor, "actor");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId is required");
        }
    }
}
