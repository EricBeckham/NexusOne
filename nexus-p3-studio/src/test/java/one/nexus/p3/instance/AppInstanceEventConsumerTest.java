package one.nexus.p3.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppInstanceEventConsumerTest {
    private final UUID tenant = UUID.fromString("01900000-0000-7000-8000-000000000101");
    private final UUID other = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private final UUID instance = UUID.fromString("03123926-7307-4cc6-a634-8c6e87081189");
    private final UUID first = UUID.fromString("0192f0c4-7a1e-7c3b-9f00-000000000001");
    private final UUID second = UUID.fromString("0192f0c4-7a1e-7c3b-9f00-000000000002");
    private final UUID third = UUID.fromString("0192f0c4-7a1e-7c3b-9f00-000000000003");
    private final UUID fourth = UUID.fromString("0192f0c4-7a1e-7c3b-9f00-000000000004");
    private MemoryStore store;
    private MemoryQuery query;
    private AppInstanceEventConsumer consumer;

    @BeforeEach
    void setUp() {
        store = new MemoryStore();
        query = new MemoryQuery();
        consumer = new AppInstanceEventConsumer(store, query);
    }

    @Test
    void redeliveryOfTheSameEventIdDoesNotApplyTwice() {
        consumer.accept(tenant, event(first, 1, "1.0.0", null, "INHERIT", List.of("fn:leave:leave_apply")));
        consumer.accept(tenant, event(first, 1, "9.9.9", null, "INHERIT", List.of("fn:leave:other")));
        InstanceSnapshot saved = store.find(tenant, instance).orElseThrow();
        assertEquals("1.0.0", saved.version());
        assertEquals(List.of("fn:leave:leave_apply"), saved.entries());
        assertEquals(1, store.seenCount());
    }

    @Test
    void anOlderRevisionWithANewEventIdIsDiscarded() {
        consumer.accept(tenant, event(first, 1, "1.0.0", null, "INHERIT", List.of("fn:leave:leave_apply")));
        consumer.accept(tenant, event(second, 2, "1.1.0", "1.0.0", "INHERIT", List.of("fn:leave:leave_apply", "fn:leave:leave_view")));
        consumer.accept(tenant, event(third, 2, "0.0.1", "1.0.0", "INHERIT", List.of("fn:leave:stale")));
        InstanceSnapshot saved = store.find(tenant, instance).orElseThrow();
        assertEquals(2, saved.revision());
        assertEquals(List.of("fn:leave:leave_apply", "fn:leave:leave_view"), saved.entries());
    }

    @Test
    void aGapUsesTheQuerySnapshotAndNotTheEventEntries() {
        consumer.accept(tenant, event(first, 1, "1.0.0", null, "INHERIT", List.of("fn:leave:leave_apply")));
        query.put(state(4, "INSTALLED", "1.3.0", "INHERIT", List.of("fn:leave:leave_view")));
        consumer.accept(tenant, event(fourth, 4, "1.2.0", "1.1.0", "INHERIT", List.of("fn:leave:from_event")));
        InstanceSnapshot saved = store.find(tenant, instance).orElseThrow();
        assertEquals(4, saved.revision());
        assertEquals(List.of("fn:leave:leave_view"), saved.entries());
        assertEquals("1.3.0", saved.version());
    }

    @Test
    void aGapIsRetriedWhenTheQueryIsStillBehind() {
        consumer.accept(tenant, event(first, 1, "1.0.0", null, "INHERIT", List.of("fn:leave:leave_apply")));
        query.put(state(2, "INSTALLED", "1.1.0", "INHERIT", List.of("fn:leave:leave_apply")));
        assertThrows(InstanceEventRetry.class,
                () -> consumer.accept(tenant, event(fourth, 4, "1.2.0", "1.1.0", "INHERIT", List.of("fn:leave:leave_apply"))));
        assertFalse(store.seen(tenant, fourth));
        assertEquals(1, store.find(tenant, instance).orElseThrow().revision());

        query.put(state(4, "INSTALLED", "1.3.0", "INHERIT", List.of("fn:leave:leave_view")));
        consumer.accept(tenant, event(fourth, 4, "1.2.0", "1.1.0", "INHERIT", List.of("fn:leave:from_event")));
        assertEquals(4, store.find(tenant, instance).orElseThrow().revision());
    }

    @Test
    void uninstallClearsEntries() {
        consumer.accept(tenant, event(first, 1, "1.0.0", null, "INHERIT", List.of("fn:leave:leave_apply")));
        consumer.accept(tenant, event(second, 2, "1.0.0", null, null, List.of()));
        InstanceSnapshot saved = store.find(tenant, instance).orElseThrow();
        assertFalse(saved.installed());
        assertTrue(saved.entries().isEmpty());
        assertEquals(null, saved.mode());
    }

    @Test
    void anotherTenantKeepsItsOwnPin() {
        consumer.accept(tenant, event(first, 1, "1.0.0", null, "INHERIT", List.of("fn:leave:leave_apply")));
        assertTrue(store.find(other, instance).isEmpty());
    }

    private InstanceEvent event(UUID eventId, long revision, String version, String previous, String mode, List<String> entries) {
        return new InstanceEvent(eventId, instance, revision, "oa_basic", "leave", version, previous, mode, entries);
    }

    private static final class MemoryStore implements InstanceConsumptionStore {
        private final Map<String, InstanceSnapshot> rows = new HashMap<>();
        private final List<String> seen = new ArrayList<>();

        @Override
        public boolean seen(UUID tenantId, UUID eventId) {
            return seen.contains(tenantId + "|" + eventId);
        }

        @Override
        public void markSeen(UUID tenantId, UUID instanceId, UUID eventId) {
            String key = tenantId + "|" + eventId;
            if (!seen.contains(key)) {
                seen.add(key);
            }
        }

        @Override
        public Optional<InstanceSnapshot> find(UUID tenantId, UUID instanceId) {
            return Optional.ofNullable(rows.get(tenantId + "|" + instanceId));
        }

        @Override
        public void save(UUID tenantId, InstanceSnapshot snapshot) {
            rows.put(tenantId + "|" + snapshot.instanceId(), snapshot);
        }

        int seenCount() {
            return seen.size();
        }
    }

    private AppInstanceState state(long revision, String status, String version, String mode, List<String> entries) {
        return new AppInstanceState(instance, revision, status, "oa_basic", "leave", version, mode, entries,
                Instant.parse("2026-09-28T08:00:00Z"));
    }

    private static final class MemoryQuery implements AppInstanceQuery {
        private final Map<UUID, AppInstanceState> rows = new HashMap<>();

        void put(AppInstanceState state) {
            rows.put(state.instanceId(), state);
        }

        @Override
        public List<AppInstanceState> current() {
            return List.copyOf(rows.values());
        }

        @Override
        public Optional<AppInstanceState> current(UUID instanceId) {
            return Optional.ofNullable(rows.get(instanceId));
        }
    }
}
