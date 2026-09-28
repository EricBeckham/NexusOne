package one.nexus.p3.portal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import one.nexus.common.id.UuidV7;
import one.nexus.p3.domain.P3ErrorCodes;
import one.nexus.p3.domain.P3Exception;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** PLC3. Tenant admin menu and home modules. A repeated save of the same payload replaces the previous rows. */
@Service
public class PortalConfigService {
    private static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9_]{0,23}$");
    private static final Pattern ENTRY = Pattern.compile("^fn:[a-z][a-z0-9_]{0,23}:[a-z][a-z0-9_]{0,23}$");

    private final PortalConfigStore store;

    public PortalConfigService(PortalConfigStore store) {
        this.store = store;
    }

    @Transactional(readOnly = true)
    public List<MenuItem> menu(UUID tenantId) {
        return store.menu(tenantId);
    }

    @Transactional(readOnly = true)
    public List<HomeModule> modules(UUID tenantId) {
        return store.modules(tenantId);
    }

    @Transactional
    public List<MenuItem> replaceMenu(UUID tenantId, List<MenuItem> items) {
        List<MenuItem> rows = items == null ? List.of() : items;
        List<MenuItem> assigned = new ArrayList<>();
        Set<UUID> ids = new HashSet<>();
        List<String> problems = new ArrayList<>();
        for (MenuItem item : rows) {
            UUID id = item.id() == null ? UuidV7.generate() : item.id();
            if (!ids.add(id)) {
                problems.add("duplicate_id");
            }
            if (item.label() == null || item.label().isBlank()) {
                problems.add("label_required");
            }
            if (item.entryKey() != null && !item.entryKey().isBlank() && !ENTRY.matcher(item.entryKey()).matches()) {
                problems.add("invalid_entry_key");
            }
            assigned.add(new MenuItem(id, item.parentId(), item.label(), blankToNull(item.entryKey()),
                    blankToNull(item.section()), item.sort(), item.hidden()));
        }
        for (MenuItem item : assigned) {
            if (item.parentId() != null && !ids.contains(item.parentId())) {
                problems.add("unknown_parent");
            }
        }
        if (!problems.isEmpty()) {
            throw new P3Exception(P3ErrorCodes.PORTAL_INVALID, 400, "portal menu is invalid",
                    java.util.Map.of("problems", List.copyOf(problems)));
        }
        store.replaceMenu(tenantId, List.copyOf(assigned));
        return store.menu(tenantId);
    }

    @Transactional
    public List<HomeModule> replaceModules(UUID tenantId, List<HomeModule> modules) {
        List<HomeModule> rows = modules == null ? List.of() : modules;
        Set<String> codes = new HashSet<>();
        List<String> problems = new ArrayList<>();
        List<HomeModule> assigned = new ArrayList<>();
        for (HomeModule module : rows) {
            if (module.code() == null || !CODE.matcher(module.code()).matches()) {
                problems.add("invalid_code");
            } else if (!codes.add(module.code())) {
                problems.add("duplicate_code");
            }
            if (module.title() == null || module.title().isBlank()) {
                problems.add("title_required");
            }
            assigned.add(module);
        }
        if (!problems.isEmpty()) {
            throw new P3Exception(P3ErrorCodes.PORTAL_INVALID, 400, "portal home modules are invalid",
                    java.util.Map.of("problems", List.copyOf(problems)));
        }
        store.replaceModules(tenantId, List.copyOf(assigned));
        return store.modules(tenantId);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
