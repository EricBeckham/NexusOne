package one.nexus.isolation.api;

import java.time.Instant;
import java.util.Objects;

/**
 * Cross-tenant platform context. Constructed only by {@link one.nexus.isolation.context.PlatformContextIssuer}.
 */
public final class PlatformContext implements AccessContext {
    private final String path;
    private final Actor actor;
    private final String reason;
    private final String traceId;
    private final Instant issuedAt;

    /** Server issuance only. Business modules must not call this; CI forbids {@code new PlatformContext}. */
    public PlatformContext(String path, Actor actor, String reason, String traceId, Instant issuedAt) {
        this.path = Actor.requireToken(path, "path");
        this.actor = Objects.requireNonNull(actor, "actor");
        this.reason = Actor.requireToken(reason, "reason");
        this.traceId = Actor.requireToken(traceId, "traceId");
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
    }

    public String path() {
        return path;
    }

    public String reason() {
        return reason;
    }

    @Override
    public Actor actor() {
        return actor;
    }

    @Override
    public String traceId() {
        return traceId;
    }

    @Override
    public Instant issuedAt() {
        return issuedAt;
    }
}
