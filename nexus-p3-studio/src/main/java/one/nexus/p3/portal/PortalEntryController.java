package one.nexus.p3.portal;

import java.util.List;
import java.util.Map;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.domain.P3ErrorCodes;
import one.nexus.p3.domain.P3Exception;
import one.nexus.p3.instance.AppInstanceQuery;
import one.nexus.p3.instance.AppInstanceState;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** PLX1 C1 display. Listing an entry does not grant write. */
@RestController
@RequestMapping("/api/v1/p3/portal")
public class PortalEntryController {
    private final PortalEntryService portal;
    private final AppInstanceQuery query;

    public PortalEntryController(PortalEntryService portal, AppInstanceQuery query) {
        this.portal = portal;
        this.query = query;
    }

    @GetMapping("/entries")
    public PortalEntries entries() {
        return new PortalEntries(portal.visible(requireTenant().tenantId()));
    }

    /** Current tenant instances, including uninstalled rows. The tenant comes from the server context. */
    @GetMapping("/instances")
    public PortalInstances instances() {
        requireTenant();
        return new PortalInstances(query.current());
    }

    private static TenantContext requireTenant() {
        TenantContext context = TenantContextHolder.get();
        if (context == null) {
            throw new P3Exception(P3ErrorCodes.MISSING_TENANT, 403, "TenantContext is required", Map.of());
        }
        return context;
    }

    public record PortalEntries(List<PortalEntry> entries) {}

    public record PortalInstances(List<AppInstanceState> instances) {}
}
