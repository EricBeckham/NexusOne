package one.nexus.p3.instance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * B implements this in p4-market. The event consumer keeps {@link #current(UUID, UUID)}.
 * Portal load uses {@link #current()} and does not pass a tenant.
 */
public interface AppInstanceQuery {
    Optional<InstanceSnapshot> current(UUID tenantId, UUID instanceId);

    /** All instances of the current tenant, including uninstalled. Empty until B registers an implementation. */
    default List<AppInstanceState> current() {
        return List.of();
    }
}
