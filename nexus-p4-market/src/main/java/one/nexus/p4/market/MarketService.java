package one.nexus.p4.market;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import one.nexus.p1.domain.EntitlementPayload;
import one.nexus.p1.domain.EntitlementProjection;
import one.nexus.p1.port.TenantRepository;
import one.nexus.p4.market.MarketModels.*;

@Service
public class MarketService {
    private final MarketRepository market;
    private final TenantRepository tenants;
    private final Clock clock;

    @Autowired
    public MarketService(MarketRepository market, TenantRepository tenants) {
        this(market, tenants, Clock.systemUTC());
    }

    MarketService(MarketRepository market, TenantRepository tenants, Clock clock) {
        this.market = market;
        this.tenants = tenants;
        this.clock = clock;
    }

    @Transactional
    public AppPackage createApp(String code, String name, String summary) {
        requireText(code, "code"); requireText(name, "name");
        AppPackage app = new AppPackage(UUID.randomUUID(), code.trim(), name.trim(), summary, clock.instant());
        market.saveApp(app);
        return app;
    }

    @Transactional
    public AppVersion createVersion(UUID appId, String version, String notes) {
        app(appId); requireText(version, "version");
        AppVersion result = new AppVersion(UUID.randomUUID(), appId, version.trim(), VersionStatus.DRAFT,
                notes, clock.instant(), null);
        market.saveVersion(result);
        return result;
    }

    @Transactional
    public AppVersion transition(UUID versionId, VersionStatus target) {
        AppVersion current = version(versionId);
        boolean allowed = switch (target) {
            case IN_REVIEW -> current.status() == VersionStatus.DRAFT;
            case APPROVED -> current.status() == VersionStatus.IN_REVIEW;
            case PUBLISHED -> current.status() == VersionStatus.APPROVED;
            case UNPUBLISHED -> current.status() == VersionStatus.PUBLISHED;
            default -> false;
        };
        if (!allowed) throw new IllegalStateException("invalid version transition: " + current.status() + " -> " + target);
        Instant now = clock.instant();
        AppVersion next = new AppVersion(current.id(), current.appId(), current.version(), target,
                current.releaseNotes(), current.createdAt(), target == VersionStatus.PUBLISHED ? now : current.publishedAt());
        market.saveVersion(next);
        return next;
    }

    @Transactional
    public Sku defineSku(String code, UUID appId, String name, Map<String, EntitlementSpec> entitlements) {
        app(appId); requireText(code, "sku code");
        if (entitlements == null || entitlements.isEmpty()) throw new IllegalArgumentException("entitlements required");
        Sku sku = new Sku(code.trim(), appId, name, Map.copyOf(entitlements));
        market.saveSku(sku);
        return sku;
    }

    @Transactional
    public Installation subscribeAndInstall(UUID tenantId, String skuCode, UUID versionId) {
        tenants.findById(tenantId).orElseThrow(() -> new IllegalArgumentException("tenant not found"));
        Sku sku = market.sku(skuCode).orElseThrow(() -> new IllegalArgumentException("sku not found"));
        AppVersion version = version(versionId);
        if (version.status() != VersionStatus.PUBLISHED || !version.appId().equals(sku.appId())) {
            throw new IllegalStateException("only a published version matching the SKU can be installed");
        }
        Instant now = clock.instant();
        Subscription subscription = new Subscription(UUID.randomUUID(), tenantId, skuCode,
                SubscriptionStatus.ACTIVE, now, null);
        market.saveSubscription(subscription);
        Map<String, EntitlementPayload.EntitlementItem> items = new LinkedHashMap<>();
        sku.entitlements().forEach((key, spec) -> items.put(key,
                new EntitlementPayload.EntitlementItem(spec.enabled(), spec.quota(), spec.attributes())));
        long nextVersion = tenants.findProjection(tenantId).map(p -> p.version() + 1).orElse(1L);
        tenants.updateProjection(EntitlementProjection.fromSubscription(tenantId, skuCode,
                subscription.id(), new EntitlementPayload(EntitlementPayload.SCHEMA_V1, items), now, nextVersion));
        Installation installation = new Installation(UUID.randomUUID(), tenantId, sku.appId(), versionId,
                InstallationStatus.INSTALLED, now, now);
        market.saveInstallation(installation);
        return installation;
    }

    @Transactional
    public Installation upgrade(UUID installationId, UUID versionId) {
        Installation current = installation(installationId);
        AppVersion target = version(versionId);
        if (current.status() != InstallationStatus.INSTALLED || target.status() != VersionStatus.PUBLISHED
                || !current.appId().equals(target.appId())) throw new IllegalStateException("invalid upgrade target");
        Installation next = new Installation(current.id(), current.tenantId(), current.appId(), target.id(),
                current.status(), current.installedAt(), clock.instant());
        market.saveInstallation(next);
        return next;
    }

    @Transactional
    public Installation uninstall(UUID installationId) {
        Installation current = installation(installationId);
        Installation next = new Installation(current.id(), current.tenantId(), current.appId(), current.versionId(),
                InstallationStatus.UNINSTALLED, current.installedAt(), clock.instant());
        market.saveInstallation(next);
        return next;
    }

    @Transactional(readOnly = true) public List<CatalogItem> catalog() { return market.catalog(); }
    private AppPackage app(UUID id) { return market.app(id).orElseThrow(() -> new IllegalArgumentException("app not found")); }
    private AppVersion version(UUID id) { return market.version(id).orElseThrow(() -> new IllegalArgumentException("version not found")); }
    private Installation installation(UUID id) { return market.installation(id).orElseThrow(() -> new IllegalArgumentException("installation not found")); }
    private static void requireText(String value, String field) { if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " required"); }
}
