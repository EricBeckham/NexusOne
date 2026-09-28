package one.nexus.p3.portal;

import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(InstalledEntrySource.class)
public class EmptyInstalledEntrySource implements InstalledEntrySource {
    @Override
    public List<FunctionEntry> installed(UUID tenantId) {
        return List.of();
    }
}
