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
import one.nexus.p3.domain.P3Exception;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BaselineDiffServiceTest {
    private final UUID tenant = UUID.fromString("01900000-0000-7000-8000-000000000101");
    private final UUID instance = UUID.fromString("03123926-7307-4cc6-a634-8c6e87081189");
    private MemoryOverrides overrides;
    private MemoryPlatform platform;
    private BaselineDiffService diffs;

    @BeforeEach
    void setUp() {
        overrides = new MemoryOverrides();
        platform = new MemoryPlatform();
        InstanceResolutionService resolution = new InstanceResolutionService(overrides, platform);
        diffs = new BaselineDiffService(overrides, platform, resolution, null);
    }

    @Test
    void listsEveryDifferenceAndRefusesMergeUntilEachConflictHasASide() {
        platform.put(ObjectType.FORM, "leave_apply", "1.1.0", Map.of(
                "title", "新版",
                "fields", List.of(Map.of("code", "title", "type", "TEXT"), Map.of("code", "days", "type", "NUMERIC"))));
        overrides.save(row("1.0.0", Map.of(
                "title", "租户标题",
                "fields", List.of(Map.of("code", "title", "type", "TEXT"), Map.of("code", "note", "type", "TEXT")))));
        overrides.save(new OverrideRecord(tenant, instance, ObjectType.ROLE, "leave_approver", "1.1.0",
                Map.of("name", "审批人"), Instant.parse("2026-09-28T00:00:00Z"), "actor"));

        List<BaselineDiff> drifted = diffs.drift(tenant);
        assertEquals(1, drifted.size());
        assertEquals(List.of("/fields/days", "/fields/note", "/title"),
                drifted.get(0).changes().stream().map(BaselineChange::path).sorted().toList());

        P3Exception rejected = assertThrows(P3Exception.class,
                () -> diffs.merge(tenant, "actor", instance, ObjectType.FORM, "leave_apply", Map.of()));
        assertEquals(409, rejected.httpStatus());
        assertEquals(List.of("/title"), rejected.details().get("paths"));

        ResolvedObject merged = diffs.merge(tenant, "actor", instance, ObjectType.FORM, "leave_apply",
                Map.of("/title", "TENANT"));
        assertFalse(merged.drift());
        assertEquals("1.1.0", merged.baseVersion());
        assertEquals("租户标题", merged.content().get("title"));
        assertTrue(String.valueOf(merged.content().get("fields")).contains("days"));
        assertTrue(String.valueOf(merged.content().get("fields")).contains("note"));
        assertTrue(diffs.drift(tenant).isEmpty());
    }

    @Test
    void rejectsATenantChoiceThatRetypesAPlatformField() {
        platform.put(ObjectType.FORM, "leave_apply", "1.1.0", Map.of(
                "fields", List.of(Map.of("code", "title", "type", "TEXT"))));
        overrides.save(row("1.0.0", Map.of(
                "fields", List.of(Map.of("code", "title", "type", "NUMERIC")))));
        P3Exception rejected = assertThrows(P3Exception.class, () -> diffs.merge(tenant, "actor", instance,
                ObjectType.FORM, "leave_apply", Map.of("/fields/title/type", "TENANT")));
        assertEquals(400, rejected.httpStatus());
        assertEquals("1.0.0", overrides.find(tenant, instance, ObjectType.FORM, "leave_apply").orElseThrow().baseVersion());
    }

    private OverrideRecord row(String base, Map<String, Object> content) {
        return new OverrideRecord(tenant, instance, ObjectType.FORM, "leave_apply", base, content,
                Instant.parse("2026-09-28T00:00:00Z"), "actor");
    }

    private static final class MemoryOverrides implements InstanceOverrideStore {
        private final Map<String, OverrideRecord> rows = new HashMap<>();

        @Override
        public Optional<OverrideRecord> find(UUID tenantId, UUID instanceId, ObjectType type, String code) {
            return Optional.ofNullable(rows.get(key(tenantId, instanceId, type, code)));
        }

        @Override
        public List<OverrideRecord> list(UUID tenantId) {
            List<OverrideRecord> found = new ArrayList<>();
            for (OverrideRecord row : rows.values()) {
                if (tenantId.equals(row.tenantId())) {
                    found.add(row);
                }
            }
            return found;
        }

        @Override
        public OverrideRecord save(OverrideRecord record) {
            rows.put(key(record.tenantId(), record.instanceId(), record.objectType(), record.objectCode()), record);
            return record;
        }

        @Override
        public void delete(UUID tenantId, UUID instanceId, ObjectType type, String code) {
            rows.remove(key(tenantId, instanceId, type, code));
        }

        private static String key(UUID tenantId, UUID instanceId, ObjectType type, String code) {
            return tenantId + "|" + instanceId + "|" + type + "|" + code;
        }
    }

    private static final class MemoryPlatform implements PlatformObjectSource {
        private final Map<String, PlatformObject> rows = new HashMap<>();

        void put(ObjectType type, String code, String version, Map<String, Object> content) {
            rows.put(type + "|" + code, new PlatformObject(version, content));
        }

        @Override
        public Optional<PlatformObject> find(UUID tenantId, UUID instanceId, ObjectType type, String code) {
            return Optional.ofNullable(rows.get(type + "|" + code));
        }
    }
}
