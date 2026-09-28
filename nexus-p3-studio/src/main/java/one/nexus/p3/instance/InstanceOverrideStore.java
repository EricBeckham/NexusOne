package one.nexus.p3.instance;

import java.util.Optional;
import java.util.UUID;

public interface InstanceOverrideStore {
    Optional<OverrideRecord> find(UUID tenantId, UUID instanceId, ObjectType type, String code);

    OverrideRecord save(OverrideRecord record);

    void delete(UUID tenantId, UUID instanceId, ObjectType type, String code);
}
