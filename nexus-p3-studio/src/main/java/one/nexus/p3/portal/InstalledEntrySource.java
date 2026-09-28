package one.nexus.p3.portal;

import java.util.List;
import java.util.UUID;

/** Installed function entries for the current tenant. Empty until an instance catalog exists. */
public interface InstalledEntrySource {
    List<FunctionEntry> installed(UUID tenantId);
}
