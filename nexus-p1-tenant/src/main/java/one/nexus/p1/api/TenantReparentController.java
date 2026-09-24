package one.nexus.p1.api;

import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import one.nexus.p1.application.TenantRelationService.TenantRelationNode;
import one.nexus.p1.application.TenantReparentService;

/** Platform-admin parent change. Does not expose quotas or other business rows. */
@RestController
@RequestMapping("/api/v1/p1")
public class TenantReparentController {
    private final TenantReparentService reparent;

    public TenantReparentController(TenantReparentService reparent) {
        this.reparent = reparent;
    }

    @RequestMapping(path = "/tenants/{id}/parent", method = {RequestMethod.PUT, RequestMethod.PATCH})
    public TenantRelationNode reparent(@PathVariable UUID id, @RequestBody(required = false) ParentBody body) {
        return reparent.reparent(id, body == null ? null : body.parentTenantId());
    }

    public record ParentBody(UUID parentTenantId) {}
}
