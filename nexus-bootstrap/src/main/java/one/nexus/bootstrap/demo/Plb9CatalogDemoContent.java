package one.nexus.bootstrap.demo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.infra.isolation.IsolationContextHolder;
import one.nexus.p1.port.IsolationGuard.IsolationSnapshot;
import one.nexus.p1.port.PlatformContext;
import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p2.domain.Model;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p4.market.AppDesignService;
import one.nexus.p4.market.MarketIsolation;
import one.nexus.p4.market.MarketModels.AppPackage;
import one.nexus.p4.market.MarketModels.AppVersion;
import one.nexus.p4.market.MarketModels.EntitlementSpec;
import one.nexus.p4.market.MarketModels.VersionStatus;
import one.nexus.p4.market.MarketRepository;
import one.nexus.p4.market.MarketService;
import one.nexus.p4.market.ProductCatalogService;
import one.nexus.p4.market.ProductCatalogService.Product;
import one.nexus.p4.market.ProductCatalogService.ProductApp;
import one.nexus.p4.market.ProductCatalogService.ProductView;
import one.nexus.p4.market.ProductCatalogService.ProductWrite;
import one.nexus.p4.market.ProductCatalogService.SkuWrite;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.pack.RolePackService;
import one.nexus.permission.persist.PolicyRepository.RoleRecord;
import one.nexus.permission.position.PositionRoleService;

/**
 * PLB9. Demo catalog for the fixed P1 tenant: one category, the leave manifest, a published product SKU,
 * a tenant self-service subscription, then the landed role pack bound onto the PLA8 positions.
 * A later start finds the same codes and writes nothing new. The platform approval path installs the same SKU,
 * so this seed does not submit a second activation.
 */
public final class Plb9CatalogDemoContent {
    public static final String CATEGORY = "oa";
    public static final String APP = "leave";
    public static final String VERSION = "1.0.0";
    public static final String PRODUCT = "pl_leave";
    public static final String SKU = "pl_leave";
    public static final String ROLE_APPLICANT = "leave_applicant";
    public static final String ROLE_APPROVER = "leave_approver";
    /** Same codes PLA8 writes. Duplicated here so this card does not depend on that class. */
    public static final String POSITION_LEAD = "pl_lead";
    public static final String POSITION_MEMBER = "pl_member";
    public static final String EMPLOYEE_ADMIN = "E-PL-ADMIN";

    /**
     * docs/planning/plx1/leave-app-manifest.example.json, with the personnel node pointed at {@code pl_lead}
     * so the position reference matches the PLA8 demo org.
     */
    static final String MANIFEST = """
            {
              "schema": "nexus.app-manifest.v1",
              "app_code": "leave",
              "version": "1.0.0",
              "category_code": "oa",
              "name": "请假",
              "ledgers": [
                {
                  "code": "leave",
                  "name": "请假台账",
                  "fields": [
                    { "code": "title", "name": "请假类型", "type": "TEXT", "required": true, "indexed": true },
                    { "code": "days", "name": "天数", "type": "NUMERIC", "required": true, "indexed": false },
                    { "code": "start_at", "name": "开始时间", "type": "TIMESTAMPTZ", "required": true, "indexed": false },
                    { "code": "reason", "name": "事由", "type": "TEXT", "required": false, "indexed": false }
                  ]
                }
              ],
              "forms": [
                {
                  "code": "leave_apply",
                  "name": "请假申请",
                  "ledger_code": "leave",
                  "purpose": "FILL",
                  "layout": { "sections": [ { "title": "基本信息", "fields": ["title", "days", "start_at", "reason"] } ] },
                  "readonly_fields": []
                },
                {
                  "code": "leave_approve",
                  "name": "请假审批",
                  "ledger_code": "leave",
                  "purpose": "APPROVE",
                  "layout": { "sections": [ { "title": "申请内容", "fields": ["title", "days", "start_at", "reason"] } ] },
                  "readonly_fields": ["title", "days", "start_at", "reason"]
                },
                {
                  "code": "leave_view",
                  "name": "请假记录",
                  "ledger_code": "leave",
                  "purpose": "VIEW",
                  "layout": { "sections": [ { "title": "请假记录", "fields": ["title", "days", "start_at", "reason"] } ] },
                  "readonly_fields": ["title", "days", "start_at", "reason"]
                }
              ],
              "processes": [
                {
                  "key": "leave_approval",
                  "name": "请假审批",
                  "form_code": "leave_apply",
                  "nodes": [
                    { "id": "n1", "name": "部门审批", "form_code": "leave_approve", "assignee": { "type": "ROLE", "ref": "leave_approver" } },
                    { "id": "n2", "name": "人事备案", "form_code": "leave_approve", "assignee": { "type": "POSITION", "ref": "pl_lead" } }
                  ]
                }
              ],
              "role_pack": [
                {
                  "code": "leave_applicant",
                  "name": "请假申请人",
                  "grants": [
                    { "resource": "fn.leave.leave_apply", "action": "read", "scope": "SELF" },
                    { "resource": "fn.leave.leave_apply", "action": "write", "scope": "SELF" }
                  ]
                },
                {
                  "code": "leave_approver",
                  "name": "请假审批人",
                  "grants": [
                    { "resource": "fn.leave.leave_view", "action": "read", "scope": "DEPT_AND_CHILDREN" }
                  ]
                }
              ],
              "entries": [
                {
                  "key": "fn:leave:leave_apply",
                  "app_code": "leave",
                  "form_code": "leave_apply",
                  "ledger_code": "leave",
                  "process_key": "leave_approval",
                  "name": "请假申请",
                  "icon": "calendar",
                  "kind": "FILL",
                  "default_group": "办公",
                  "sort": 10
                },
                {
                  "key": "fn:leave:leave_view",
                  "app_code": "leave",
                  "form_code": "leave_view",
                  "ledger_code": "leave",
                  "process_key": null,
                  "name": "请假记录",
                  "icon": "list",
                  "kind": "LIST",
                  "default_group": "办公",
                  "sort": 20
                }
              ]
            }
            """;

    private Plb9CatalogDemoContent() {}

    public static void write(AppDesignService design, MarketService market, ProductCatalogService products,
            MarketRepository repository, MarketIsolation isolation, IdentityRepository identity,
            PermissionModule permissions, UUID tenantId) {
        IsolationSnapshot previousPlatform = IsolationContextHolder.get();
        TenantContext previousTenant = TenantContextHolder.get();
        AccessContext previousAccess = AccessContextHolder.currentOrNull();
        try {
            asPlatform(tenantId);
            AppVersion published = publishLeave(design, market, products, repository);
            asTenantAdmin(isolation, identity, tenantId);
            if (!repository.hasActiveSubscription(tenantId, SKU)) {
                market.subscribeProductSku(tenantId, SKU);
            }
            bindRoles(identity, permissions, tenantId, published);
        } finally {
            restore(previousPlatform, previousTenant, previousAccess);
        }
    }

    private static AppVersion publishLeave(AppDesignService design, MarketService market, ProductCatalogService products,
            MarketRepository repository) {
        if (design.categories().stream().noneMatch(category -> CATEGORY.equals(category.code()))) {
            design.createCategory(CATEGORY, new AppDesignService.CategoryWrite("办公", "PLB9 演示分类", 10, null));
        }
        AppPackage app = repository.apps().stream().filter(item -> APP.equals(item.code())).findFirst()
                .orElseGet(() -> market.createApp(APP, "请假", "PLB9 演示请假应用"));
        design.setAppCategory(app.id(), CATEGORY);
        AppVersion version = repository.versions(app.id()).stream().filter(item -> VERSION.equals(item.version()))
                .findFirst().orElseGet(() -> market.createVersion(app.id(), VERSION, "PLB9 演示"));
        version = publishVersion(design, market, version);
        Product product = products.list().stream().filter(item -> PRODUCT.equals(item.code())).findFirst().orElse(null);
        if (product == null) {
            product = products.create(new ProductWrite(PRODUCT, "请假", "PLB9 演示产品", null)).product();
        }
        if ("DRAFT".equals(product.status())) {
            ProductView view = products.get(product.id());
            if (view.apps().isEmpty()) {
                view = products.replaceApps(product.id(), view.product().version(),
                        List.of(new ProductApp(app.id(), version.id(), 0)));
            }
            products.publish(view.product().id(), view.product().version());
        }
        if (repository.sku(SKU).isEmpty()) {
            products.createSku(product.id(), new SkuWrite(SKU, "请假",
                    Map.of("apps", new EntitlementSpec(true, 5, Map.of()))));
        }
        return version;
    }

    private static AppVersion publishVersion(AppDesignService design, MarketService market, AppVersion version) {
        if (version.status() == VersionStatus.DRAFT) {
            Map<String, Object> manifest;
            try {
                manifest = new ObjectMapper().readValue(MANIFEST, new TypeReference<Map<String, Object>>() {});
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw new IllegalStateException("请假清单无法解析", ex);
            }
            AppDesignService.Validation validation = design.validate(version.id(), manifest);
            if (!validation.problems().isEmpty()) {
                throw new IllegalStateException("请假清单校验未通过: " + validation.problems());
            }
            design.saveManifest(version.id(), manifest);
            version = market.transition(version.id(), VersionStatus.IN_REVIEW);
        }
        if (version.status() == VersionStatus.IN_REVIEW) {
            version = market.transition(version.id(), VersionStatus.APPROVED);
        }
        if (version.status() == VersionStatus.APPROVED) {
            version = market.transition(version.id(), VersionStatus.PUBLISHED);
        }
        if (version.status() != VersionStatus.PUBLISHED) {
            throw new IllegalStateException("请假版本未能发布: " + version.status());
        }
        return version;
    }

    private static void bindRoles(IdentityRepository identity, PermissionModule permissions, UUID tenantId,
            AppVersion published) {
        Model.Position lead = identity.findPositionByCode(tenantId, POSITION_LEAD)
                .orElseThrow(() -> new IllegalStateException("PLB9 需要 PLA8 的岗位 " + POSITION_LEAD));
        Model.Position member = identity.findPositionByCode(tenantId, POSITION_MEMBER)
                .orElseThrow(() -> new IllegalStateException("PLB9 需要 PLA8 的岗位 " + POSITION_MEMBER));
        RoleRecord applicant = requireRole(permissions, tenantId, RolePackService.tenantRoleCode(APP, ROLE_APPLICANT));
        RoleRecord approver = requireRole(permissions, tenantId, RolePackService.tenantRoleCode(APP, ROLE_APPROVER));
        PositionRoleService bindings = new PositionRoleService(permissions, identity);
        keepAndAdd(bindings, member.id(), applicant.id());
        keepAndAdd(bindings, lead.id(), approver.id());
        if (published.status() != VersionStatus.PUBLISHED) {
            throw new IllegalStateException("请假版本未能发布");
        }
    }

    private static void keepAndAdd(PositionRoleService bindings, UUID positionId, UUID roleId) {
        List<UUID> roleIds = new ArrayList<>();
        bindings.rolesOf(positionId).forEach(role -> roleIds.add(role.roleId()));
        if (!roleIds.contains(roleId)) {
            roleIds.add(roleId);
        }
        bindings.set(positionId, roleIds);
    }

    private static RoleRecord requireRole(PermissionModule permissions, UUID tenantId, String code) {
        return permissions.repository().findRoleByCode(tenantId, code)
                .orElseThrow(() -> new IllegalStateException("订购没有落地角色 " + code));
    }

    private static void asPlatform(UUID tenantId) {
        var actor = new one.nexus.p1.domain.Actor(P1DemoSeedConfiguration.PLATFORM_ID, tenantId, "PLB9",
                Set.of(PlatformDuty.SYSTEM_ADMIN));
        IsolationContextHolder.set(new IsolationSnapshot(actor,
                new PlatformContext(true, "PLB9 demo catalog", actor, "plb9-demo", tenantId), null, "plb9-demo"));
    }

    private static void asTenantAdmin(MarketIsolation isolation, IdentityRepository identity, UUID tenantId) {
        Model.Person admin = identity.findPersonByEmployeeNo(tenantId, EMPLOYEE_ADMIN)
                .orElseThrow(() -> new IllegalStateException("PLB9 需要 PLA8 的演示管理员 " + EMPLOYEE_ADMIN));
        String tenant = tenantId.toString();
        AccessContextHolder.set(isolation.kernel().tenants().issue(new AuthenticatedMembership(
                one.nexus.isolation.api.Actor.person(admin.id().toString()), tenant, tenant, Set.of(tenant)),
                "plb9-demo"));
        Instant now = Instant.now();
        TenantContextHolder.set(new TenantContext(UUID.randomUUID(), tenantId, admin.accountId(), admin.id(),
                "plb9-demo", now, now.plusSeconds(600)));
    }

    private static void restore(IsolationSnapshot platform, TenantContext tenant, AccessContext access) {
        if (platform == null) {
            IsolationContextHolder.clear();
        } else {
            IsolationContextHolder.set(platform);
        }
        if (tenant == null) {
            TenantContextHolder.clear();
        } else {
            TenantContextHolder.set(tenant);
        }
        if (access == null) {
            AccessContextHolder.clear();
        } else {
            AccessContextHolder.set(access);
        }
    }
}
