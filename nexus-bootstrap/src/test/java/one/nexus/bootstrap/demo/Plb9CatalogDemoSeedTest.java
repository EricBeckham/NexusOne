package one.nexus.bootstrap.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.Instant;
import java.time.LocalDate;
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
import one.nexus.p2.org.OrganizationService;
import one.nexus.p4.market.AppDesignService;
import one.nexus.p4.market.MarketIsolation;
import one.nexus.p4.market.MarketRepository;
import one.nexus.p4.market.MarketService;
import one.nexus.p4.market.ProductCatalogService;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import one.nexus.permission.pack.RolePackService;
import one.nexus.permission.persist.PolicyRepository.RoleRecord;
import one.nexus.permission.position.PositionRoleService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** PLB9 catalog seed against disposable loopback PostgreSQL. The org stands in for PLA8, which is a separate card. */
@SpringBootTest(classes = NexusOneApplication.class)
class Plb9CatalogDemoSeedTest {
    private static EmbeddedPostgres postgres;
    @Autowired JdbcClient jdbc;
    @Autowired AppDesignService design;
    @Autowired MarketService market;
    @Autowired ProductCatalogService products;
    @Autowired MarketRepository repository;
    @Autowired MarketIsolation isolation;
    @Autowired IdentityModule identity;
    @Autowired PermissionModule permissions;
    @Autowired TenantMemberGrantSeeder seeder;

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
        Plb9CatalogDemoSeedConfiguration.run("false", "local", "jdbc:postgresql://127.0.0.1:5432/nexusone",
                null, null, null, null, null, null, null, null);
        assertThrows(IllegalStateException.class, () -> Plb9CatalogDemoSeedConfiguration.run(
                "true", "local", "jdbc:postgresql://db.example/nexusone",
                null, null, null, null, null, null, null, null));
    }

    @Test
    void publishesLeaveSubscribesTheDemoTenantAndKeepsExistingPositionRoles() {
        P1DemoSeedConfiguration.seedTenant(jdbc);
        office();
        write();
        write();

        UUID tenant = P1DemoSeedConfiguration.TENANT_ID;
        var sku = repository.sku(Plb9CatalogDemoContent.SKU).orElseThrow();
        assertNull(sku.appId());
        assertTrue(repository.hasActiveSubscription(tenant, Plb9CatalogDemoContent.SKU));
        assertEquals(1, jdbc.sql("SELECT count(*) FROM market.app_instance WHERE tenant_id=:t AND status='INSTALLED'")
                .param("t", tenant).query(Integer.class).single());
        assertEquals(1, jdbc.sql("""
                SELECT count(*) FROM market.subscription WHERE tenant_id=:t AND sku_code=:sku AND status='ACTIVE'
                """).param("t", tenant).param("sku", Plb9CatalogDemoContent.SKU).query(Integer.class).single());

        asAdmin(tenant);
        PositionRoleService bindings = new PositionRoleService(permissions, identity.repository());
        UUID lead = identity.repository().findPositionByCode(tenant, Plb9CatalogDemoContent.POSITION_LEAD).orElseThrow().id();
        UUID member = identity.repository().findPositionByCode(tenant, Plb9CatalogDemoContent.POSITION_MEMBER).orElseThrow().id();
        assertEquals(Set.of("tenant_member", RolePackService.tenantRoleCode("leave", "leave_approver")),
                bindings.rolesOf(lead).stream().map(PositionRoleService.BoundRole::roleCode).collect(Collectors.toSet()));
        assertEquals(Set.of("tenant_member", RolePackService.tenantRoleCode("leave", "leave_applicant")),
                bindings.rolesOf(member).stream().map(PositionRoleService.BoundRole::roleCode).collect(Collectors.toSet()));
    }

    private void write() {
        Plb9CatalogDemoContent.write(design, market, products, repository, isolation, identity.repository(),
                permissions, P1DemoSeedConfiguration.TENANT_ID);
    }

    /** The positions and people PLA8 will have written by the time this seed runs. */
    private void office() {
        UUID tenant = P1DemoSeedConfiguration.TENANT_ID;
        UUID account = UUID.fromString("01900000-0000-7000-8000-000000000a01");
        TenantContextHolder.set(context(tenant, account, null));
        OrganizationService org = identity.organization();
        var people = identity.repository();
        Model.Person admin = org.createPerson(new OrganizationService.PersonCommand(
                "陈管理", Plb9CatalogDemoContent.EMPLOYEE_ADMIN, "pl-admin@demo.invalid", null,
                LocalDate.of(2026, 1, 1), account));
        Model.Person lead = org.createPerson(new OrganizationService.PersonCommand(
                "周审批", "E-PL-LEAD", "pl-lead@demo.invalid", null, LocalDate.of(2026, 1, 1),
                UUID.fromString("01900000-0000-7000-8000-000000000a02")));
        Model.Person member = org.createPerson(new OrganizationService.PersonCommand(
                "林办理", "E-PL-MEMBER", "pl-member@demo.invalid", null, LocalDate.of(2026, 1, 1),
                UUID.fromString("01900000-0000-7000-8000-000000000a03")));
        Model.Department department = org.createDepartment(new OrganizationService.DepartmentCommand(
                "pl_office", "办公", null, 0, lead.id()));
        Model.Position adminPosition = org.createPosition(new OrganizationService.PositionCommand(
                "tenant_admin", "租户管理员", "GENERAL", "管理"));
        Model.Position leadPosition = org.createPosition(new OrganizationService.PositionCommand(
                Plb9CatalogDemoContent.POSITION_LEAD, "部门负责人", "GENERAL", "审批"));
        Model.Position memberPosition = org.createPosition(new OrganizationService.PositionCommand(
                Plb9CatalogDemoContent.POSITION_MEMBER, "成员", "GENERAL", "办理"));
        LocalDate hired = LocalDate.of(2026, 1, 1);
        org.assign(new OrganizationService.EmploymentCommand(
                admin.id(), department.id(), adminPosition.id(), Model.EmploymentKind.PRIMARY, hired, null));
        org.assign(new OrganizationService.EmploymentCommand(
                lead.id(), department.id(), leadPosition.id(), Model.EmploymentKind.PRIMARY, hired, null));
        org.assign(new OrganizationService.EmploymentCommand(
                member.id(), department.id(), memberPosition.id(), Model.EmploymentKind.PRIMARY, hired, null));
        TenantContextHolder.set(context(tenant, account, admin.id()));
        seeder.ensure();
        RoleRecord memberRole = permissions.repository().findRoleByCode(tenant, TenantMemberGrantSeeder.ROLE_CODE).orElseThrow();
        RoleRecord designer = permissions.repository().findRoleByCode(tenant, TenantMemberGrantSeeder.DESIGNER_ROLE).orElseThrow();
        PositionRoleService bindings = new PositionRoleService(permissions, people);
        bindings.set(memberPosition.id(), java.util.List.of(memberRole.id()));
        bindings.set(leadPosition.id(), java.util.List.of(memberRole.id()));
        bindings.set(adminPosition.id(), java.util.List.of(memberRole.id(), designer.id()));
        TenantContextHolder.clear();
    }

    private void asAdmin(UUID tenant) {
        Model.Person admin = identity.repository().findPersonByEmployeeNo(tenant, Plb9CatalogDemoContent.EMPLOYEE_ADMIN).orElseThrow();
        TenantContextHolder.set(context(tenant, admin.accountId(), admin.id()));
    }

    private static TenantContext context(UUID tenantId, UUID accountId, UUID personId) {
        Instant now = Instant.now();
        return new TenantContext(UUID.randomUUID(), tenantId, accountId, personId, "plb9-test", now, now.plusSeconds(600));
    }
}
