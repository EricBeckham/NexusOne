package one.nexus.p3.portal;

import java.util.List;
import java.util.UUID;

public class EmptyInstalledEntrySource implements InstalledEntrySource {
    @Override
    public List<FunctionEntry> installed(UUID tenantId) {
        return List.of();
    }
}
