package one.nexus.p3.instance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstanceConsumptionStore {
    boolean seen(UUID tenantId, UUID eventId);

    void markSeen(UUID tenantId, UUID instanceId, UUID eventId);

    Optional<InstanceSnapshot> find(UUID tenantId, UUID instanceId);

    void save(UUID tenantId, InstanceSnapshot snapshot);

    default List<InstanceSnapshot> list(UUID tenantId) {
        return List.of();
    }
}
