package one.nexus.p3.instance;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import one.nexus.p3.domain.P3ErrorCodes;
import one.nexus.p3.domain.P3Exception;
import org.springframework.stereotype.Service;

/**
 * PLX1 C2 install check. A process is {@code NEEDS_MAPPING} when any POSITION code on that instance is unbound.
 * ROLE assignees are ignored. Mapping takes effect immediately.
 */
@Service
public class ProcessMappingService implements ProcessStartGate {
    public static final String BLOCKED_MESSAGE = "管理员尚未配置审批岗位";
    private static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9_]{0,23}$");

    private final ProcessPositionSource positions;
    private final PositionMappingStore mappings;

    public ProcessMappingService(ProcessPositionSource positions, PositionMappingStore mappings) {
        this.positions = positions;
        this.mappings = mappings;
    }

    public void map(UUID tenantId, UUID instanceId, String positionCode, UUID positionId) {
        require(tenantId, instanceId, positionCode, positionId);
        mappings.map(tenantId, instanceId, positionCode, positionId);
    }

    public void unmap(UUID tenantId, UUID instanceId, String positionCode) {
        if (positionCode == null || !CODE.matcher(positionCode).matches()) {
            throw invalid("position_code is invalid");
        }
        mappings.unmap(tenantId, instanceId, positionCode);
    }

    public void assertStartable(UUID tenantId, UUID instanceId, String processKey) {
        List<String> missing = missing(tenantId, instanceId, processKey);
        if (!missing.isEmpty()) {
            throw new P3Exception(P3ErrorCodes.INSTANCE_NEEDS_MAPPING, 409, BLOCKED_MESSAGE, java.util.Map.of(
                    "instance_id", instanceId.toString(),
                    "process_key", processKey,
                    "positions", List.copyOf(missing)));
        }
    }

    @Override
    public Optional<String> blockReason(UUID tenantId, String processKey) {
        if (processKey == null || processKey.isBlank()) {
            return Optional.empty();
        }
        boolean blocked = positions.positions(tenantId).stream()
                .filter(ref -> processKey.equals(ref.processKey()))
                .anyMatch(ref -> !mappings.mapped(tenantId, ref.instanceId(), ref.positionCode()));
        return blocked ? Optional.of(BLOCKED_MESSAGE) : Optional.empty();
    }

    private List<String> missing(UUID tenantId, UUID instanceId, String processKey) {
        List<String> missing = new ArrayList<>();
        for (ProcessPositionSource.PositionRef ref : positions.positions(tenantId)) {
            if (!instanceId.equals(ref.instanceId()) || !processKey.equals(ref.processKey())) {
                continue;
            }
            if (!mappings.mapped(tenantId, instanceId, ref.positionCode()) && !missing.contains(ref.positionCode())) {
                missing.add(ref.positionCode());
            }
        }
        return missing;
    }

    private static void require(UUID tenantId, UUID instanceId, String positionCode, UUID positionId) {
        if (tenantId == null || instanceId == null || positionId == null
                || positionCode == null || !CODE.matcher(positionCode).matches()) {
            throw invalid("position mapping is invalid");
        }
    }

    private static P3Exception invalid(String message) {
        return new P3Exception(P3ErrorCodes.INSTANCE_INVALID, 400, message, java.util.Map.of());
    }
}
