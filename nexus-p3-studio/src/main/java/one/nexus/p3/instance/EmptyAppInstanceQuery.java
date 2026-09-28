package one.nexus.p3.instance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/** Placeholder until B registers the real query. It cannot be asked for an arbitrary tenant. */
@Component
@ConditionalOnMissingBean(AppInstanceQuery.class)
public class EmptyAppInstanceQuery implements AppInstanceQuery {
    @Override
    public List<AppInstanceState> current() {
        return List.of();
    }

    @Override
    public Optional<AppInstanceState> current(UUID instanceId) {
        return Optional.empty();
    }
}
