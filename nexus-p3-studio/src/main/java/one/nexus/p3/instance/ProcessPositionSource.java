package one.nexus.p3.instance;

import java.util.List;
import java.util.UUID;

/** POSITION references on installed processes. The market module reads them from the published manifest. */
public interface ProcessPositionSource {
    List<PositionRef> positions(UUID tenantId);

    record PositionRef(UUID instanceId, String processKey, String positionCode) {}
}
