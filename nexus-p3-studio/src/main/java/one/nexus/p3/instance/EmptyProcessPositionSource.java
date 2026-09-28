package one.nexus.p3.instance;

import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(ProcessPositionSource.class)
public class EmptyProcessPositionSource implements ProcessPositionSource {
    @Override
    public List<PositionRef> positions(UUID tenantId) {
        return List.of();
    }
}
