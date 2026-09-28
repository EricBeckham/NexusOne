package one.nexus.p3.instance;

import java.util.Optional;
import java.util.UUID;

/** Placeholder until B publishes instance manifests. */
public class EmptyPlatformObjectSource implements PlatformObjectSource {
    @Override
    public Optional<PlatformObject> find(UUID tenantId, UUID instanceId, ObjectType type, String code) {
        return Optional.empty();
    }
}
