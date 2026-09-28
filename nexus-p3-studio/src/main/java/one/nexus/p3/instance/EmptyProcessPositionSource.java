package one.nexus.p3.instance;

import java.util.List;
import java.util.UUID;

public class EmptyProcessPositionSource implements ProcessPositionSource {
    @Override
    public List<PositionRef> positions(UUID tenantId) {
        return List.of();
    }
}
