package one.nexus.p3.portal;

import java.util.List;
import java.util.UUID;

/** Installed function entries for one tenant, read from that tenant's published manifests. */
public interface InstalledEntrySource {
    List<FunctionEntry> installed(UUID tenantId);
}
