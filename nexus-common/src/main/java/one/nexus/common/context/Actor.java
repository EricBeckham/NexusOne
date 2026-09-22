package one.nexus.common.context;

import java.util.Objects;
import java.util.UUID;

/**
 * Authenticated or system principal. M0-04 binds this to Keycloak/P2 identity;
 * M0-01 only defines the shape.
 */
public record Actor(UUID subjectId, ActorType type, String displayName) {
    public Actor {
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(type, "type");
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName is required");
        }
    }
}
