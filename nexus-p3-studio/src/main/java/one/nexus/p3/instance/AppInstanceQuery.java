package one.nexus.p3.instance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PLX1 C4 query port (r3.3). B implements this in p4-market and registers the bean.
 * Neither method takes a tenant: the implementation reads the server context and rejects a call with none.
 * {@link #current()} returns every instance of that tenant, including {@code UNINSTALLED}.
 */
public interface AppInstanceQuery {
    List<AppInstanceState> current();

    Optional<AppInstanceState> current(UUID instanceId);
}
