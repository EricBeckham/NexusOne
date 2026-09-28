package one.nexus.p3.infra.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import one.nexus.p2.persist.InMemoryIdentityRepository;
import one.nexus.p2.smtp.KeycloakSmtpHook;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.MemberAccessMode;
import one.nexus.permission.engine.DataScopeKind;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class PermissionEntryReadFilterTest {
    static final String APPLY = "fn:leave:leave_apply";
    static final String EXPENSE = "fn:expense:expense_apply";

    final UUID tenant = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0c05");
    PermissionModule permissions;
    UUID person;

    @BeforeEach
    void setUp() {
        StubIdentityProvider idp = new StubIdentityProvider();
        IdentityModule iam = new IdentityModule(new InMemoryIdentityRepository(), idp, new KeycloakSmtpHook(idp, false),
                Clock.systemUTC(), Duration.ofHours(1));
        person = iam.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenant, "Admin", "portal-admin@example.com", "portal-admin")).personId();
        TenantContextHolder.set(new TenantContext(UUID.randomUUID(), tenant, null, person, "sub",
                Instant.now(), Instant.now().plusSeconds(600)));
        permissions = PermissionModule.create(new InMemoryPolicyRepository(), iam.repository(), new InMemoryPolicyInvalidation());
        permissions.admin().grant("GRANT", "user", person.toString(), "fn.leave.leave_apply", "read",
                DataScopeKind.ALL, List.of(), Map.of());
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    PermissionEntryReadFilter filter(PermissionModule module) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        if (module != null) {
            beans.addBean("permissionModule", module);
        }
        return new PermissionEntryReadFilter(beans.getBeanProvider(PermissionModule.class));
    }

    @Test
    void positionModeShowsOnlyGrantedEntries() {
        permissions.entries().useMemberAccessMode(MemberAccessMode.POSITION);
        assertEquals(List.of(APPLY), filter(permissions).readable(List.of(APPLY, EXPENSE)));
    }

    @Test
    void legacyModeShowsInstalledEntriesUnlessDenied() {
        permissions.entries().useMemberAccessMode(MemberAccessMode.LEGACY);
        assertEquals(List.of(APPLY, EXPENSE), filter(permissions).readable(List.of(APPLY, EXPENSE)));
        permissions.admin().grant("DENY", "user", person.toString(), "fn.expense.expense_apply", "read",
                DataScopeKind.ALL, List.of(), Map.of());
        assertEquals(List.of(APPLY), filter(permissions).readable(List.of(APPLY, EXPENSE)));
    }

    @Test
    void withoutAPermissionModuleNothingIsFilteredAndEmptyStaysEmpty() {
        assertEquals(List.of(APPLY, EXPENSE), filter(null).readable(List.of(APPLY, EXPENSE)));
        assertTrue(filter(permissions).readable(List.of()).isEmpty());
    }
}
