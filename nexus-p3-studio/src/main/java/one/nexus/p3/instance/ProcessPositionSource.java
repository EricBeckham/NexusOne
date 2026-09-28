package one.nexus.p3.instance;

import java.util.List;
import java.util.UUID;

/** POSITION references on installed processes. B supplies these from the manifest; empty until then. */
public interface ProcessPositionSource {
    List<PositionRef> positions(UUID tenantId);

    record PositionRef(UUID instanceId, String processKey, String positionCode) {}
}
