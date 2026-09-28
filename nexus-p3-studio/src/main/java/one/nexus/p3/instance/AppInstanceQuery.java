package one.nexus.p3.instance;

import java.util.Optional;
import java.util.UUID;

/** B fills this. C calls it only when an event skips one or more revisions. */
public interface AppInstanceQuery {
    Optional<InstanceSnapshot> current(UUID tenantId, UUID instanceId);
}
