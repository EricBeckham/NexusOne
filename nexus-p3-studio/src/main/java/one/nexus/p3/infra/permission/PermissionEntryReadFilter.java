package one.nexus.p3.infra.permission;

import java.util.Collection;
import java.util.List;
import one.nexus.p3.portal.EntryReadFilter;
import one.nexus.permission.PermissionModule;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * PLA6 → PLC portal: the real {@link EntryReadFilter}. Delegates to the permission module's C5 check
 * ({@code EntryAccessService.readableEntries}), so the studio portal and the member portal (which calls
 * {@code /api/v1/permission/entries/readable}) show the same entries under either member-access mode (PLA5).
 * Replaces the open placeholder from {@code P3PlaceholderAutoConfiguration}. Without a permission module (some
 * slice contexts) nothing is filtered, as before.
 */
@Component
public class PermissionEntryReadFilter implements EntryReadFilter {
    private final ObjectProvider<PermissionModule> permissions;

    public PermissionEntryReadFilter(ObjectProvider<PermissionModule> permissions) {
        this.permissions = permissions;
    }

    @Override
    public List<String> readable(Collection<String> candidateKeys) {
        if (candidateKeys == null || candidateKeys.isEmpty()) {
            return List.of();
        }
        PermissionModule module = permissions.getIfAvailable();
        if (module == null) {
            return List.copyOf(candidateKeys);
        }
        return List.copyOf(module.entries().readableEntries(candidateKeys));
    }
}
