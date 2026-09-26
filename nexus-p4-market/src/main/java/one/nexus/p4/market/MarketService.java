package one.nexus.p4.market;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.context.AccessContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import one.nexus.p1.domain.EntitlementPayload;
import one.nexus.p1.domain.EntitlementProjection;
import one.nexus.p1.port.TenantRepository;
import one.nexus.p1.port.AuditPort;
import one.nexus.p1.domain.AuditEvent;
import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p2.domain.Model;
import one.nexus.p2.session.AdminMfaPolicy;
import one.nexus.p4.market.MarketModels.*;

@Service
public class MarketService {
    private static final ErrorCode SUBSCRIBE_DEFINITION_MISSING = ErrorCode.of("P4", "MARKET", 1);
    private static final ErrorCode SUBSCRIBE_ADMIN_REQUIRED = ErrorCode.of("P4", "MARKET", 2);
    private static final ErrorCode SUBSCRIBE_CONFLICT = ErrorCode.of("P4", "MARKET", 3);

    private final MarketRepository market;
    private final TenantRepository tenants;
    private final Clock clock;
    private final IdentityRepository identities;
    private final AuditPort audit;
    private final ProductDefaultPermissionProvisioner permissionPacks;

    @Autowired
    public MarketService(MarketRepository market, TenantRepository tenants,
            ObjectProvider<IdentityRepository> identities, ObjectProvider<AuditPort> audit,
            ObjectProvider<ProductDefaultPermissionProvisioner> permissionPacks) {
        this(market, tenants, Clock.systemUTC(), identities.getIfAvailable(), audit.getIfAvailable(), permissionPacks.getIfAvailable());
    }

    MarketService(MarketRepository market, TenantRepository tenants, Clock clock) {
        this(market, tenants, clock, null, null, null);
    }

    MarketService(MarketRepository market, TenantRepository tenants, Clock clock,
            IdentityRepository identities, AuditPort audit) {
        this(market, tenants, clock, identities, audit, null);
    }

    MarketService(MarketRepository market, TenantRepository tenants, Clock clock,
            IdentityRepository identities, AuditPort audit, ProductDefaultPermissionProvisioner permissionPacks) {
        this.market = market;
        this.tenants = tenants;
        this.clock = clock;
        this.identities = identities;
        this.audit = audit;
        this.permissionPacks = permissionPacks;
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
        assertTenant(requireTenantId(), tenantId);
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

    /** Tenant-admin self-service install. Idempotency contract is explicit conflict on duplicates. */
    @Transactional
    public Installation subscribeProduct(UUID tenantId, String skuCode, UUID versionId) {
        assertTenant(requireTenantId(), tenantId);
        requireTenantAdmin(tenantId);
        ProductDefinition definition = market.productDefinitionForSku(skuCode)
                .orElseThrow(() -> new NexusException(SUBSCRIBE_DEFINITION_MISSING, 404,
                        "catalog SKU has no product definition"));
        if (market.subscriptions().stream().anyMatch(row -> row.skuCode().equals(skuCode)
                && row.status() == SubscriptionStatus.ACTIVE)) {
            throw new NexusException(SUBSCRIBE_CONFLICT, 409,
                    "tenant already has an active subscription for this SKU");
        }
        Installation installation = subscribeAndInstall(tenantId, skuCode, versionId);
        market.saveInstallationProductPin(new InstallationProductPin(installation.id(), definition.id(),
                definition.formTemplateVersionId(), definition.processDefinitionKey(),
                definition.processDefinitionVersion(), clock.instant()));
        if (permissionPacks == null) throw new IllegalStateException("default permission pack provisioner is required");
        permissionPacks.provision(tenantId, definition);
        if (audit == null) throw new IllegalStateException("audit port is required for tenant self-subscription");
        AccessContext context = AccessContextHolder.current();
        UUID actorId = UUID.fromString(((TenantContext) context).actor().actorId());
        audit.append(new AuditEvent(UUID.randomUUID(), "market.subscription.self_service_activated", AuditEvent.VERSION,
                clock.instant(), tenantId, actorId, tenantId, false, null, context.traceId(), context.traceId(), null,
                AuditEvent.OUTCOME_ACCEPTED, "installation", installation.id().toString(),
                Map.of("sku_code", skuCode, "product_definition_id", definition.id().toString(),
                        "form_template_version_id", definition.formTemplateVersionId().toString())));
        return installation;
    }

    private void requireTenantAdmin(UUID tenantId) {
        AccessContext context = AccessContextHolder.currentOrNull();
        if (!(context instanceof TenantContext tenant) || !tenant.tenantId().equals(tenantId.toString())
                || identities == null) {
            throw new NexusException(SUBSCRIBE_ADMIN_REQUIRED, 403,
                    "tenant administrator context is required");
        }
        UUID subject;
        try { subject = UUID.fromString(tenant.actor().actorId()); }
        catch (IllegalArgumentException ex) {
            throw new NexusException(SUBSCRIBE_ADMIN_REQUIRED, 403, "authenticated actor is invalid");
        }
        Model.Person person = identities.findPerson(tenantId, subject)
                .or(() -> identities.findPersonByAccount(tenantId, subject))
                .orElseThrow(() -> new NexusException(SUBSCRIBE_ADMIN_REQUIRED, 403,
                        "tenant administrator role is required"));
        boolean tenantAdmin = identities.listEmploymentsByPerson(tenantId, person.id()).stream()
                .filter(e -> e.status() == Model.EmploymentStatus.ACTIVE)
                .map(e -> identities.findPosition(tenantId, e.positionId()).orElse(null))
                .anyMatch(position -> position != null && "TENANT_ADMIN".equalsIgnoreCase(position.code()));
        if (!tenantAdmin || person.status() != Model.PersonStatus.ACTIVE) {
            throw new NexusException(SUBSCRIBE_ADMIN_REQUIRED, 403,
                    "tenant administrator role is required");
        }
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

    /**
     * XB6: honest read of the upgrade steps. Nothing persists step state (upgrade only switches the version),
     * so the answer is the step catalog with every step UNKNOWN — never a fabricated DONE/COMPLETED.
     */
    @Transactional(readOnly = true)
    public UpgradeSteps upgradeSteps(UUID installationId) {
        Installation current = installation(installationId);
        return UpgradeSteps.unrecorded(current.id());
    }

    /**
     * XB7: upgrade facts for an installation. Versions carry only release notes, so compatibility, schema
     * change and data migration are null (暂无) with a reason; rollback describes the real version switch.
     */
    @Transactional(readOnly = true)
    public UpgradeFacts upgradeFacts(UUID installationId) {
        return UpgradeFacts.current(installation(installationId));
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
    @Transactional(readOnly = true) public List<AppPackage> apps() { return market.apps(); }
    @Transactional(readOnly = true) public AppPackage getApp(UUID id) {
        return market.app(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "app not found"));
    }
    @Transactional(readOnly = true) public List<AppVersion> versions(UUID appId) {
        getApp(appId);
        return market.versions(appId);
    }
    @Transactional(readOnly = true) public AppVersion getVersion(UUID id) {
        return market.version(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "version not found"));
    }
    @Transactional(readOnly = true) public List<Sku> skus() { return market.skus(); }
    @Transactional(readOnly = true) public Sku getSku(String code) {
        return market.sku(code).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "sku not found"));
    }
    @Transactional(readOnly = true) public List<Subscription> subscriptions() {
        UUID tenantId = requireTenantId();
        List<Subscription> rows = market.subscriptions();
        rows.forEach(row -> assertTenant(tenantId, row.tenantId()));
        return rows;
    }
    @Transactional(readOnly = true) public Subscription getSubscription(UUID id) {
        UUID tenantId = requireTenantId();
        Subscription row = market.subscriptionForCurrentTenant(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "subscription not found"));
        assertTenant(tenantId, row.tenantId());
        return row;
    }
    @Transactional(readOnly = true) public List<Installation> installations() {
        UUID tenantId = requireTenantId();
        List<Installation> rows = market.installations();
        rows.forEach(row -> assertTenant(tenantId, row.tenantId()));
        return rows;
    }
    @Transactional(readOnly = true) public Installation getInstallation(UUID id) {
        UUID tenantId = requireTenantId();
        Installation row = market.installationForCurrentTenant(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "installation not found"));
        assertTenant(tenantId, row.tenantId());
        return row;
    }
    @Transactional(readOnly = true) public InstallationProductPin installationProductPin(UUID installationId) {
        getInstallation(installationId);
        return market.installationProductPin(installationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "installation product pin not found"));
    }

    private AppPackage app(UUID id) { return market.app(id).orElseThrow(() -> new IllegalArgumentException("app not found")); }
    private AppVersion version(UUID id) { return market.version(id).orElseThrow(() -> new IllegalArgumentException("version not found")); }
    private Installation installation(UUID id) {
        UUID tenantId = requireTenantId();
        Installation row = market.installationForCurrentTenant(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "installation not found"));
        assertTenant(tenantId, row.tenantId());
        return row;
    }
    private static UUID requireTenantId() {
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
    private static void assertTenant(UUID expected, UUID actual) {
        if (!expected.equals(actual)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "tenant scope mismatch");
        }
    }
    private static void requireText(String value, String field) { if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " required"); }
}
