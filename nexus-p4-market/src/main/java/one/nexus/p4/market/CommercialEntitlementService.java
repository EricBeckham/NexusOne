package one.nexus.p4.market;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.p4.market.MarketModels.EntitlementSpec;
import one.nexus.p4.market.MarketModels.Installation;
import one.nexus.p4.market.MarketModels.Sku;
import one.nexus.p4.market.MarketModels.Subscription;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Read-only commercial entitlement projection from subscription, SKU, and installation. No prices. */
@Service
public class CommercialEntitlementService {
    public record EntitlementLine(String key, boolean enabled, Integer quota) {}

    public record CommercialEntitlement(
            UUID subscriptionId,
            UUID tenantId,
            String skuCode,
            String skuName,
            String subscriptionStatus,
            UUID appId,
            UUID installationId,
            String installationStatus,
            List<EntitlementLine> lines) {
        public CommercialEntitlement {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    private final MarketRepository market;

    public CommercialEntitlementService(MarketRepository market) {
        this.market = market;
    }

    @Transactional(readOnly = true)
    public List<CommercialEntitlement> list() {
        UUID tenantId = tenantId();
        List<Subscription> rows = market.subscriptions().stream()
                .filter(row -> tenantId.equals(row.tenantId()))
                .toList();
        List<Installation> installations = market.installations().stream()
                .filter(row -> tenantId.equals(row.tenantId()))
                .toList();
        List<CommercialEntitlement> views = new ArrayList<>();
        for (Subscription subscription : rows) {
            views.add(project(subscription, installations));
        }
        return List.copyOf(views);
    }

    @Transactional(readOnly = true)
    public CommercialEntitlement get(UUID subscriptionId) {
        UUID tenantId = tenantId();
        Subscription subscription = market.subscriptionForCurrentTenant(subscriptionId)
                .filter(row -> tenantId.equals(row.tenantId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "commercial entitlement not found"));
        List<Installation> installations = market.installations().stream()
                .filter(row -> tenantId.equals(row.tenantId()))
                .toList();
        return project(subscription, installations);
    }

    private CommercialEntitlement project(Subscription subscription, List<Installation> installations) {
        Sku sku = market.sku(subscription.skuCode()).orElse(null);
        Installation installation = sku == null ? null : installations.stream()
                .filter(row -> sku.appId().equals(row.appId()))
                .max(Comparator.comparing(Installation::updatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
        List<EntitlementLine> lines = new ArrayList<>();
        if (sku != null) {
            sku.entitlements().forEach((key, spec) -> lines.add(line(key, spec)));
        }
        return new CommercialEntitlement(
                subscription.id(),
                subscription.tenantId(),
                subscription.skuCode(),
                sku == null ? null : sku.name(),
                subscription.status().name(),
                sku == null ? null : sku.appId(),
                installation == null ? null : installation.id(),
                installation == null ? null : installation.status().name(),
                lines);
    }

    private static EntitlementLine line(String key, EntitlementSpec spec) {
        return new EntitlementLine(key, spec.enabled(), spec.quota());
    }

    private static UUID tenantId() {
        AccessContext context = AccessContextHolder.currentOrNull();
        if (!(context instanceof TenantContext tenant)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TenantContext is required");
        }
        try {
            return UUID.fromString(tenant.tenantId());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TenantContext has an invalid tenant id");
        }
    }
}
