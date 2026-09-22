package one.nexus.permission.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.AuditPort;
import one.nexus.audit.ChainedAuditEvent;
import one.nexus.audit.InMemoryHashChainedAuditPort;
import one.nexus.common.trace.TraceIds;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.persist.InMemoryIdentityRepository;
import one.nexus.p2.smtp.KeycloakSmtpHook;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.persist.PolicyRepository;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class G17DutyAssignTest {
    private static final String TRACE = "0199a1c0-0000-7000-8000-00000000d017";

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
        TraceIds.clearMdc();
    }

    @Test
    void assignRejectsConflictingDutyPairAndEvaluateDeniesIfForced() {
        AuditPort audits = new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        InMemoryIdentityRepository identity = new InMemoryIdentityRepository();
        PermissionModule permission = PermissionModule.create(
                new InMemoryPolicyRepository(), identity, new InMemoryPolicyInvalidation(), audits);
        StubIdentityProvider idp = new StubIdentityProvider();
        IdentityModule iam = new IdentityModule(
                identity, idp, new KeycloakSmtpHook(idp, false), java.time.Clock.systemUTC(), Duration.ofHours(1));
        UUID tenant = UUID.fromString("17171717-1717-4717-8717-171717171717");
        iam.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenant, "Duty Admin", "g17-duty@example.com", "g17-duty"));
        var person = identity.findPersonByAccount(
                tenant, identity.findAccountByEmail("g17-duty@example.com").orElseThrow().id()).orElseThrow();
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenant, person.accountId(), person.id(), "g17-duty",
                Instant.now(), Instant.now().plusSeconds(3600)));
        TraceIds.putMdc(TRACE);

        permission.duties().install();
        PolicyRepository.RoleRecord system = permission.repository()
                .findRoleByCode(tenant, PlatformDutyCatalog.SYSTEM_ADMIN).orElseThrow();
        PolicyRepository.RoleRecord audit = permission.repository()
                .findRoleByCode(tenant, PlatformDutyCatalog.AUDIT_ADMIN).orElseThrow();

        permission.admin().assign("user", person.id().toString(), system.id());
        PermissionException conflict = assertThrows(
                PermissionException.class,
                () -> permission.admin().assign("user", person.id().toString(), audit.id()));
        assertEquals("P2.PERM.0014", conflict.errorCode().code());
        assertEquals(409, conflict.httpStatus());

        List<ChainedAuditEvent> events = audits.findByTraceId(TRACE);
        assertTrue(events.stream().anyMatch(event -> AuditCaptures.PERMISSION_DENIED.equals(event.event().eventType())),
                events.toString());
        assertTrue(events.stream().anyMatch(event -> "duty_conflict".equals(event.event().reason())),
                events.toString());
        assertTrue(audits.verifyChain().intact());

        PolicyRepository.RoleRecord security = permission.repository()
                .findRoleByCode(tenant, PlatformDutyCatalog.SECURITY_ADMIN).orElseThrow();
        PermissionException inherit = assertThrows(
                PermissionException.class,
                () -> permission.admin().inherit(system.id(), security.id()));
        assertEquals("P2.PERM.0014", inherit.errorCode().code());
    }
}
