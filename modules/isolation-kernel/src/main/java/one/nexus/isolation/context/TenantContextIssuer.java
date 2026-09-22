package one.nexus.isolation.context;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.audit.IsolationAuditor;

/**
 * Issues {@link TenantContext} from server-side membership only.
 * A client-claimed tenant id is never the source of truth.
 */
public final class TenantContextIssuer {
    private final IsolationAuditor auditor;
    private final Clock clock;

    public TenantContextIssuer(IsolationAuditor auditor) {
        this(auditor, Clock.systemUTC());
    }

    public TenantContextIssuer(IsolationAuditor auditor, Clock clock) {
        this.auditor = Objects.requireNonNull(auditor, "auditor");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public TenantContext issue(AuthenticatedMembership membership, String traceId) {
        Objects.requireNonNull(membership, "membership");
        Instant now = Instant.now(clock);
        return new TenantContext(
                membership.tenantId(),
                membership.actor(),
                membership.membershipId(),
                requireTrace(traceId),
                now
        );
    }

    /**
     * Optional client hint may only select among already-authorized memberships.
     * The issued tenant id still comes from server membership, not from the raw header.
     */
    public TenantContext issueSelectingAuthorizedTenant(
            AuthenticatedMembership membership,
            String clientTenantHint,
            String traceId
    ) {
        Objects.requireNonNull(membership, "membership");
        if (clientTenantHint == null || clientTenantHint.isBlank()) {
            return issue(membership, traceId);
        }
        if (!membership.authorizes(clientTenantHint)) {
            throw auditor.reject(
                    IsolationErrorCode.MEMBERSHIP_DENIED,
                    requireTrace(traceId),
                    membership.actor().actorId(),
                    "client hint is not an authorized membership"
            );
        }
        AuthenticatedMembership selected = new AuthenticatedMembership(
                membership.actor(),
                clientTenantHint.trim(),
                membership.membershipId(),
                membership.authorizedTenantIds()
        );
        return issue(selected, traceId);
    }

    private static String requireTrace(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new one.nexus.isolation.api.IsolationException(
                    IsolationErrorCode.MISSING_CONTEXT,
                    "missing-trace",
                    "traceId required"
            );
        }
        return traceId.trim();
    }
}
