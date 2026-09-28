package one.nexus.p3.instance;

import java.util.Optional;
import java.util.UUID;

/**
 * Platform snapshot for one instance object. B supplies the manifest-backed implementation.
 * Until then, {@link EmptyPlatformObjectSource} returns nothing.
 */
public interface PlatformObjectSource {
    Optional<PlatformObject> find(UUID tenantId, UUID instanceId, ObjectType type, String code);
}
