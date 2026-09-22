package one.nexus.isolation.context;

import java.util.Objects;
import java.util.Optional;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.audit.IsolationAuditor;

/**
 * HTTP/request binder used by M0-01 filters and M0-04 login/session.
 * Client {@code X-Nexus-Tenant-Id} is a hint, never a credential.
 */
public final class RequestIsolationPolicy {
    public static final String CLIENT_TENANT_HEADER = "X-Nexus-Tenant-Id";

    private final TenantContextIssuer tenantIssuer;
    private final IsolationAuditor auditor;

    public RequestIsolationPolicy(TenantContextIssuer tenantIssuer, IsolationAuditor auditor) {
        this.tenantIssuer = Objects.requireNonNull(tenantIssuer, "tenantIssuer");
        this.auditor = Objects.requireNonNull(auditor, "auditor");
    }

    /**
     * Bind a tenant context from server-side session membership.
     *
     * @param membership           required; from session/P2, not from the request body
     * @param sessionTenantId      tenant already bound on the server session
     * @param clientClaimedTenantId optional header/query claim; rejected unless it matches an authorized membership
     */
    public TenantContext bindTenant(
            AuthenticatedMembership membership,
            String sessionTenantId,
            Optional<String> clientClaimedTenantId,
            String traceId
    ) {
        if (membership == null) {
            throw auditor.reject(IsolationErrorCode.MISSING_CONTEXT, traceId, null, "membership required");
        }
        if (sessionTenantId == null || sessionTenantId.isBlank()) {
            throw auditor.reject(IsolationErrorCode.MISSING_CONTEXT, traceId, membership.actor().actorId(), "session tenant required");
        }
        if (!membership.authorizes(sessionTenantId)) {
            throw auditor.reject(
                    IsolationErrorCode.MEMBERSHIP_DENIED,
                    traceId,
                    membership.actor().actorId(),
                    "session tenant is not a membership"
            );
        }
        AuthenticatedMembership sessionBound = new AuthenticatedMembership(
                membership.actor(),
                sessionTenantId.trim(),
                membership.membershipId(),
                membership.authorizedTenantIds()
        );
        if (clientClaimedTenantId != null && clientClaimedTenantId.isPresent()) {
            String claimed = clientClaimedTenantId.get();
            if (claimed == null || claimed.isBlank()) {
                return tenantIssuer.issue(sessionBound, traceId);
            }
            if (!claimed.trim().equals(sessionBound.tenantId())) {
                if (!sessionBound.authorizes(claimed)) {
                    throw auditor.reject(
                            IsolationErrorCode.CLIENT_TENANT_UNTRUSTED,
                            traceId,
                            membership.actor().actorId(),
                            "client tenant claim is not an authorized membership"
                    );
                }
                // Explicit tenant switch is still server-authorized membership, not a raw header credential.
                return tenantIssuer.issueSelectingAuthorizedTenant(sessionBound, claimed, traceId);
            }
        }
        return tenantIssuer.issue(sessionBound, traceId);
    }

    public static Optional<String> clientClaimFromHeaders(java.util.Map<String, String> headers) {
        if (headers == null) {
            return Optional.empty();
        }
        for (var entry : headers.entrySet()) {
            if (entry.getKey() != null && CLIENT_TENANT_HEADER.equalsIgnoreCase(entry.getKey())) {
                return Optional.ofNullable(entry.getValue());
            }
        }
        return Optional.empty();
    }

    public AccessContext requireBound() {
        return AccessContextHolder.current();
    }
}
