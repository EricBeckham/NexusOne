package one.nexus.isolation.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import one.nexus.isolation.api.TableDescriptor;
import one.nexus.isolation.api.TableScope;
import org.junit.jupiter.api.Test;

class TableRegistryReplaceTest {
    @Test
    void replaceUpdatesColumnsForSchemaEvolution() {
        TableRegistry registry = new TableRegistry();
        registry.registerTenant("l_alpha_orders", "nexus-p3-studio", 1, "id", "tenant_id", "title");
        registry.replace(new TableDescriptor(
                "l_alpha_orders",
                TableScope.TENANT,
                Set.of("id", "tenant_id", "title", "amount"),
                "nexus-p3-studio",
                2));
        assertEquals(Set.of("id", "tenant_id", "title", "amount"), registry.require("l_alpha_orders").columns());
        assertEquals(2, registry.require("l_alpha_orders").schemaVersion());
    }

    @Test
    void replaceRejectedWhenFrozen() {
        TableRegistry registry = new TableRegistry();
        registry.registerTenant("l_alpha_orders", "nexus-p3-studio", 1, "id", "tenant_id");
        registry.freeze();
        assertThrows(IllegalStateException.class, () -> registry.replace(new TableDescriptor(
                "l_alpha_orders",
                TableScope.TENANT,
                Set.of("id", "tenant_id", "title"),
                "nexus-p3-studio",
                2)));
    }

    @Test
    void registerAppSharedRequiresTenantId() {
        TableRegistry registry = new TableRegistry();
        registry.registerAppShared("a_crm_orders", "nexus-p3-studio", 1, "id", "tenant_id", "title");
        assertTrue(registry.require("a_crm_orders").tenantRequired());
        assertEquals(TableScope.APP_SHARED, registry.require("a_crm_orders").scope());
    }
}
