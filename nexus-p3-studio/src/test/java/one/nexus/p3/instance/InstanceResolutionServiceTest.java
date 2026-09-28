package one.nexus.p3.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p3.domain.P3ErrorCodes;
import one.nexus.p3.domain.P3Exception;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InstanceResolutionServiceTest {
    private final UUID tenant = UUID.fromString("01900000-0000-7000-8000-000000000101");
    private final UUID other = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private final UUID instance = UUID.fromString("03123926-7307-4cc6-a634-8c6e87081189");
    private MemoryOverrides overrides;
    private MemoryPlatform platform;
    private InstanceResolutionService service;

    @BeforeEach
    void setUp() {
        overrides = new MemoryOverrides();
        platform = new MemoryPlatform();
        service = new InstanceResolutionService(overrides, platform);
    }

    @Test
    void platformVersionWinsWhenTheTenantHasNotOverridden() {
        platform.put(ObjectType.FORM, "leave_apply", "1.0.0", Map.of("title", "请假"));
        ResolvedObject resolved = service.resolve(tenant, instance, ObjectType.FORM, "leave_apply");
        assertEquals(ResolutionSource.PLATFORM, resolved.source());
        assertEquals("1.0.0", resolved.baseVersion());
        assertEquals("1.0.0", resolved.platformVersion());
        assertFalse(resolved.drift());
        assertEquals("请假", resolved.content().get("title"));
    }

    @Test
    void overrideStaysWhenThePlatformVersionMovesAhead() {
        platform.put(ObjectType.PROCESS, "leave_approval", "1.1.0", Map.of("steps", 2));
        overrides.save(row(ObjectType.PROCESS, "leave_approval", "1.0.0", Map.of("steps", 1)));
        ResolvedObject kept = service.resolve(tenant, instance, ObjectType.PROCESS, "leave_approval");
        assertEquals(ResolutionSource.TENANT, kept.source());
        assertEquals(1, kept.content().get("steps"));
        assertTrue(kept.drift());
        assertEquals("1.1.0", kept.platformVersion());

        platform.put(ObjectType.FORM, "leave_apply", "1.1.0", Map.of("title", "新版"));
        ResolvedObject fresh = service.resolve(tenant, instance, ObjectType.FORM, "leave_apply");
        assertEquals(ResolutionSource.PLATFORM, fresh.source());
        assertEquals("新版", fresh.content().get("title"));
        assertFalse(fresh.drift());
    }

    @Test
    void equalOrOlderPlatformVersionIsNotDrift() {
        overrides.save(row(ObjectType.ROLE, "leave_approver", "1.2.0", Map.of("grants", 1)));
        platform.put(ObjectType.ROLE, "leave_approver", "1.2.0", Map.of("grants", 9));
        assertFalse(service.resolve(tenant, instance, ObjectType.ROLE, "leave_approver").drift());
        platform.put(ObjectType.ROLE, "leave_approver", "1.0.0", Map.of("grants", 9));
        assertFalse(service.resolve(tenant, instance, ObjectType.ROLE, "leave_approver").drift());
        platform.put(ObjectType.ROLE, "leave_approver", "latest", Map.of("grants", 9));
        assertFalse(service.resolve(tenant, instance, ObjectType.ROLE, "leave_approver").drift());
    }

    @Test
    void missingObjectReturnsNotFoundWithoutContent() {
        P3Exception missing = assertThrows(P3Exception.class,
                () -> service.resolve(tenant, instance, ObjectType.FORM, "leave_apply"));
        assertEquals(404, missing.httpStatus());
        assertEquals(P3ErrorCodes.INSTANCE_NOT_FOUND, missing.errorCode());
        assertEquals("leave_apply", missing.details().get("object_code"));
        assertFalse(missing.details().containsKey("content"));
    }

    @Test
    void anotherTenantCannotSeeTheOverride() {
        overrides.save(row(ObjectType.FORM, "leave_apply", "1.0.0", Map.of("title", "本租户")));
        P3Exception missing = assertThrows(P3Exception.class,
                () -> service.resolve(other, instance, ObjectType.FORM, "leave_apply"));
        assertEquals(P3ErrorCodes.INSTANCE_NOT_FOUND, missing.errorCode());
    }

    @Test
    void ledgerFieldsMayBeAddedButNotRemovedOrRetyped() {
        platform.put(ObjectType.FORM, "leave_apply", "1.0.0", Map.of("fields", List.of(
                Map.of("code", "title", "type", "TEXT"),
                Map.of("code", "days", "type", "NUMBER"))));
        P3Exception dropped = assertThrows(P3Exception.class, () -> service.save(tenant, "actor", instance,
                ObjectType.FORM, "leave_apply", "1.0.0",
                Map.of("fields", List.of(Map.of("code", "title", "type", "TEXT")))));
        assertEquals(List.of("days"), dropped.details().get("fields"));

        P3Exception retyped = assertThrows(P3Exception.class, () -> service.save(tenant, "actor", instance,
                ObjectType.FORM, "leave_apply", "1.0.0",
                Map.of("fields", List.of(
                        Map.of("code", "title", "type", "TEXT"),
                        Map.of("code", "days", "type", "TEXT")))));
        assertEquals(List.of("days"), retyped.details().get("fields"));

        ResolvedObject added = service.save(tenant, "actor", instance, ObjectType.FORM, "leave_apply", "1.0.0",
                Map.of("fields", List.of(
                        Map.of("code", "title", "type", "TEXT", "name", "事由"),
                        Map.of("code", "days", "type", "NUMBER"),
                        Map.of("code", "note", "type", "TEXT"))));
        assertEquals(ResolutionSource.TENANT, added.source());
        assertEquals(3, ((List<?>) added.content().get("fields")).size());
    }

    @Test
    void saveWithoutPlatformFieldsSkipsTheLedgerCheckAndIsIdempotent() {
        service.save(tenant, "actor", instance, ObjectType.PROCESS, "leave_approval", "1.0.0", Map.of("name", "请假"));
        ResolvedObject again = service.save(tenant, "actor", instance, ObjectType.PROCESS, "leave_approval", "1.0.0",
                Map.of("name", "请假"));
        assertEquals(1, overrides.size());
        assertEquals("请假", again.content().get("name"));
        assertEquals(ResolutionSource.TENANT, again.source());
    }

    @Test
    void clearingTheOverrideFallsBackToThePlatformVersion() {
        platform.put(ObjectType.FORM, "leave_apply", "1.0.0", Map.of("title", "平台"));
        service.save(tenant, "actor", instance, ObjectType.FORM, "leave_apply", "1.0.0", Map.of("title", "租户"));
        ResolvedObject cleared = service.clear(tenant, instance, ObjectType.FORM, "leave_apply");
        assertEquals(ResolutionSource.PLATFORM, cleared.source());
        assertEquals("平台", cleared.content().get("title"));
        assertFalse(cleared.drift());
    }

    @Test
    void invalidCodeOrVersionIsRejected() {
        assertThrows(P3Exception.class,
                () -> service.resolve(tenant, instance, ObjectType.FORM, "Leave"));
        assertThrows(P3Exception.class, () -> service.save(tenant, "actor", instance,
                ObjectType.FORM, "leave_apply", "1", Map.of()));
    }

    private OverrideRecord row(ObjectType type, String code, String base, Map<String, Object> content) {
        return new OverrideRecord(tenant, instance, type, code, base, content, Instant.parse("2026-09-28T00:00:00Z"), "actor");
    }

    private static final class MemoryOverrides implements InstanceOverrideStore {
        private final Map<String, OverrideRecord> rows = new HashMap<>();

        @Override
        public Optional<OverrideRecord> find(UUID tenantId, UUID instanceId, ObjectType type, String code) {
            return Optional.ofNullable(rows.get(key(tenantId, instanceId, type, code)));
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

        int size() {
            return rows.size();
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
