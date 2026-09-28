package one.nexus.p3.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import one.nexus.p3.domain.P3ErrorCodes;
import one.nexus.p3.domain.P3Exception;
import org.junit.jupiter.api.Test;

class ProcessMappingServiceTest {
    private final UUID tenant = UUID.fromString("01900000-0000-7000-8000-000000000101");
    private final UUID instance = UUID.fromString("03123926-7307-4cc6-a634-8c6e87081189");
    private final UUID position = UUID.fromString("01900000-0000-7000-8000-000000000201");

    @Test
    void anUnmappedPositionBlocksStartUntilItIsMapped() {
        MemoryMappings mappings = new MemoryMappings();
        ProcessMappingService service = new ProcessMappingService(tenantId -> List.of(
                new ProcessPositionSource.PositionRef(instance, "leave_approval", "hr_clerk"),
                new ProcessPositionSource.PositionRef(instance, "leave_approval", "dept_lead")
        ), mappings);
        P3Exception blocked = assertThrows(P3Exception.class,
                () -> service.assertStartable(tenant, instance, "leave_approval"));
        assertEquals(P3ErrorCodes.INSTANCE_NEEDS_MAPPING, blocked.errorCode());
        assertEquals(409, blocked.httpStatus());
        assertEquals(ProcessMappingService.BLOCKED_MESSAGE, blocked.getMessage());
        assertEquals(List.of("hr_clerk", "dept_lead"), blocked.details().get("positions"));
        assertEquals(ProcessMappingService.BLOCKED_MESSAGE, service.blockReason(tenant, "leave_approval").orElseThrow());

        service.map(tenant, instance, "hr_clerk", position);
        assertThrows(P3Exception.class, () -> service.assertStartable(tenant, instance, "leave_approval"));
        service.map(tenant, instance, "dept_lead", position);
        service.assertStartable(tenant, instance, "leave_approval");
        assertTrue(service.blockReason(tenant, "leave_approval").isEmpty());

        service.unmap(tenant, instance, "hr_clerk");
        assertEquals(P3ErrorCodes.INSTANCE_NEEDS_MAPPING,
                assertThrows(P3Exception.class, () -> service.assertStartable(tenant, instance, "leave_approval")).errorCode());
    }

    @Test
    void aRoleOnlyProcessAndAnotherProcessStayStartable() {
        ProcessMappingService service = new ProcessMappingService(tenantId -> List.of(
                new ProcessPositionSource.PositionRef(instance, "leave_approval", "hr_clerk")
        ), new MemoryMappings());
        service.assertStartable(tenant, instance, "leave_record");
        assertTrue(service.blockReason(tenant, null).isEmpty());
        assertTrue(service.blockReason(tenant, "leave_record").isEmpty());
    }

    private static final class MemoryMappings implements PositionMappingStore {
        private final Set<String> codes = new HashSet<>();

        @Override
        public boolean mapped(UUID tenantId, UUID instanceId, String positionCode) {
            return codes.contains(tenantId + "|" + instanceId + "|" + positionCode);
        }

        @Override
        public void map(UUID tenantId, UUID instanceId, String positionCode, UUID positionId) {
            codes.add(tenantId + "|" + instanceId + "|" + positionCode);
        }

        @Override
        public void unmap(UUID tenantId, UUID instanceId, String positionCode) {
            codes.remove(tenantId + "|" + instanceId + "|" + positionCode);
        }
    }
}
