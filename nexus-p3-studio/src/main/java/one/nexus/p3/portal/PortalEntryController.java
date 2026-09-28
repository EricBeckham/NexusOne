package one.nexus.p3.portal;

import java.util.List;
import java.util.Map;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.domain.P3ErrorCodes;
import one.nexus.p3.domain.P3Exception;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** PLX1 C1 display. Listing an entry does not grant write. */
@RestController
@RequestMapping("/api/v1/p3/portal")
public class PortalEntryController {
    private final PortalEntryService portal;

    public PortalEntryController(PortalEntryService portal) {
        this.portal = portal;
    }

    @GetMapping("/entries")
    public PortalEntries entries() {
        TenantContext context = TenantContextHolder.get();
        if (context == null) {
            throw new P3Exception(P3ErrorCodes.MISSING_TENANT, 403, "TenantContext is required", Map.of());
        }
        return new PortalEntries(portal.visible(context.tenantId()));
    }

    public record PortalEntries(List<FunctionEntry> entries) {}
}
