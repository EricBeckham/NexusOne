package one.nexus.p3.instance;

import java.util.Optional;
import java.util.UUID;

/**
 * Platform snapshot for one instance object. The market module supplies the manifest-backed bean.
 * {@link EmptyPlatformObjectSource} is used only when that bean is absent.
 */
public interface PlatformObjectSource {
    Optional<PlatformObject> find(UUID tenantId, UUID instanceId, ObjectType type, String code);
}
