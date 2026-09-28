package one.nexus.p3.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.domain.P3Exception;
import one.nexus.p3.instance.AppInstanceQuery;
import one.nexus.p3.instance.AppInstanceState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PortalEntryControllerTest {
    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void listsInstancesForTheBoundTenant() {
        AppInstanceState installed = instance("INSTALLED");
        PortalEntryController controller = new PortalEntryController(null, query(List.of(installed)));
        TenantContextHolder.set(context());

        assertEquals(List.of(installed), controller.instances().instances());
    }

    @Test
    void refusesInstanceListingWithoutATenant() {
        PortalEntryController controller = new PortalEntryController(null, query(List.of(instance("INSTALLED"))));

        assertThrows(P3Exception.class, controller::instances);
    }

    private static AppInstanceQuery query(List<AppInstanceState> rows) {
        return new AppInstanceQuery() {
            @Override
            public List<AppInstanceState> current() {
                return rows;
            }

            @Override
            public Optional<AppInstanceState> current(UUID instanceId) {
                return rows.stream().filter(row -> row.instanceId().equals(instanceId)).findFirst();
            }
        };
    }

    private static AppInstanceState instance(String status) {
        return new AppInstanceState(UUID.randomUUID(), 1, status, "pl_leave", "leave", "1.0.0", "INHERIT",
                List.of("fn:leave:leave_apply"), Instant.parse("2026-09-28T00:00:00Z"));
    }

    private static TenantContext context() {
        Instant now = Instant.parse("2026-09-28T00:00:00Z");
        return new TenantContext(UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(), "sub", now,
                now.plusSeconds(600));
    }
}
