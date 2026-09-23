package one.nexus.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.persist.InMemoryIdentityRepository;
import one.nexus.permission.engine.DataScopeKind;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import one.nexus.permission.scope.PermissionQueryBinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** T2-C thin exit: tenant principal -> role -> SELF grant -> snapshot -> protected query. */
class T2CThinPermissionFlowTest {
    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    void currentTenantCatalogAndSelfScopeReachProtectedQuery() {
        var identity = new InMemoryIdentityRepository();
        var policies = new InMemoryPolicyRepository();
        var invalidation = new InMemoryPolicyInvalidation();
        var permission = PermissionModule.create(policies, identity, invalidation);
        var idp = new StubIdentityProvider();
        var iam = new IdentityModule(
                identity,
                idp,
                new one.nexus.p2.smtp.KeycloakSmtpHook(idp, false),
                java.time.Clock.systemUTC(),
                Duration.ofHours(1));

        UUID tenant = UUID.fromString("2c2c2c2c-2c2c-4c2c-8c2c-2c2c2c2c2c2c");
        UUID otherTenant = UUID.fromString("2d2d2d2d-2d2d-4d2d-8d2d-2d2d2d2d2d2d");
        iam.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenant, "T2-C Admin", "t2c@example.com", "t2c-admin"));
        iam.adminInitializer().initialize(new TenantAdminInitializer.Command(
                otherTenant, "Other Admin", "other-t2c@example.com", "other-t2c-admin"));
        var person = identity.findPersonByAccount(
                tenant, identity.findAccountByEmail("t2c@example.com").orElseThrow().id()).orElseThrow();
        bind(tenant, person.accountId(), person.id());

        var catalog = permission.principals().view();
        assertEquals(List.of(person.id().toString()), catalog.users().stream().map(item -> item.id()).toList());
        assertTrue(catalog.users().stream().noneMatch(item -> item.label().equals("Other Admin")));

        long before = policies.currentVersion(tenant);
        var role = permission.admin().createRole("t2c_self_reader", "T2-C self reader", false);
        permission.admin().assign("user", person.id().toString(), role.id());
        permission.admin().grant(
                "GRANT", "role", role.code(), "ledger", "read", DataScopeKind.SELF, List.of(), Map.of());

        var decision = permission.service().decide("ledger", "read");
        assertTrue(decision.allowed());
        assertTrue(decision.policyVersion() > before);
        assertTrue(invalidation.wasNotified(tenant.toString()));

        var query = PermissionQueryBinder.bind(tenant.toString(), decision);
        assertEquals("tenant_id = ? AND (owner_id = ?)", query.whereSql());
        assertEquals(List.of(tenant.toString(), person.id().toString()), query.parameters());
    }

    private static void bind(UUID tenantId, UUID accountId, UUID personId) {
        Instant now = Instant.now();
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenantId, accountId, personId, "t2c", now, now.plus(Duration.ofHours(1))));
    }
}
