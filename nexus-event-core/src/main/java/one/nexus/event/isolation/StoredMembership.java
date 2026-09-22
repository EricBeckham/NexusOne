package one.nexus.event.isolation;

import java.util.Objects;
import java.util.Set;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.api.TenantContext;

/**
 * Persistable membership facts used to re-issue {@link TenantContext} via
 * {@link one.nexus.isolation.context.RequestIsolationPolicy}. Not a second context model.
 */
public record StoredMembership(String tenantId, String actorId, String actorType, String membershipId) {
    public StoredMembership {
        tenantId = require(tenantId, "tenantId");
        actorId = require(actorId, "actorId");
        actorType = require(actorType, "actorType");
        membershipId = require(membershipId, "membershipId");
    }

    public static StoredMembership from(TenantContext context) {
        Objects.requireNonNull(context, "context");
        return new StoredMembership(
                context.tenantId(),
                context.actor().actorId(),
                context.actor().actorType(),
                context.membershipId());
    }

    public AuthenticatedMembership toMembership() {
        return new AuthenticatedMembership(
                new Actor(actorId, actorType),
                tenantId,
                membershipId,
                Set.of(tenantId));
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
