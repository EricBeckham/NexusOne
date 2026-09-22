package one.nexus.isolation.api;

import java.util.Objects;
import java.util.Set;

/**
 * Server-side membership fact from M0-04 / session. Client headers are not this type.
 */
public record AuthenticatedMembership(
        Actor actor,
        String tenantId,
        String membershipId,
        Set<String> authorizedTenantIds
) {
    public AuthenticatedMembership {
        Objects.requireNonNull(actor, "actor");
        tenantId = Actor.requireToken(tenantId, "tenantId");
        membershipId = Actor.requireToken(membershipId, "membershipId");
        authorizedTenantIds = Set.copyOf(Objects.requireNonNull(authorizedTenantIds, "authorizedTenantIds"));
        if (authorizedTenantIds.isEmpty()) {
            throw new IllegalArgumentException("authorizedTenantIds required");
        }
        if (!authorizedTenantIds.contains(tenantId)) {
            throw new IllegalArgumentException("tenantId must be in authorizedTenantIds");
        }
    }

    public boolean authorizes(String candidateTenantId) {
        return authorizedTenantIds.contains(candidateTenantId);
    }
}
