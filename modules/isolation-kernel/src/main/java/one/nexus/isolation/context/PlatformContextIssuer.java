package one.nexus.isolation.context;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.PlatformContext;
import one.nexus.isolation.audit.IsolationAuditor;

/** Whitelist + audit issuance for {@link PlatformContext}. There is no client header path. */
public final class PlatformContextIssuer {
    private final Set<String> allowedPaths;
    private final IsolationAuditor auditor;
    private final Clock clock;

    public PlatformContextIssuer(Set<String> allowedPaths, IsolationAuditor auditor) {
        this(allowedPaths, auditor, Clock.systemUTC());
    }

    public PlatformContextIssuer(Set<String> allowedPaths, IsolationAuditor auditor, Clock clock) {
        this.allowedPaths = Set.copyOf(Objects.requireNonNull(allowedPaths, "allowedPaths"));
        this.auditor = Objects.requireNonNull(auditor, "auditor");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PlatformContext issue(String path, Actor actor, String reason, String traceId) {
        if (path == null || !allowedPaths.contains(path)) {
            throw auditor.reject(IsolationErrorCode.ILLEGAL_PLATFORM_ACCESS, traceId, actor == null ? null : actor.actorId(), path);
        }
        if (actor == null) {
            throw auditor.reject(IsolationErrorCode.ILLEGAL_PLATFORM_ACCESS, traceId, null, "actor required");
        }
        if (reason == null || reason.isBlank() || traceId == null || traceId.isBlank()) {
            throw auditor.reject(
                    IsolationErrorCode.ILLEGAL_PLATFORM_ACCESS,
                    traceId,
                    actor.actorId(),
                    "reason and trace required"
            );
        }
        PlatformContext context = new PlatformContext(path, actor, reason.trim(), traceId.trim(), Instant.now(clock));
        auditor.platformIssued(context);
        return context;
    }

    public boolean allows(String path) {
        return path != null && allowedPaths.contains(path);
    }
}
