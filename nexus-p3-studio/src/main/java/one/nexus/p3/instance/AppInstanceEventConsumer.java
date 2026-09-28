package one.nexus.p3.instance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * PLX1 C4 consumer. Dedupes by {@code event_id}, orders by {@code instance_revision},
 * and replaces only the instance pin. Tenant overrides are untouched.
 */
@Service
public class AppInstanceEventConsumer {
    private final InstanceConsumptionStore store;
    private final AppInstanceQuery query;

    public AppInstanceEventConsumer(InstanceConsumptionStore store, AppInstanceQuery query) {
        this.store = store;
        this.query = query;
    }

    public void accept(UUID tenantId, InstanceEvent event) {
        if (store.seen(tenantId, event.eventId())) {
            return;
        }
        long applied = store.find(tenantId, event.instanceId()).map(InstanceSnapshot::revision).orElse(0L);
        if (event.revision() <= applied) {
            store.markSeen(tenantId, event.instanceId(), event.eventId());
            return;
        }
        if (event.revision() == applied + 1) {
            store.save(tenantId, fromEvent(event));
            store.markSeen(tenantId, event.instanceId(), event.eventId());
            return;
        }
        InstanceSnapshot current = query.current(tenantId, event.instanceId())
                .filter(snapshot -> snapshot.revision() >= event.revision())
                .orElseThrow(InstanceEventRetry::new);
        store.save(tenantId, current);
        store.markSeen(tenantId, event.instanceId(), event.eventId());
    }

    private static InstanceSnapshot fromEvent(InstanceEvent event) {
        boolean installed = event.kind() != InstanceEvent.Kind.UNINSTALLED;
        List<String> entries = installed ? event.entries() : List.of();
        String mode = installed ? event.mode() : null;
        return new InstanceSnapshot(event.instanceId(), event.revision(), event.productCode(), event.appCode(),
                event.version(), event.previousVersion(), mode, entries, installed);
    }
}
