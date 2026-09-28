package one.nexus.p3.portal;

import java.util.List;
import java.util.UUID;

public interface PortalConfigStore {
    List<MenuItem> menu(UUID tenantId);

    void replaceMenu(UUID tenantId, List<MenuItem> items);

    List<HomeModule> modules(UUID tenantId);

    void replaceModules(UUID tenantId, List<HomeModule> modules);
}
