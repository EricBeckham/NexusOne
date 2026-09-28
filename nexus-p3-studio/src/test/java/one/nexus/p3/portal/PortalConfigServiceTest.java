package one.nexus.p3.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import one.nexus.p3.domain.P3ErrorCodes;
import one.nexus.p3.domain.P3Exception;
import org.junit.jupiter.api.Test;

class PortalConfigServiceTest {
    private final UUID tenant = UUID.fromString("01900000-0000-7000-8000-000000000101");
    private final MemoryStore store = new MemoryStore();
    private final PortalConfigService service = new PortalConfigService(store);

    @Test
    void replacingTheMenuIsIdempotentAndKeepsOrder() {
        UUID group = UUID.fromString("03123926-7307-4cc6-a634-8c6e87081189");
        List<MenuItem> items = List.of(
                new MenuItem(group, null, "办公", null, "办公", 10, false),
                new MenuItem(null, group, "请假申请", "fn:leave:leave_apply", "办公", 20, false));
        List<MenuItem> saved = service.replaceMenu(tenant, items);
        assertEquals(2, saved.size());
        assertEquals("fn:leave:leave_apply", saved.get(1).entryKey());
        assertEquals(saved, service.replaceMenu(tenant, saved));
    }

    @Test
    void aBrokenEntryKeyAndMissingParentAreRejectedTogether() {
        P3Exception invalid = assertThrows(P3Exception.class, () -> service.replaceMenu(tenant, List.of(
                new MenuItem(UUID.randomUUID(), UUID.randomUUID(), "请假", "Leave", "办公", 1, false))));
        assertEquals(P3ErrorCodes.PORTAL_INVALID, invalid.errorCode());
        assertEquals(List.of("invalid_entry_key", "unknown_parent"), invalid.details().get("problems"));
    }

    @Test
    void homeModulesReplaceByCode() {
        service.replaceModules(tenant, List.of(new HomeModule("todos", "待办", 1, false)));
        List<HomeModule> replaced = service.replaceModules(tenant, List.of(new HomeModule("entries", "功能入口", 2, false)));
        assertEquals(List.of("entries"), replaced.stream().map(HomeModule::code).toList());
    }

    private static final class MemoryStore implements PortalConfigStore {
        private List<MenuItem> menu = List.of();
        private List<HomeModule> modules = List.of();

        @Override
        public List<MenuItem> menu(UUID tenantId) {
            return menu;
        }

        @Override
        public void replaceMenu(UUID tenantId, List<MenuItem> items) {
            menu = new ArrayList<>(items);
        }

        @Override
        public List<HomeModule> modules(UUID tenantId) {
            return modules;
        }

        @Override
        public void replaceModules(UUID tenantId, List<HomeModule> modules) {
            this.modules = new ArrayList<>(modules);
        }
    }
}
