package one.nexus.bootstrap.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import one.nexus.p3.portal.HomeModule;
import one.nexus.p3.portal.MenuItem;
import one.nexus.p3.portal.PortalConfigService;
import one.nexus.p3.portal.PortalConfigStore;
import org.junit.jupiter.api.Test;

class PortalDemoSeedTest {
    @Test
    void writesTheDemoMenuThroughThePortalConfigServiceAndRepeatsCleanly() {
        MemoryStore store = new MemoryStore();
        PortalConfigService config = new PortalConfigService(store);

        PortalDemoSeedConfiguration.run("true", "local", "jdbc:postgresql://127.0.0.1:5432/nexusone", config);
        PortalDemoSeedConfiguration.run("true", "local", "jdbc:postgresql://127.0.0.1:5432/nexusone", config);

        assertEquals(P1DemoSeedConfiguration.TENANT_ID, store.tenantId);
        assertEquals(List.of("办公", "请假申请", "报销"), store.menu.stream().map(MenuItem::label).toList());
        assertEquals(List.of("fn:leave:leave_apply", "fn:expense:expense_apply"),
                store.menu.stream().map(MenuItem::entryKey).filter(key -> key != null && !key.isBlank()).toList());
        assertEquals(PortalDemoContent.GROUP_OFFICE, store.menu.get(1).parentId());
        assertEquals(List.of("todos", "notices"), store.modules.stream().map(HomeModule::code).toList());
        assertEquals(2, store.menuWrites);
        assertEquals(2, store.moduleWrites);
    }

    @Test
    void staysQuietWhenTheDemoSwitchIsOff() {
        MemoryStore store = new MemoryStore();
        PortalDemoSeedConfiguration.run("false", "local", "jdbc:postgresql://127.0.0.1:5432/nexusone",
                new PortalConfigService(store));
        assertTrue(store.menu.isEmpty());
        assertEquals(0, store.menuWrites);
    }

    @Test
    void refusesARemoteDatabaseBeforeWriting() {
        MemoryStore store = new MemoryStore();
        assertThrows(IllegalStateException.class, () -> PortalDemoSeedConfiguration.run(
                "true", "local", "jdbc:postgresql://db.internal:5432/nexusone", new PortalConfigService(store)));
        assertEquals(0, store.menuWrites);
    }

    private static final class MemoryStore implements PortalConfigStore {
        private UUID tenantId;
        private List<MenuItem> menu = List.of();
        private List<HomeModule> modules = List.of();
        private int menuWrites;
        private int moduleWrites;

        @Override
        public List<MenuItem> menu(UUID tenantId) {
            return menu;
        }

        @Override
        public void replaceMenu(UUID tenantId, List<MenuItem> items) {
            this.tenantId = tenantId;
            this.menu = new ArrayList<>(items);
            menuWrites++;
        }

        @Override
        public List<HomeModule> modules(UUID tenantId) {
            return modules;
        }

        @Override
        public void replaceModules(UUID tenantId, List<HomeModule> modules) {
            this.tenantId = tenantId;
            this.modules = new ArrayList<>(modules);
            moduleWrites++;
        }
    }
}
