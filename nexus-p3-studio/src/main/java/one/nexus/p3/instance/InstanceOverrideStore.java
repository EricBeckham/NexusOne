package one.nexus.p3.instance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstanceOverrideStore {
    Optional<OverrideRecord> find(UUID tenantId, UUID instanceId, ObjectType type, String code);

    /** Overrides for one tenant. Stores that predate PLC8 return an empty list. */
    default List<OverrideRecord> list(UUID tenantId) {
        return List.of();
    }

    OverrideRecord save(OverrideRecord record);

    void delete(UUID tenantId, UUID instanceId, ObjectType type, String code);
}
