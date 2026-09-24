package one.nexus.p4.market;

import java.util.List;
import java.util.UUID;
import one.nexus.p4.market.CommercialEntitlementService.CommercialEntitlement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only commercial entitlement projection. Does not accept prices or write a projection. */
@RestController
@RequestMapping("/api/v1/p4/commercial-entitlements")
public class CommercialEntitlementController {
    private final CommercialEntitlementService entitlements;

    public CommercialEntitlementController(CommercialEntitlementService entitlements) {
        this.entitlements = entitlements;
    }

    @GetMapping
    public List<CommercialEntitlement> list() {
        return entitlements.list();
    }

    @GetMapping("/{id}")
    public CommercialEntitlement get(@PathVariable UUID id) {
        return entitlements.get(id);
    }
}
