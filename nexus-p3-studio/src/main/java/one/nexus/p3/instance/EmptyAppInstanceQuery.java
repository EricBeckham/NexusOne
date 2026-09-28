package one.nexus.p3.instance;

import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(AppInstanceQuery.class)
public class EmptyAppInstanceQuery implements AppInstanceQuery {
    @Override
    public Optional<InstanceSnapshot> current(UUID tenantId, UUID instanceId) {
        return Optional.empty();
    }
}
