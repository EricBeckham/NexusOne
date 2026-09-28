package one.nexus.permission.personal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
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
import one.nexus.p2.org.OrganizationService;
import one.nexus.p2.persist.InMemoryIdentityRepository;
import one.nexus.p2.smtp.KeycloakSmtpHook;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.MemberAccessMode;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import one.nexus.permission.engine.DataScopeKind;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.persist.PolicyRepository;
import one.nexus.permission.personal.ExceptionGrantService.ExceptionItem;
import one.nexus.permission.personal.ExceptionGrantService.Filter;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExceptionGrantServiceTest {
    final UUID tenant = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0a04");
    IdentityModule iam;
    PermissionModule permissions;
    PolicyRepository repository;
    ExceptionGrantService exceptions;
    TenantContext adminContext;
    UUID alice;
    UUID bob;

    @BeforeEach
    void setUp() {
        StubIdentityProvider idp = new StubIdentityProvider();
        iam = new IdentityModule(new InMemoryIdentityRepository(), idp, new KeycloakSmtpHook(idp, false),
                Clock.systemUTC(), Duration.ofHours(1));
        var admin = iam.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenant, "Admin", "pla4-admin@example.com", "pla4-admin"));
        adminContext = context(admin.personId());
        TenantContextHolder.set(adminContext);
        repository = new InMemoryPolicyRepository();
        permissions = PermissionModule.create(repository, iam.repository(), new InMemoryPolicyInvalidation());
        exceptions = new ExceptionGrantService(permissions, iam.repository());
        alice = person("Alice", "A-4");
        bob = person("Bob", "B-4");

        // Exceptions an admin made.
        grant("GRANT", "user", alice, "report", "read");
        grant("DENY", "user", bob, "ledger", "write");
        UUID auditor = permissions.admin().createRole("temp_auditor", "临时审计", false).id();
        permissions.admin().assign("user", alice.toString(), auditor);
        // Not exceptions: a role grant and a position binding.
        permissions.admin().grant("GRANT", "role", "temp_auditor", "audit", "read", DataScopeKind.ALL, List.of(), Map.of());
        // System-made personal assignment: the legacy member seed for Bob.
        TenantContextHolder.set(context(bob));
        new TenantMemberGrantSeeder(permissions, iam.repository(), MemberAccessMode.LEGACY).ensure();
        TenantContextHolder.set(adminContext);
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    UUID person(String name, String no) {
        return iam.organization().createPerson(new OrganizationService.PersonCommand(
                name, no, no.toLowerCase() + "@example.com", null, null, UUID.randomUUID())).id();
    }

    TenantContext context(UUID person) {
        return new TenantContext(UUID.randomUUID(), tenant, null, person, "sub", Instant.now(), Instant.now().plusSeconds(600));
    }

    void grant(String effect, String type, UUID person, String resource, String action) {
        permissions.admin().grant(effect, type, person.toString(), resource, action, DataScopeKind.SELF, List.of(), Map.of());
    }

    List<ExceptionItem> list(UUID person, String kind, String q, boolean system) {
        return exceptions.list(new Filter(person, kind, q, system));
    }

    @Test
    void listsOnlyPersonalGrantsAndRolesWithNames() {
        List<ExceptionItem> all = list(null, null, null, false);

        assertEquals(3, all.size(), "two personal grants and one personal role; role grants and seeds are not exceptions");
        ExceptionItem role = all.stream().filter(i -> i.kind().equals("ROLE")).findFirst().orElseThrow();
        assertEquals("Alice", role.personName());
        assertEquals("temp_auditor", role.roleCode());
        assertEquals("临时审计", role.roleName());
        ExceptionItem deny = all.stream().filter(i -> "DENY".equals(i.effect())).findFirst().orElseThrow();
        assertEquals("Bob", deny.personName());
        assertEquals("SELF", deny.scope());
    }

    @Test
    void filtersByPersonKindAndText() {
        assertEquals(2, list(alice, null, null, false).size());
        assertEquals(1, list(null, "ROLE", null, false).size());
        assertEquals(2, list(null, "grant", null, false).size(), "kind is case-insensitive");
        assertEquals(1, list(null, null, "REPORT", false).size());
        assertEquals(1, list(null, null, "临时", false).size(), "matches role names");
        assertEquals(1, list(null, null, "bob", false).size(), "matches person names");
    }

    @Test
    void systemSeedsAreHiddenByDefaultAndCannotBeRevokedHere() {
        List<ExceptionItem> withSystem = list(bob, "ROLE", null, true);
        assertEquals(1, withSystem.size());
        ExceptionItem seed = withSystem.getFirst();
        assertTrue(seed.system());
        assertEquals(TenantMemberGrantSeeder.ROLE_CODE, seed.roleCode());
        assertTrue(list(bob, "ROLE", null, false).isEmpty());
        assertThrows(PermissionException.class, () -> exceptions.revokeAssignment(seed.id()));
    }

    @Test
    void revokingRemovesTheExceptionAndItsEffect() {
        ExceptionItem report = list(alice, "GRANT", null, false).getFirst();
        ExceptionItem role = list(alice, "ROLE", null, false).getFirst();
        assertTrue(allowed(alice, "report", "read"));
        assertTrue(allowed(alice, "audit", "read"));

        assertTrue(exceptions.revokeGrant(report.id()));
        assertTrue(exceptions.revokeAssignment(role.id()));

        assertTrue(list(alice, null, null, false).isEmpty());
        assertFalse(allowed(alice, "report", "read"));
        assertFalse(allowed(alice, "audit", "read"));
        assertFalse(exceptions.revokeGrant(report.id()), "already gone");
    }

    @Test
    void nonPersonalRulesAreRefusedAndAContextIsRequired() {
        UUID roleGrant = repository.listGrants(tenant).stream()
                .filter(g -> "role".equals(g.rule().principalType())).findFirst().orElseThrow().id();
        assertThrows(PermissionException.class, () -> exceptions.revokeGrant(roleGrant));
        TenantContextHolder.clear();
        assertThrows(PermissionException.class, () -> list(null, null, null, false));
    }

    boolean allowed(UUID person, String resource, String action) {
        TenantContextHolder.set(context(person));
        try {
            return permissions.service().decide(resource, action).allowed();
        } finally {
            TenantContextHolder.set(adminContext);
        }
    }
}
