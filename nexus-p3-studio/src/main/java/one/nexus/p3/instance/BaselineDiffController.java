package one.nexus.p3.instance;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.domain.P3ErrorCodes;
import one.nexus.p3.domain.P3Exception;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** PLC8. Tenant comes from the server context. */
@RestController
@RequestMapping("/api/v1/p3")
public class BaselineDiffController {
    private final BaselineDiffService diffs;

    public BaselineDiffController(BaselineDiffService diffs) {
        this.diffs = diffs;
    }

    @GetMapping("/instance-overrides/drift")
    public List<BaselineDiff> drift() {
        return diffs.drift(requireTenant().tenantId());
    }

    @GetMapping("/instances/{instanceId}/overrides/{objectType}/{objectCode}/diff")
    public BaselineDiff diff(@PathVariable UUID instanceId, @PathVariable String objectType,
            @PathVariable String objectCode) {
        return diffs.diff(requireTenant().tenantId(), instanceId, type(objectType), objectCode);
    }

    @PostMapping("/instances/{instanceId}/overrides/{objectType}/{objectCode}/merge")
    public ResolvedObject merge(@PathVariable UUID instanceId, @PathVariable String objectType,
            @PathVariable String objectCode, @RequestBody MergeRequest request) {
        TenantContext tenant = requireTenant();
        Map<String, String> choices = request == null || request.choices() == null ? Map.of() : request.choices();
        return diffs.merge(tenant.tenantId(), tenant.personId() == null ? null : tenant.personId().toString(),
                instanceId, type(objectType), objectCode, choices);
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

    public record MergeRequest(Map<String, String> choices) {}
}
