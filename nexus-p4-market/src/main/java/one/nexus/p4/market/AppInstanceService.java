package one.nexus.p4.market;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import one.nexus.common.id.UuidV7;
import one.nexus.event.OutboxAppendCommand;
import one.nexus.event.OutboxPort;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.p1.domain.EntitlementPayload;
import one.nexus.p1.domain.EntitlementProjection;
import one.nexus.p1.port.TenantRepository;
import one.nexus.p3.template.PlatformTemplateCatalog;
import one.nexus.p3.template.PlatformTemplateCatalog.TemplateVersion;
import one.nexus.p4.market.MarketModels.Sku;
import one.nexus.p4.market.MarketModels.Subscription;
import one.nexus.p4.market.MarketModels.SubscriptionStatus;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.pack.RolePackService.AppliedRole;
import one.nexus.permission.pack.RolePackService.GrantSpec;
import one.nexus.permission.pack.RolePackService.RolePackCommand;
import one.nexus.permission.pack.RolePackService.RoleSpec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * PLB7: subscribing to a product-native SKU (PLB5, {@code sku.app_id IS NULL}) installs every application of the
 * product as a tenant instance (PLX1 C4), landing each application's role pack (PLX1 C3, PLA7) — not bound to any
 * position (D4). Legacy single-app SKUs keep the {@code ProductDefinition} path in {@link MarketService}.
 * <ul>
 *   <li>Everything is validated before the first write; instances, subscription and projection share the caller's
 *       transaction. Role packs live in the permission database and are idempotent; call order puts them before
 *       commit, so a failure rolls the market side back and a leftover unbound role affects nobody (C3 r3.2).</li>
 *   <li>An application the tenant already has installed is reused, not installed twice ("复用").</li>
 *   <li>Tenant self-service emits a {@code p4.app_instance} outbox event per new instance, in the payload shape C's
 *       consumer parses. The platform-approved path emits none (C4 r3): C reconciles through
 *       {@link one.nexus.p3.instance.AppInstanceQuery}.</li>
 * </ul>
 */
@Service
@DependsOn("flyway")
public class AppInstanceService {
    public static final String DESTINATION = "p4.app_instance";
    public static final String DEFAULT_MODE = "INHERIT";
    /**
     * Event payloads keep explicit nulls: C4 requires every key to be present ({@code previous_version: null}), and
     * the application mapper drops null properties.
     */
    private static final ObjectMapper EVENT_JSON = new ObjectMapper();

    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final MarketRepository market;
    private final TenantRepository tenants;
    private final PlatformTemplateCatalog templates;
    private final ObjectProvider<PermissionModule> permissions;
    private final ObjectProvider<OutboxPort> outbox;
    private final Clock clock;

    @Autowired
    public AppInstanceService(JdbcClient jdbc, ObjectMapper json, MarketRepository market, TenantRepository tenants,
            PlatformTemplateCatalog templates, ObjectProvider<PermissionModule> permissions,
            ObjectProvider<OutboxPort> outbox) {
        this(jdbc, json, market, tenants, templates, permissions, outbox, Clock.systemUTC());
    }

    AppInstanceService(JdbcClient jdbc, ObjectMapper json, MarketRepository market, TenantRepository tenants,
            PlatformTemplateCatalog templates, ObjectProvider<PermissionModule> permissions,
            ObjectProvider<OutboxPort> outbox, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.market = market;
        this.tenants = tenants;
        this.templates = templates;
        this.permissions = permissions;
        this.outbox = outbox;
        this.clock = clock;
    }

    /** A tenant application instance. {@code reused} marks an instance that already existed before this install. */
    public record AppInstance(UUID id, UUID tenantId, UUID productId, String productCode, String skuCode,
            UUID subscriptionId, UUID appId, String appCode, UUID appVersionId, String version, String status,
            String mode, List<String> entries, long revision, Instant changedAt, boolean reused) {}

    /** The validated install plan of a product-native SKU. */
    public record Plan(String skuCode, UUID productId, String productCode, List<PlannedApp> apps) {}

    public record PlannedApp(UUID appId, UUID versionId, TemplateVersion template, List<String> entries) {}

    /** Tenant upgrade. {@code rolePack} reports grants a newer manifest dropped; those grants are not revoked. */
    public record UpgradeResult(AppInstance instance, List<AppliedRole> rolePack) {}

    /** A newer published version an INHERIT instance can move to. {@code minor} keeps the same major number. */
    public record UpgradeOffer(UUID instanceId, String appCode, String currentVersion, UUID targetVersionId,
            String targetVersion, boolean minor) {}

    public static boolean productNative(Sku sku) {
        return sku != null && sku.appId() == null;
    }

    /** Validates that the SKU is product-native and every pinned application version is a published manifest. */
    @Transactional(readOnly = true)
    public Plan plan(String skuCode) {
        Sku sku = market.sku(skuCode).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "sku not found"));
        if (!productNative(sku)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "sku is a legacy single-app sku");
        }
        Map<String, Object> product = jdbc.sql("""
                SELECT p.id, p.code, p.status FROM market.sku s JOIN market.product p ON p.id = s.product_id
                WHERE s.code = :sku
                """).param("sku", skuCode).query().singleRow();
        if (!"PUBLISHED".equals(product.get("status"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "product is not published");
        }
        UUID productId = (UUID) product.get("id");
        List<Map<String, Object>> rows = jdbc.sql("""
                SELECT pa.app_id, pa.app_version_id, v.status FROM market.product_app pa
                LEFT JOIN market.app_version v ON v.id = pa.app_version_id
                WHERE pa.product_id = :product ORDER BY pa.sort_order, pa.app_id
                """).param("product", productId).query().listOfRows();
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "product has no applications");
        }
        List<PlannedApp> apps = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            UUID versionId = (UUID) row.get("app_version_id");
            if (versionId == null || !"PUBLISHED".equals(row.get("status"))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "product application version is not published");
            }
            TemplateVersion template = templates.published(versionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "application version has no manifest"));
            List<String> entries = new ArrayList<>();
            for (Map<String, Object> entry : template.entries()) {
                if (entry.get("key") instanceof String key && !key.isBlank()) {
                    entries.add(key);
                }
            }
            apps.add(new PlannedApp((UUID) row.get("app_id"), versionId, template, List.copyOf(entries)));
        }
        return new Plan(skuCode, productId, String.valueOf(product.get("code")), List.copyOf(apps));
    }

    /** Tenant self-service. The caller has checked the tenant-administrator position and the tenant scope. */
    @Transactional
    public List<AppInstance> subscribe(UUID tenantId, String skuCode) {
        UUID bound = boundTenant();
        if (!bound.equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "tenant scope mismatch");
        }
        Plan plan = plan(skuCode);
        if (market.hasActiveSubscription(tenantId, skuCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "tenant already has an active subscription for this SKU");
        }
        Instant now = clock.instant();
        Subscription subscription = new Subscription(UUID.randomUUID(), tenantId, skuCode, SubscriptionStatus.ACTIVE, now, null);
        market.saveSubscription(subscription);
        project(tenantId, skuCode, subscription.id(), now);
        List<AppInstance> installed = new ArrayList<>();
        for (PlannedApp app : plan.apps()) {
            AppInstance instance = installOne(tenantId, plan, app, subscription.id(), now, null);
            PermissionModule module = requirePermissions();
            rolePack(app.template()).ifPresent(pack -> module.rolePacks().apply(pack));
            if (!instance.reused()) {
                emitInstalled(instance, now);
            }
            installed.add(instance);
        }
        return List.copyOf(installed);
    }

    /** Platform-approved activation of a product-native SKU into the application's tenant. No outbox event. */
    @Transactional
    public List<AppInstance> installApproved(PlatformActivationCommand command) {
        command.assertActive();
        UUID tenantId = command.application().tenantId();
        String skuCode = command.application().skuCode();
        Plan plan = plan(skuCode);
        if (market.hasActiveSubscription(tenantId, skuCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "tenant already has an active subscription for this SKU");
        }
        Instant now = clock.instant();
        Subscription subscription = new Subscription(UUID.randomUUID(), tenantId, skuCode, SubscriptionStatus.ACTIVE, now, null);
        market.saveApprovedSubscription(command, subscription);
        project(tenantId, skuCode, subscription.id(), now);
        List<AppInstance> installed = new ArrayList<>();
        for (PlannedApp app : plan.apps()) {
            installed.add(installOne(tenantId, plan, app, subscription.id(), now, command));
            PermissionModule module = requirePermissions();
            rolePack(app.template()).ifPresent(pack -> module.rolePacks().applyApproved(command, pack));
        }
        return List.copyOf(installed);
    }

    /** The current tenant's instances (all statuses), newest change first. */
    @Transactional(readOnly = true)
    public List<AppInstance> current() {
        UUID tenant = boundTenant();
        return jdbc.sql(SELECT + " WHERE i.tenant_id = :tenant ORDER BY i.changed_at DESC, i.id")
                .param("tenant", tenant).query(this::map).list();
    }

    /** Newer published versions for the current tenant's INHERIT instances. Empty when nothing is ahead. */
    @Transactional(readOnly = true)
    public List<UpgradeOffer> upgradeOffers() {
        List<UpgradeOffer> offers = new ArrayList<>();
        for (AppInstance instance : current()) {
            UpgradeOffer offer = offerFor(instance);
            if (offer != null) offers.add(offer);
        }
        return List.copyOf(offers);
    }

    /**
     * Tenant self-service upgrade. Same transaction updates the pinned version, entries and revision,
     * lands added role-pack grants, and emits {@code p4.app_instance} with {@code previous_version} set.
     */
    @Transactional
    public UpgradeResult upgrade(UUID instanceId, UUID targetVersionId) {
        AppInstance current = requireInstalled(instanceId, true);
        if (!DEFAULT_MODE.equals(current.mode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "only inherit instances can be upgraded");
        }
        TemplateVersion target = requireTarget(current, targetVersionId);
        String previous = current.version();
        AppInstance next = writeUpgrade(current, target);
        List<AppliedRole> landed = land(target, false, current.tenantId());
        emit(next, previous, "upgraded", "p4.app_instance.upgraded", next.changedAt());
        return new UpgradeResult(next, landed);
    }

    /**
     * Uninstall keeps the row. Status becomes {@code UNINSTALLED}, mode is null, entries are empty, revision bumps.
     * The tenant path emits an event whose kind is {@code UNINSTALLED}. The subscription ends when it has no
     * installed instance left.
     */
    @Transactional
    public AppInstance uninstall(UUID instanceId) {
        AppInstance current = requireInstalled(instanceId, true);
        Instant now = clock.instant();
        int changed = jdbc.sql("""
                UPDATE market.app_instance
                SET status='UNINSTALLED', mode=NULL, entries='[]'::jsonb, revision=revision+1, changed_at=:now
                WHERE id=:id AND status='INSTALLED' AND revision=:revision
                """).param("now", Timestamp.from(now)).param("id", current.id()).param("revision", current.revision()).update();
        if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "instance changed before uninstall");
        AppInstance next = copy(current, current.appVersionId(), current.version(), "UNINSTALLED", null, List.of(),
                current.revision() + 1, now);
        emit(next, null, "uninstalled", "p4.app_instance.uninstalled", now);
        int remaining = jdbc.sql("""
                SELECT count(*) FROM market.app_instance WHERE subscription_id=:id AND status='INSTALLED'
                """).param("id", current.subscriptionId()).query(Integer.class).single();
        if (remaining == 0) {
            jdbc.sql("""
                    UPDATE market.subscription SET status='CANCELLED', ended_at=:now
                    WHERE id=:id AND status='ACTIVE'
                    """).param("now", Timestamp.from(now)).param("id", current.subscriptionId()).update();
        }
        return next;
    }

    /** Publish hook: INHERIT instances of products with the switch on follow this version when it is a same-major bump. No event. */
    @Transactional
    public void autoUpgradePublished(UUID versionId) {
        TemplateVersion target = templates.published(versionId).orElse(null);
        if (target == null) return;
        List<AppInstance> rows = jdbc.sql(SELECT + """
                 WHERE i.status='INSTALLED' AND i.mode='INHERIT' AND i.app_version_id<>:version
                   AND i.app_id=(SELECT app_id FROM market.app_version WHERE id=:version)
                   AND EXISTS (SELECT 1 FROM market.product p WHERE p.id=i.product_id AND p.auto_upgrade_minor)
                """).param("version", versionId).query(this::map).list();
        for (AppInstance instance : rows) {
            if (sameMajorNewer(instance.version(), target.version())) quietUpgrade(instance, target);
        }
    }

    /** After the product switch is turned on, move each INHERIT instance to its newest same-major published version. No event. */
    @Transactional
    public void catchUpProduct(UUID productId) {
        Boolean enabled = jdbc.sql("SELECT auto_upgrade_minor FROM market.product WHERE id=:id")
                .param("id", productId).query(Boolean.class).optional().orElse(false);
        if (!Boolean.TRUE.equals(enabled)) return;
        List<AppInstance> rows = jdbc.sql(SELECT + """
                 WHERE i.product_id=:product AND i.status='INSTALLED' AND i.mode='INHERIT'
                """).param("product", productId).query(this::map).list();
        for (AppInstance instance : rows) {
            TemplateVersion target = newestSameMajor(instance);
            if (target != null) quietUpgrade(instance, target);
        }
    }

    private void quietUpgrade(AppInstance current, TemplateVersion target) {
        writeUpgrade(current, target);
        land(target, true, current.tenantId());
    }

    private UpgradeOffer offerFor(AppInstance instance) {
        if (!"INSTALLED".equals(instance.status()) || !DEFAULT_MODE.equals(instance.mode())) return null;
        TemplateVersion best = null;
        for (TemplateVersion candidate : publishedOf(instance.appId())) {
            if (candidate.versionId().equals(instance.appVersionId())) continue;
            if (compareVersions(candidate.version(), instance.version()) <= 0) continue;
            if (best == null || compareVersions(candidate.version(), best.version()) > 0) best = candidate;
        }
        if (best == null) return null;
        return new UpgradeOffer(instance.id(), instance.appCode(), instance.version(), best.versionId(), best.version(),
                sameMajor(instance.version(), best.version()));
    }

    private TemplateVersion newestSameMajor(AppInstance instance) {
        TemplateVersion best = null;
        for (TemplateVersion candidate : publishedOf(instance.appId())) {
            if (!sameMajorNewer(instance.version(), candidate.version())) continue;
            if (best == null || compareVersions(candidate.version(), best.version()) > 0) best = candidate;
        }
        return best;
    }

    private List<TemplateVersion> publishedOf(UUID appId) {
        List<UUID> ids = jdbc.sql("""
                SELECT id FROM market.app_version WHERE app_id=:app AND status='PUBLISHED'
                """).param("app", appId).query(UUID.class).list();
        List<TemplateVersion> versions = new ArrayList<>();
        for (UUID id : ids) templates.published(id).ifPresent(versions::add);
        return versions;
    }

    private AppInstance requireInstalled(UUID instanceId, boolean owned) {
        AppInstance instance = jdbc.sql(SELECT + " WHERE i.id=:id").param("id", instanceId).query(this::map).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "app instance not found"));
        if (owned && !instance.tenantId().equals(boundTenant())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "app instance not found");
        }
        if (!"INSTALLED".equals(instance.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "instance is not installed");
        }
        return instance;
    }

    private TemplateVersion requireTarget(AppInstance current, UUID targetVersionId) {
        if (targetVersionId == null || targetVersionId.equals(current.appVersionId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "instance is already on this version");
        }
        TemplateVersion target = templates.published(targetVersionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "target version is not a published manifest"));
        if (!target.appCode().equals(current.appCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "target version belongs to another application");
        }
        return target;
    }

    private AppInstance writeUpgrade(AppInstance current, TemplateVersion target) {
        List<String> entries = entryKeys(target);
        Instant now = clock.instant();
        int changed = jdbc.sql("""
                UPDATE market.app_instance
                SET app_version_id=:version, entries=CAST(:entries AS jsonb), revision=revision+1, changed_at=:now
                WHERE id=:id AND status='INSTALLED' AND revision=:revision
                """).param("version", target.versionId()).param("entries", json.writeValueAsString(entries))
                .param("now", Timestamp.from(now)).param("id", current.id()).param("revision", current.revision()).update();
        if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "instance changed before upgrade");
        return copy(current, target.versionId(), target.version(), "INSTALLED", current.mode(), entries,
                current.revision() + 1, now);
    }

    private List<AppliedRole> land(TemplateVersion target, boolean platform, UUID tenantId) {
        Optional<RolePackCommand> pack = rolePack(target);
        if (pack.isEmpty()) return List.of();
        PermissionModule module = requirePermissions();
        if (platform) {
            return module.rolePacks().applyApproved(new PlatformInstanceWrite(tenantId), pack.get()).roles();
        }
        return module.rolePacks().apply(pack.get()).roles();
    }

    private static AppInstance copy(AppInstance current, UUID versionId, String version, String status, String mode,
            List<String> entries, long revision, Instant changedAt) {
        return new AppInstance(current.id(), current.tenantId(), current.productId(), current.productCode(),
                current.skuCode(), current.subscriptionId(), current.appId(), current.appCode(), versionId, version,
                status, mode, entries, revision, changedAt, false);
    }

    private static List<String> entryKeys(TemplateVersion template) {
        List<String> entries = new ArrayList<>();
        for (Map<String, Object> entry : template.entries()) {
            if (entry.get("key") instanceof String key && !key.isBlank()) entries.add(key);
        }
        return List.copyOf(entries);
    }

    /** Same major, and the target sorts after the current version. Unparseable versions are not automatic. */
    static boolean sameMajorNewer(String current, String target) {
        int[] left = parts(current);
        int[] right = parts(target);
        return left != null && right != null && left[0] == right[0] && compareParts(left, right) < 0;
    }

    private static boolean sameMajor(String current, String target) {
        int[] left = parts(current);
        int[] right = parts(target);
        return left != null && right != null && left[0] == right[0];
    }

    private static int compareVersions(String left, String right) {
        int[] a = parts(left);
        int[] b = parts(right);
        if (a == null || b == null) return String.valueOf(left).compareTo(String.valueOf(right));
        return compareParts(a, b);
    }

    private static int compareParts(int[] left, int[] right) {
        for (int i = 0; i < 3; i++) if (left[i] != right[i]) return Integer.compare(left[i], right[i]);
        return 0;
    }

    private static int[] parts(String version) {
        if (version == null) return null;
        String[] bits = version.split("\\.");
        if (bits.length != 3) return null;
        int[] values = new int[3];
        for (int i = 0; i < 3; i++) {
            try { values[i] = Integer.parseInt(bits[i]); }
            catch (NumberFormatException ex) { return null; }
        }
        return values;
    }

    private AppInstance installOne(UUID tenantId, Plan plan, PlannedApp app, UUID subscriptionId, Instant now,
            PlatformActivationCommand command) {
        Optional<AppInstance> existing = jdbc.sql(SELECT + " WHERE i.tenant_id = :tenant AND i.app_id = :app AND i.status = 'INSTALLED'")
                .param("tenant", tenantId).param("app", app.appId()).query(this::map).optional();
        if (existing.isPresent()) {
            AppInstance found = existing.get();
            return new AppInstance(found.id(), found.tenantId(), found.productId(), found.productCode(), found.skuCode(),
                    found.subscriptionId(), found.appId(), found.appCode(), found.appVersionId(), found.version(),
                    found.status(), found.mode(), found.entries(), found.revision(), found.changedAt(), true);
        }
        if (command != null) {
            command.assertTenant(tenantId);
        } else if (!boundTenant().equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "tenant scope mismatch");
        }
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO market.app_instance(id, tenant_id, product_id, sku_code, subscription_id, app_id, app_version_id,
                    status, mode, entries, revision, created_at, changed_at)
                VALUES(:id, :tenant, :product, :sku, :subscription, :app, :version, 'INSTALLED', :mode,
                    CAST(:entries AS jsonb), 1, :now, :now)
                """).param("id", id).param("tenant", tenantId).param("product", plan.productId())
                .param("sku", plan.skuCode()).param("subscription", subscriptionId).param("app", app.appId())
                .param("version", app.versionId()).param("mode", DEFAULT_MODE)
                .param("entries", json.writeValueAsString(app.entries())).param("now", Timestamp.from(now)).update();
        return new AppInstance(id, tenantId, plan.productId(), plan.productCode(), plan.skuCode(), subscriptionId,
                app.appId(), app.template().appCode(), app.versionId(), app.template().version(), "INSTALLED",
                DEFAULT_MODE, app.entries(), 1, now, false);
    }

    /** The manifest's role pack as a C3 command; empty when the manifest has none. */
    static Optional<RolePackCommand> rolePack(TemplateVersion template) {
        if (template.rolePack().isEmpty()) {
            return Optional.empty();
        }
        List<RoleSpec> roles = new ArrayList<>();
        for (Map<String, Object> role : template.rolePack()) {
            List<GrantSpec> grants = new ArrayList<>();
            if (role.get("grants") instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> grant) {
                        grants.add(new GrantSpec(text(grant.get("resource")), text(grant.get("action")), text(grant.get("scope"))));
                    }
                }
            }
            roles.add(new RoleSpec(text(role.get("code")), text(role.get("name")), grants));
        }
        return Optional.of(new RolePackCommand(template.appCode(), template.version(), roles));
    }

    /** C4 tenant-path event, in the payload shape of C's {@code InstanceEvents} parser. */
    private void emitInstalled(AppInstance instance, Instant now) {
        emit(instance, null, "installed", "p4.app_instance.installed", now);
    }

    private void emit(AppInstance instance, String previousVersion, String eventType, String outboxType, Instant now) {
        OutboxPort port = outbox.getIfAvailable();
        if (port == null) {
            return; // no outbox in this context; C still reconciles through AppInstanceQuery
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_id", UuidV7.generate().toString());
        payload.put("event_type", eventType);
        payload.put("instance_id", instance.id().toString());
        payload.put("instance_revision", instance.revision());
        payload.put("product_code", instance.productCode());
        payload.put("app_code", instance.appCode());
        payload.put("version", instance.version());
        payload.put("previous_version", previousVersion);
        payload.put("mode", instance.mode());
        payload.put("entries", instance.entries());
        payload.put("occurred_at", now.toString());
        port.append(new OutboxAppendCommand(DESTINATION, outboxType, 1, EVENT_JSON.writeValueAsString(payload),
                "app_instance", instance.id(), null, null));
    }

    private void project(UUID tenantId, String skuCode, UUID subscriptionId, Instant now) {
        Map<String, EntitlementPayload.EntitlementItem> items = new LinkedHashMap<>();
        market.sku(skuCode).orElseThrow().entitlements().forEach((key, spec) -> items.put(key,
                new EntitlementPayload.EntitlementItem(spec.enabled(), spec.quota(), spec.attributes())));
        var current = tenants.findProjection(tenantId);
        var projection = EntitlementProjection.fromSubscription(tenantId, skuCode, subscriptionId,
                new EntitlementPayload(EntitlementPayload.SCHEMA_V1, items), now,
                current.map(existing -> existing.version() + 1).orElse(1L));
        if (current.isPresent()) {
            tenants.updateProjection(projection);
        } else {
            tenants.insertProjection(projection);
        }
    }

    private PermissionModule requirePermissions() {
        PermissionModule module = permissions.getIfAvailable();
        if (module == null) {
            throw new IllegalStateException("permission module is required to land application role packs");
        }
        return module;
    }

    static UUID boundTenant() {
        AccessContext context = AccessContextHolder.currentOrNull();
        if (!(context instanceof TenantContext tenant)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TenantContext is required");
        }
        return UUID.fromString(tenant.tenantId());
    }

    private static final String SELECT = """
            SELECT i.*, p.code AS product_code, a.code AS app_code, v.version AS version_label
            FROM market.app_instance i
            JOIN market.product p ON p.id = i.product_id
            JOIN market.app_package a ON a.id = i.app_id
            JOIN market.app_version v ON v.id = i.app_version_id
            """;

    private AppInstance map(ResultSet row, int index) throws SQLException {
        List<String> entries = json.readValue(row.getString("entries"), new TypeReference<List<String>>() {});
        return new AppInstance(row.getObject("id", UUID.class), row.getObject("tenant_id", UUID.class),
                row.getObject("product_id", UUID.class), row.getString("product_code"), row.getString("sku_code"),
                row.getObject("subscription_id", UUID.class), row.getObject("app_id", UUID.class),
                row.getString("app_code"), row.getObject("app_version_id", UUID.class), row.getString("version_label"),
                row.getString("status"), row.getString("mode"), List.copyOf(entries), row.getLong("revision"),
                row.getTimestamp("changed_at").toInstant(), false);
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }
}
