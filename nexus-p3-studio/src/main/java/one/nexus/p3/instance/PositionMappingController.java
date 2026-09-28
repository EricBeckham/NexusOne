package one.nexus.p3.instance;

import java.util.Map;
import java.util.UUID;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.domain.P3ErrorCodes;
import one.nexus.p3.domain.P3Exception;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Binds a manifest POSITION code to a tenant position. Takes effect without reinstalling. */
@RestController
@RequestMapping("/api/v1/p3/instances/{instanceId}/position-mappings")
public class PositionMappingController {
    private final ProcessMappingService mappings;

    public PositionMappingController(ProcessMappingService mappings) {
        this.mappings = mappings;
    }

    @PutMapping("/{positionCode}")
    public void map(@PathVariable UUID instanceId, @PathVariable String positionCode, @RequestBody MapBody body) {
        TenantContext tenant = requireTenant();
        mappings.map(tenant.tenantId(), instanceId, positionCode, body == null ? null : body.positionId());
    }

    @DeleteMapping("/{positionCode}")
    public void unmap(@PathVariable UUID instanceId, @PathVariable String positionCode) {
        TenantContext tenant = requireTenant();
        mappings.unmap(tenant.tenantId(), instanceId, positionCode);
    }

    private static TenantContext requireTenant() {
        TenantContext context = TenantContextHolder.get();
        if (context == null) {
            throw new P3Exception(P3ErrorCodes.MISSING_TENANT, 403, "TenantContext is required", Map.of());
        }
        return context;
    }

    public record MapBody(UUID positionId) {}
}
