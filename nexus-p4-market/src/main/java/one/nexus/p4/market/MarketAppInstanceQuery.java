package one.nexus.p4.market;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p3.instance.AppInstanceQuery;
import one.nexus.p3.instance.AppInstanceState;
import one.nexus.p4.market.AppInstanceService.AppInstance;
import org.springframework.stereotype.Component;

/**
 * PLB7: B's implementation of C's {@link AppInstanceQuery} (PLX1 C4 r3.3). No tenant parameter — the tenant is the
 * server-bound context, and a call without one is refused. {@link #current()} includes uninstalled instances.
 * Registering this bean retires the empty stand-in from {@code P3PlaceholderAutoConfiguration}.
 */
@Component
public class MarketAppInstanceQuery implements AppInstanceQuery {
    private final AppInstanceService instances;

    public MarketAppInstanceQuery(AppInstanceService instances) {
        this.instances = instances;
    }

    @Override
    public List<AppInstanceState> current() {
        return instances.current().stream().map(MarketAppInstanceQuery::state).toList();
    }

    @Override
    public Optional<AppInstanceState> current(UUID instanceId) {
        return instances.current().stream()
                .filter(instance -> instance.id().equals(instanceId))
                .findFirst()
                .map(MarketAppInstanceQuery::state);
    }

    static AppInstanceState state(AppInstance instance) {
        boolean installed = "INSTALLED".equals(instance.status());
        return new AppInstanceState(instance.id(), instance.revision(), instance.status(), instance.productCode(),
                instance.appCode(), instance.version(), installed ? instance.mode() : null,
                installed ? instance.entries() : List.of(), instance.changedAt());
    }
}
