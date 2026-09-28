package one.nexus.p3.instance;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * PLX1 r3.3. Pulls {@link AppInstanceQuery#current()}, which includes uninstalled instances.
 * A higher revision replaces the local pin. An equal revision is a no-op. Tenant overrides are not touched.
 */
@Service
public class InstanceReconciler {
    private final AppInstanceQuery query;
    private final InstanceConsumptionStore store;

    public InstanceReconciler(AppInstanceQuery query, InstanceConsumptionStore store) {
        this.query = query;
        this.store = store;
    }

    public void reconcile(UUID tenantId) {
        if (tenantId == null) {
            return;
        }
        for (AppInstanceState state : query.current()) {
            if (state == null || state.instanceId() == null || state.revision() < 1) {
                continue;
            }
            long applied = store.find(tenantId, state.instanceId()).map(InstanceSnapshot::revision).orElse(0L);
            if (state.revision() <= applied) {
                continue;
            }
            boolean installed = state.installed();
            List<String> entries = installed && state.entries() != null ? List.copyOf(state.entries()) : List.of();
            store.save(tenantId, new InstanceSnapshot(state.instanceId(), state.revision(), state.productCode(),
                    state.appCode(), state.version(), null, installed ? state.mode() : null, entries, installed));
        }
    }
}
