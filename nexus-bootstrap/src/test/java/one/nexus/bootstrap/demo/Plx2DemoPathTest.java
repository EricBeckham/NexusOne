package one.nexus.bootstrap.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import one.nexus.bootstrap.NexusOneApplication;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.p1.infra.isolation.IsolationContextHolder;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.domain.Model;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p4.market.AppDesignService;
import one.nexus.p4.market.MarketIsolation;
import one.nexus.p4.market.MarketRepository;
import one.nexus.p4.market.MarketService;
import one.nexus.p4.market.ProductCatalogService;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import one.nexus.permission.effective.EffectivePermissionService;
import one.nexus.permission.pack.RolePackService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** PLX2: tenant, organization, then the published leave subscription, in that order. */
@SpringBootTest(classes = NexusOneApplication.class)
class Plx2DemoPathTest {
    private static EmbeddedPostgres postgres;
    @Autowired JdbcClient jdbc;
    @Autowired IdentityModule identity;
    @Autowired PermissionModule permissions;
    @Autowired TenantMemberGrantSeeder seeder;
    @Autowired AppDesignService design;
    @Autowired MarketService market;
    @Autowired ProductCatalogService products;
    @Autowired MarketRepository repository;
    @Autowired MarketIsolation isolation;

    @BeforeAll static void startPostgres() throws Exception { postgres = EmbeddedPostgres.builder().start(); }
    @AfterAll static void stopPostgres() throws Exception { if (postgres != null) postgres.close(); }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/postgres");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("nexus.execution.dispatcher-enabled", () -> "false");
        registry.add("nexus.execution.worker-enabled", () -> "false");
        registry.add("nexus.execution.quartz-auto-startup", () -> "false");
        registry.add("nexus.cache.mode", () -> "memory");
        registry.add("nexus.demo.enabled", () -> "false");
    }

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
        TenantContextHolder.clear();
        IsolationContextHolder.clear();
    }

    @Test
    void staysQuietWhenDemoIsOffAndRefusesARemoteDatabase() {
        Plx2DemoSeedConfiguration.run("false", "local", localUrl(), jdbc, identity, permissions, seeder,
                design, market, products, repository, isolation);
        assertThrows(IllegalStateException.class, () -> Plx2DemoSeedConfiguration.run(
                "true", "local", "jdbc:postgresql://db.example/nexusone",
                jdbc, identity, permissions, seeder, design, market, products, repository, isolation));
        assertTrue(Plx2DemoSeedOrder.PATH < Plx2DemoSeedOrder.PORTAL);
    }

    @Test
    void seedsOrgThenPublishesLeaveAndBindsPositionRoles() {
        runPath();
        runPath();

        UUID tenant = P1DemoSeedConfiguration.TENANT_ID;
        assertTrue(identity.repository().findDepartmentByCode(tenant, Pla8OrgDemoContent.DEPARTMENT).isPresent());
        assertTrue(repository.hasActiveSubscription(tenant, Plb9CatalogDemoContent.SKU));
        assertEquals(1, jdbc.sql("SELECT count(*) FROM market.app_instance WHERE tenant_id=:t AND status='INSTALLED'")
                .param("t", tenant).query(Integer.class).single());
        assertEquals(Plb9CatalogDemoContent.CATEGORY, market.catalogGroups().get(0).categoryCode());
        assertEquals(Plb9CatalogDemoContent.PRODUCT, market.catalogGroups().get(0).products().get(0).code());

        Model.Person admin = identity.repository().findPersonByEmployeeNo(tenant, Pla8OrgDemoContent.EMPLOYEE_ADMIN).orElseThrow();
        Model.Person member = identity.repository().findPersonByEmployeeNo(tenant, Pla8OrgDemoContent.EMPLOYEE_MEMBER).orElseThrow();
        Model.Person lead = identity.repository().findPersonByEmployeeNo(tenant, Pla8OrgDemoContent.EMPLOYEE_LEAD).orElseThrow();
        TenantContextHolder.set(new TenantContext(UUID.randomUUID(), tenant, admin.accountId(), admin.id(), "plx2",
                Instant.now(), Instant.now().plusSeconds(600)));
        EffectivePermissionService preview = new EffectivePermissionService(permissions, identity.repository());
        assertEquals(Set.of(TenantMemberGrantSeeder.ROLE_CODE, RolePackService.tenantRoleCode("leave", "leave_applicant")),
                roleCodes(preview, member.id()));
        assertEquals(Set.of(TenantMemberGrantSeeder.ROLE_CODE, RolePackService.tenantRoleCode("leave", "leave_approver")),
                roleCodes(preview, lead.id()));
    }

    private void runPath() {
        Plx2DemoSeedConfiguration.run("true", "local", localUrl(), jdbc, identity, permissions, seeder,
                design, market, products, repository, isolation);
    }

    private static String localUrl() {
        return "jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/postgres";
    }

    private static Set<String> roleCodes(EffectivePermissionService preview, UUID personId) {
        return preview.forPerson(personId).roles().stream()
                .map(EffectivePermissionService.RoleSource::roleCode).collect(Collectors.toSet());
    }
}
