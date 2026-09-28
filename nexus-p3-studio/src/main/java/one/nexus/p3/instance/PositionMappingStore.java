package one.nexus.p3.instance;

import java.util.UUID;

public interface PositionMappingStore {
    boolean mapped(UUID tenantId, UUID instanceId, String positionCode);

    void map(UUID tenantId, UUID instanceId, String positionCode, UUID positionId);

    void unmap(UUID tenantId, UUID instanceId, String positionCode);
}
