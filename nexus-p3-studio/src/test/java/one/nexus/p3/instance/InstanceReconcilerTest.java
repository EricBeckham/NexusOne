package one.nexus.p3.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p3.portal.FunctionEntry;
import one.nexus.p3.portal.OpenEntryReadFilter;
import one.nexus.p3.portal.PortalEntry;
import one.nexus.p3.portal.PortalEntryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** PLX1 r3.3 contract tests C①–⑤. The query has no tenant argument. Event consumption is not exercised here. */
class InstanceReconcilerTest {
    private final UUID tenant = UUID.fromString("01900000-0000-7000-8000-000000000101");
    private final UUID instance = UUID.fromString("03123926-7307-4cc6-a634-8c6e87081189");
    private MemoryStore store;
    private final List<AppInstanceState> remote = new ArrayList<>();
    private InstanceReconciler reconciler;

    @BeforeEach
    void setUp() {
        store = new MemoryStore();
        reconciler = new InstanceReconciler(new AppInstanceQuery() {
            @Override
            public List<AppInstanceState> current() {
                return List.copyOf(remote);
            }

            @Override
            public Optional<AppInstanceState> current(UUID instanceId) {
                return remote.stream().filter(state -> instanceId.equals(state.instanceId())).findFirst();
            }
        }, store);
    }

    @Test
    void sameRevisionTwiceDoesNotChangeThePin() {
        remote.add(state(1, "INSTALLED", "1.0.0", "INHERIT", List.of("fn:leave:leave_apply")));
        reconciler.reconcile(tenant);
        InstanceSnapshot first = store.find(tenant, instance).orElseThrow();
        reconciler.reconcile(tenant);
        InstanceSnapshot second = store.find(tenant, instance).orElseThrow();
        assertEquals(first, second);
        assertEquals(1, store.saves());
    }

    @Test
    void aSkippedRevisionReplacesTheWholePin() {
        store.save(tenant, new InstanceSnapshot(instance, 1, "oa_basic", "leave", "1.0.0", null, "INHERIT",
                List.of("fn:leave:leave_apply"), true));
        remote.add(state(3, "INSTALLED", "1.2.0", "INHERIT", List.of("fn:leave:leave_view")));
        reconciler.reconcile(tenant);
        InstanceSnapshot saved = store.find(tenant, instance).orElseThrow();
        assertEquals(3, saved.revision());
        assertEquals("1.2.0", saved.version());
        assertEquals(List.of("fn:leave:leave_view"), saved.entries());
    }

    @Test
    void uninstalledStateRemovesEntries() {
        UUID removed = UUID.fromString("03123926-7307-4cc6-a634-8c6e87081190");
        store.save(tenant, new InstanceSnapshot(instance, 1, "oa_basic", "leave", "1.0.0", null, "INHERIT",
                List.of("fn:leave:leave_apply"), true));
        remote.add(state(2, "UNINSTALLED", "1.0.0", null, List.of()));
        remote.add(new AppInstanceState(removed, 1, "UNINSTALLED", "oa_basic", "expense", "1.0.0", null, List.of(),
                Instant.parse("2026-09-28T08:00:00Z")));
        reconciler.reconcile(tenant);
        assertTrue(portal().visible(tenant).isEmpty());
        assertFalse(store.find(tenant, instance).orElseThrow().installed());
        assertTrue(store.find(tenant, instance).orElseThrow().entries().isEmpty());
        assertFalse(store.find(tenant, removed).orElseThrow().installed());
    }

    @Test
    void movingBackToAnOlderVersionClearsDrift() {
        PlatformObjectSource platform = (tenantId, instanceId, type, code) -> store.find(tenantId, instanceId)
                .filter(InstanceSnapshot::installed)
                .map(row -> new PlatformObject(row.version(), Map.of("name", "leave")));
        MemoryOverrides overrides = new MemoryOverrides();
        InstanceResolutionService resolution = new InstanceResolutionService(overrides, platform, reconciler);
        remote.add(state(2, "INSTALLED", "1.1.0", "INHERIT", List.of("fn:leave:leave_apply")));
        resolution.save(tenant, "actor", instance, ObjectType.FORM, "leave_apply", "1.0.0", Map.of("name", "租户"));
        assertTrue(resolution.resolve(tenant, instance, ObjectType.FORM, "leave_apply").drift());

        remote.set(0, state(3, "INSTALLED", "1.0.0", "INHERIT", List.of("fn:leave:leave_apply")));
        ResolvedObject resolved = resolution.resolve(tenant, instance, ObjectType.FORM, "leave_apply");
        assertEquals(ResolutionSource.TENANT, resolved.source());
        assertEquals("1.0.0", resolved.platformVersion());
        assertFalse(resolved.drift());
        assertEquals("租户", resolved.content().get("name"));
    }

    @Test
    void portalLoadShowsEntriesWhenOnlyAPlatformApprovalExists() {
        remote.add(state(1, "INSTALLED", "1.0.0", "INHERIT", List.of("fn:leave:leave_apply", "fn:leave:leave_view")));
        List<String> keys = portal().visible(tenant).stream().map(PortalEntry::key).toList();
        assertEquals(List.of("fn:leave:leave_apply", "fn:leave:leave_view"), keys);
        assertEquals(0, store.seenCount());
    }

    private PortalEntryService portal() {
        return new PortalEntryService(tenantId -> List.<FunctionEntry>of(), new OpenEntryReadFilter(),
                (tenantId, processKey) -> Optional.empty(), reconciler, store);
    }

    private AppInstanceState state(long revision, String status, String version, String mode, List<String> entries) {
        return new AppInstanceState(instance, revision, status, "oa_basic", "leave", version, mode, entries,
                Instant.parse("2026-09-28T08:00:00Z"));
    }

    private static final class MemoryStore implements InstanceConsumptionStore {
        private final Map<String, InstanceSnapshot> rows = new HashMap<>();
        private int saves;
        private int seen;

        @Override
        public boolean seen(UUID tenantId, UUID eventId) {
            return false;
        }

        @Override
        public void markSeen(UUID tenantId, UUID instanceId, UUID eventId) {
            seen++;
        }

        @Override
        public Optional<InstanceSnapshot> find(UUID tenantId, UUID instanceId) {
            return Optional.ofNullable(rows.get(tenantId + "|" + instanceId));
        }

        @Override
        public void save(UUID tenantId, InstanceSnapshot snapshot) {
            saves++;
            rows.put(tenantId + "|" + snapshot.instanceId(), snapshot);
        }

        @Override
        public List<InstanceSnapshot> list(UUID tenantId) {
            String prefix = tenantId + "|";
            List<InstanceSnapshot> found = new ArrayList<>();
            for (Map.Entry<String, InstanceSnapshot> entry : rows.entrySet()) {
                if (entry.getKey().startsWith(prefix)) {
                    found.add(entry.getValue());
                }
            }
            return found;
        }

        int saves() {
            return saves;
        }

        int seenCount() {
            return seen;
        }
    }

    private static final class MemoryOverrides implements InstanceOverrideStore {
        private final Map<String, OverrideRecord> rows = new HashMap<>();

        @Override
        public Optional<OverrideRecord> find(UUID tenantId, UUID instanceId, ObjectType type, String code) {
            return Optional.ofNullable(rows.get(tenantId + "|" + instanceId + "|" + type + "|" + code));
        }

        @Override
        public OverrideRecord save(OverrideRecord record) {
            rows.put(record.tenantId() + "|" + record.instanceId() + "|" + record.objectType() + "|" + record.objectCode(),
                    record);
            return record;
        }

        @Override
        public void delete(UUID tenantId, UUID instanceId, ObjectType type, String code) {
            rows.remove(tenantId + "|" + instanceId + "|" + type + "|" + code);
        }
    }
}
