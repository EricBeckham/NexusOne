package one.nexus.p3.instance;

import java.util.Map;
import java.util.UUID;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.domain.P3ErrorCodes;
import one.nexus.p3.domain.P3Exception;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** PLX1 C6. Tenant comes from the server context, never from the path or body. */
@RestController
@RequestMapping("/api/v1/p3/instances/{instanceId}")
public class InstanceResolutionController {
    private final InstanceResolutionService resolution;

    public InstanceResolutionController(InstanceResolutionService resolution) {
        this.resolution = resolution;
    }

    @GetMapping("/resolved/{objectType}/{objectCode}")
    public ResolvedObject resolve(@PathVariable UUID instanceId, @PathVariable String objectType,
            @PathVariable String objectCode) {
        TenantContext tenant = requireTenant();
        return resolution.resolve(tenant.tenantId(), instanceId, type(objectType), objectCode);
    }

    @PutMapping("/overrides/{objectType}/{objectCode}")
    public ResolvedObject save(@PathVariable UUID instanceId, @PathVariable String objectType,
            @PathVariable String objectCode, @RequestBody OverrideBody body) {
        TenantContext tenant = requireTenant();
        OverrideBody request = body == null ? new OverrideBody(null, Map.of()) : body;
        return resolution.save(tenant.tenantId(), tenant.personId() == null ? null : tenant.personId().toString(),
                instanceId, type(objectType), objectCode, request.baseVersion(), request.content());
    }

    @DeleteMapping("/overrides/{objectType}/{objectCode}")
    public ResolvedObject clear(@PathVariable UUID instanceId, @PathVariable String objectType,
            @PathVariable String objectCode) {
        TenantContext tenant = requireTenant();
        return resolution.clear(tenant.tenantId(), instanceId, type(objectType), objectCode);
    }

    private static ObjectType type(String raw) {
        try {
            return ObjectType.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new P3Exception(P3ErrorCodes.INSTANCE_INVALID, 400,
                    "object_type must be FORM, PROCESS, or ROLE", Map.of());
        }
    }

    private static TenantContext requireTenant() {
        TenantContext context = TenantContextHolder.get();
        if (context == null) {
            throw new P3Exception(P3ErrorCodes.MISSING_TENANT, 403, "TenantContext is required", Map.of());
        }
        return context;
    }

    public record OverrideBody(String baseVersion, Map<String, Object> content) {}
}
