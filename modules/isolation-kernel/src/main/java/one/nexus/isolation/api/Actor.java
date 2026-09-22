package one.nexus.isolation.api;

import java.util.Objects;

/**
 * Server-authenticated actor. M0-04 fills this from Keycloak/P2 membership, never from a client tenant header.
 */
public record Actor(String actorId, String actorType) {
    public static final String PERSON = "PERSON";
    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";
    public static final String SYSTEM = "SYSTEM";

    public Actor {
        actorId = requireToken(actorId, "actorId");
        actorType = requireToken(actorType, "actorType");
    }

    public static Actor person(String actorId) {
        return new Actor(actorId, PERSON);
    }

    public static Actor platformAdmin(String actorId) {
        return new Actor(actorId, PLATFORM_ADMIN);
    }

    public static Actor system(String actorId) {
        return new Actor(actorId, SYSTEM);
    }

    static String requireToken(String value, String name) {
        Objects.requireNonNull(value, name);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " required");
        }
        return trimmed;
    }
}
