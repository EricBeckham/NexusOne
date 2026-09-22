package one.nexus.p1.domain;

import java.util.Set;
import java.util.UUID;

/**
 * Cross-module actor. M0-04 will populate subject from P2 person / Keycloak identity.
 * This module never talks to Keycloak.
 */
public record Actor(
        UUID subjectId,
        UUID homeTenantId,
        String displayName,
        Set<PlatformDuty> duties
) {

    public Actor {
        duties = duties == null ? Set.of() : Set.copyOf(duties);
    }

    public boolean has(PlatformDuty duty) {
        return duties.contains(duty) || duties.contains(PlatformDuty.ALL_DEMO);
    }
}
