package one.nexus.bootstrap.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import one.nexus.p1.config.P1Properties;
import one.nexus.p2.domain.Model;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.persist.InMemoryIdentityRepository;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.PlatformDutyCatalog;
import one.nexus.permission.engine.PermissionEngine;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import one.nexus.permission.surface.SurfaceCatalog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionTemplate;

class PlatformDutyBootstrapTest {
    private static final UUID PLATFORM = UUID.fromString("01900000-0000-7000-8000-000000000001");

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void closedWindowInstallsNothing() throws Exception {
        InMemoryIdentityRepository identity = identity();
        PermissionModule permissions = module(identity);
        var env = new MockEnvironment();
        var jdbc = org.mockito.Mockito.mock(JdbcClient.class);
        var transaction = org.mockito.Mockito.mock(TransactionTemplate.class);
        var runner = new PlatformDutyBootstrapRunner(env, permissions, new P1Properties(), jdbc, transaction);
        runner.run(new DefaultApplicationArguments());
        assertTrue(permissions.repository().listRoles(PLATFORM).isEmpty());
        org.mockito.Mockito.verifyNoInteractions(jdbc, transaction);
    }

    @Test
    void refusesDemoContextWithoutInstalling() {
        InMemoryIdentityRepository identity = identity();
        PermissionModule permissions = module(identity);
        var env = new MockEnvironment()
                .withProperty(PlatformDutyBootstrap.ENABLED, "true")
                .withProperty(PlatformDutyBootstrap.DEMO_CONTEXT, "true");
        assertThrows(IllegalStateException.class,
                () -> new PlatformDutyBootstrapRunner(env, permissions, new P1Properties(),
                        org.mockito.Mockito.mock(JdbcClient.class),
                        org.mockito.Mockito.mock(TransactionTemplate.class)));
        assertTrue(permissions.repository().listRoles(PLATFORM).isEmpty());
        assertTrue(permissions.repository().listAssignments(PLATFORM).isEmpty());
    }

    @Test
    void installsDutyCatalogAndAssignsPlatformOpsOnce() {
        InMemoryIdentityRepository identity = identity();
        PermissionModule permissions = module(identity);
        PlatformDutyBootstrap.Result first = PlatformDutyBootstrap.install(permissions, PLATFORM);
        PlatformDutyBootstrap.Result second = PlatformDutyBootstrap.install(permissions, PLATFORM);

        assertTrue(first.catalogInstalled());
        assertTrue(first.systemAdminAssigned());
        assertEquals(1, first.assignmentCount());
        assertEquals(1, second.assignmentCount());
        assertEquals(PlatformDutyCatalog.DUTY_ROLES.size(), permissions.repository().listRoles(PLATFORM).size());

        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), PLATFORM, null, PlatformDutyBootstrap.PLATFORM_OPS_PERSON_ID,
                "platform-ops", Instant.now(), Instant.now().plusSeconds(60)));
        var decision = permissions.service().check("tenant", "list");
        assertTrue(decision.allowed());
        assertFalse("no_grant".equals(decision.reason()));
        var duties = PlatformDutyCatalog.heldDuties(PermissionEngine.expandedRoleCodes(
                permissions.service().currentSubject(), permissions.service().snapshot()));
        assertEquals(Set.of(PlatformDutyCatalog.SYSTEM_ADMIN), duties);

        var export = permissions.service().decide(SurfaceCatalog.EXPORT_RESOURCE, SurfaceCatalog.EXPORT_ACTION);
        assertFalse(export.allowed());
        assertEquals("no_grant", export.reason());
    }

    private static PermissionModule module(InMemoryIdentityRepository identity) {
        return PermissionModule.create(new InMemoryPolicyRepository(), identity, new InMemoryPolicyInvalidation());
    }

    private static InMemoryIdentityRepository identity() {
        InMemoryIdentityRepository identity = new InMemoryIdentityRepository();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        identity.savePerson(new Model.Person(
                PlatformDutyBootstrap.PLATFORM_OPS_PERSON_ID,
                PLATFORM,
                UUID.fromString("01900000-0000-7000-8000-000000000221"),
                "平台运营",
                "DEMO-PLATFORM",
                "platform.admin@demo.invalid",
                null,
                LocalDate.parse("2024-01-01"),
                Model.PersonStatus.ACTIVE,
                now,
                now,
                null,
                0));
        return identity;
    }
}
