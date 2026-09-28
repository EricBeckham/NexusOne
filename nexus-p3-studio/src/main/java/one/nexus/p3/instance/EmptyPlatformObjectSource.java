package one.nexus.p3.instance;

import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/** Placeholder until B publishes instance manifests. */
@Component
@ConditionalOnMissingBean(PlatformObjectSource.class)
public class EmptyPlatformObjectSource implements PlatformObjectSource {
    @Override
    public Optional<PlatformObject> find(UUID tenantId, UUID instanceId, ObjectType type, String code) {
        return Optional.empty();
    }
}
