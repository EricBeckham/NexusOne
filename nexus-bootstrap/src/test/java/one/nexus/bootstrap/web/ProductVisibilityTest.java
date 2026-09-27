package one.nexus.bootstrap.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import one.nexus.p1.config.P1Properties;
import one.nexus.p1.domain.EntitlementPayload;
import one.nexus.p1.domain.EntitlementProjection;
import one.nexus.p1.port.TenantRepository;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.persist.InMemoryIdentityRepository;
import one.nexus.p2.smtp.KeycloakSmtpHook;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.PlatformDutyCatalog;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ProductVisibilityTest {
    static ProductVisibility.Facts platform(String... duties) {
        return new ProductVisibility.Facts(true, true, Set.of(duties), false, false);
    }

    static ProductVisibility.Facts business(boolean admin, boolean connect) {
        return new ProductVisibility.Facts(true, false, Set.of(), admin, connect);
    }

    // ---- PV1 §4 matrix -------------------------------------------------------------------------------------

    @Test
    void platformDutiesMapToPlatformProductsOnly() {
        assertEquals(List.of("p1", "p2", "p4"), ProductVisibility.visible(platform(PlatformDutyCatalog.SYSTEM_ADMIN)));
        assertEquals(List.of("p2"), ProductVisibility.visible(platform(PlatformDutyCatalog.SECURITY_ADMIN)));
        assertEquals(List.of("p1"), ProductVisibility.visible(platform(PlatformDutyCatalog.AUDIT_ADMIN)));
        assertEquals(List.of(), ProductVisibility.visible(platform()));
        // tenant_member (seeded on everyone) or tenant roles never open products in the platform tenant
        assertEquals(List.of(), ProductVisibility.visible(platform("tenant_member")));
    }

    @Test
    void businessTenantAdminGetsTenantProductsAndP5OnlyWhenEntitled() {
        assertEquals(List.of("p2", "p3", "p4"), ProductVisibility.visible(business(true, false)));
        assertEquals(List.of("p2", "p3", "p4", "p5"), ProductVisibility.visible(business(true, true)));
    }

    @Test
    void businessTenantMemberGetsTheWorkbenchOnly() {
        assertEquals(List.of("p3"), ProductVisibility.visible(business(false, false)));
        assertEquals(List.of("p3"), ProductVisibility.visible(business(false, true)));
    }

    @Test
    void noContextNoProducts() {
        assertEquals(List.of(), ProductVisibility.visible(new ProductVisibility.Facts(false, false, Set.of(), true, true)));
    }

    @Test
    void adminPositionCodesMatchTheExistingTest() {
        assertTrue(ProductVisibility.isTenantAdminPosition("TENANT_ADMIN"));
        assertTrue(ProductVisibility.isTenantAdminPosition("demo_tenant_admin"));
        assertFalse(ProductVisibility.isTenantAdminPosition("demo_member"));
        assertFalse(ProductVisibility.isTenantAdminPosition("demo_platform_ops"));
        assertFalse(ProductVisibility.isTenantAdminPosition(null));
    }

    // ---- controller facts against the real IAM session and permission engine ---------------------------------

    static final UUID ACME = UUID.fromString("01900000-0000-7000-8000-00000000c101");
    final P1Properties properties = new P1Properties();
    IdentityModule iam;
    PermissionModule permissions;
    TenantRepository tenants;
    ProductVisibilityController controller;

    @BeforeEach
    void setUp() {
        StubIdentityProvider idp = new StubIdentityProvider();
        iam = new IdentityModule(new InMemoryIdentityRepository(), idp, new KeycloakSmtpHook(idp, true),
                Clock.systemUTC(), Duration.ofMinutes(10));
        permissions = PermissionModule.create(new InMemoryPolicyRepository(), iam.repository(),
                new InMemoryPolicyInvalidation());
        tenants = mock(TenantRepository.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<TenantRepository> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(tenants);
        controller = new ProductVisibilityController(iam, permissions, properties, provider);
    }

    @AfterEach
    void clean() {
        TenantContextHolder.clear();
    }

    TenantContext signIn(UUID tenant, String sub) {
        iam.adminInitializer().initialize(new TenantAdminInitializer.Command(tenant, "Admin " + sub, sub + "@example.test", sub));
        String token = StubIdentityProvider.token(sub, sub + "@example.test", sub, "Admin " + sub);
        var view = iam.sessions().loginWithAccessToken(token);
        TenantContextHolder.clear();
        return iam.sessions().loadContext(view.contextId(), iam.sessions().authenticate(token));
    }

    @SuppressWarnings("unchecked")
    List<String> products() {
        return (List<String>) controller.products().get("products");
    }

    @Test
    void platformOperatorWithSystemAdminSeesP1P2P4() {
        UUID platform = properties.getPlatformTenant().getId();
        TenantContext context = signIn(platform, "ops");
        assertEquals(List.of(), products(), "no duty yet → nothing");
        permissions.duties().install();
        var system = permissions.repository().findRoleByCode(platform, PlatformDutyCatalog.SYSTEM_ADMIN).orElseThrow();
        permissions.admin().assign("user", context.personId().toString(), system.id());
        assertEquals(List.of("p1", "p2", "p4"), products());
    }

    @Test
    void initializedTenantAdminSeesTenantProductsAndP5FollowsTheEntitlement() {
        signIn(ACME, "lin");
        when(tenants.findProjection(ACME)).thenReturn(Optional.empty());
        assertEquals(List.of("p2", "p3", "p4"), products());

        var connect = new EntitlementPayload(EntitlementPayload.SCHEMA_V1,
                Map.of("connect", new EntitlementPayload.EntitlementItem(true, null, Map.of())));
        when(tenants.findProjection(ACME)).thenReturn(Optional.of(
                EntitlementProjection.fromDefaultPack(ACME, "pack", "1", connect, Instant.now())));
        assertEquals(List.of("p2", "p3", "p4", "p5"), products());

        when(tenants.findProjection(ACME)).thenThrow(new IllegalStateException("projection store down"));
        assertEquals(List.of("p2", "p3", "p4"), products(), "an unreadable entitlement never opens P5");
    }

    @Test
    void withoutAnIssuedContextThereAreNoProducts() {
        TenantContextHolder.clear();
        assertEquals(List.of(), products());
    }
}
