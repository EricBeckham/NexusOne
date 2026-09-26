package one.nexus.p4.market;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.p1.domain.EntitlementProjection;
import one.nexus.p1.domain.ProjectionSource;
import one.nexus.p1.port.TenantRepository;
import one.nexus.p4.market.MarketModels.EntitlementSpec;
import one.nexus.p4.market.MarketModels.Installation;
import one.nexus.p4.market.MarketModels.Sku;
import one.nexus.p4.market.MarketModels.Subscription;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Read-only commercial entitlement projection from subscription, SKU, and installation. No prices. */
@Service
public class CommercialEntitlementService {
    public record EntitlementLine(String key, boolean enabled, Integer quota) {}

    /**
     * XB10: per-subscription summary for the subscription table. Counts come from the SKU entitlement lines;
     * {@code projected} says whether the tenant's P1 projection (platform.entitlement_projection, one row per
     * tenant) is this subscription's: true / false, or null when the projection cannot be read. No money fields.
     */
    public record EntitlementSummary(int enabledCount, int totalCount, Boolean projected, Long projectionVersion,
            Instant projectedAt) {}

    public record CommercialEntitlement(
            UUID subscriptionId,
            UUID tenantId,
            String skuCode,
            String skuName,
            String subscriptionStatus,
            UUID appId,
            UUID installationId,
            String installationStatus,
            List<EntitlementLine> lines,
            EntitlementSummary summary) {
        public CommercialEntitlement {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    private final MarketRepository market;
    private final TenantRepository tenants;

    @Autowired
    public CommercialEntitlementService(MarketRepository market, ObjectProvider<TenantRepository> tenants) {
        this(market, tenants.getIfAvailable());
    }

    CommercialEntitlementService(MarketRepository market) {
        this(market, (TenantRepository) null);
    }

    CommercialEntitlementService(MarketRepository market, TenantRepository tenants) {
        this.market = market;
        this.tenants = tenants;
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
        Optional<Optional<EntitlementProjection>> projection = projection(tenantId);
        for (Subscription subscription : rows) {
            views.add(project(subscription, installations, projection));
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
        return project(subscription, installations, projection(tenantId));
    }

    /** Outer empty = the projection could not be read (unknown); inner empty = the tenant has no projection. */
    private Optional<Optional<EntitlementProjection>> projection(UUID tenantId) {
        if (tenants == null) return Optional.empty();
        try {
            return Optional.of(tenants.findProjection(tenantId));
        } catch (RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    private CommercialEntitlement project(Subscription subscription, List<Installation> installations,
            Optional<Optional<EntitlementProjection>> projection) {
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
                lines,
                summary(subscription, lines, projection));
    }

    private static EntitlementSummary summary(Subscription subscription, List<EntitlementLine> lines,
            Optional<Optional<EntitlementProjection>> projection) {
        int enabled = (int) lines.stream().filter(EntitlementLine::enabled).count();
        if (projection.isEmpty()) return new EntitlementSummary(enabled, lines.size(), null, null, null);
        boolean projected = projection.get()
                .filter(row -> row.source() == ProjectionSource.SUBSCRIPTION)
                .map(row -> subscription.id().equals(row.subscriptionId()))
                .orElse(false);
        EntitlementProjection row = projected ? projection.get().get() : null;
        return new EntitlementSummary(enabled, lines.size(), projected,
                row == null ? null : row.version(), row == null ? null : row.projectedAt());
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
