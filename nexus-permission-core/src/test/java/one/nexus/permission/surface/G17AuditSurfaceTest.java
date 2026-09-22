package one.nexus.permission.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import one.nexus.audit.AuditCaptures;
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
import one.nexus.permission.admin.PlatformDutyCatalog;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class G17AuditSurfaceTest {
    private static final String TRACE = "0199a1c0-0000-7000-8000-00000000d117";

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
        TraceIds.clearMdc();
    }

    @Test
    void auditAdminReadPassesAndWriteDeleteAdminEmitDenyOnChain() {
        InMemoryHashChainedAuditPort audits =
                new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        InMemoryIdentityRepository identity = new InMemoryIdentityRepository();
        PermissionModule permission = PermissionModule.create(
                new InMemoryPolicyRepository(), identity, new InMemoryPolicyInvalidation(), audits);
        IdentityModule iam = new IdentityModule(
                identity, new StubIdentityProvider(), new KeycloakSmtpHook(new StubIdentityProvider(), false),
                java.time.Clock.systemUTC(), Duration.ofHours(1));
        UUID tenant = UUID.fromString("17171717-1717-4717-8717-171717171719");
        iam.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenant, "G17 Surface", "g17-surface@example.com", "g17-surface"));
        var person = identity.findPersonByAccount(
                tenant, identity.findAccountByEmail("g17-surface@example.com").orElseThrow().id()).orElseThrow();
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenant, person.accountId(), person.id(), "g17-surface",
                Instant.now(), Instant.now().plusSeconds(3600)));
        TraceIds.putMdc(TRACE);

        permission.duties().install();
        var auditRole = permission.repository()
                .findRoleByCode(tenant, PlatformDutyCatalog.AUDIT_ADMIN).orElseThrow();
        permission.admin().assign("user", person.id().toString(), auditRole.id());

        SurfaceGuard.require(permission.service(), audits, "audit", "read");
        assertTrue(permission.service().holdsPlatformDuty());

        for (String[] pair : new String[][] {
                {"audit", "write"},
                {"audit", "delete"},
                {"audit", "admin"},
                {"audit.config", "write"},
                {"audit.chain", "mute"}
        }) {
            PermissionException denied = assertThrows(
                    PermissionException.class,
                    () -> SurfaceGuard.require(permission.service(), audits, pair[0], pair[1]));
            assertEquals(403, denied.httpStatus());
        }

        List<ChainedAuditEvent> events = audits.findByTraceId(TRACE);
        assertTrue(events.stream().anyMatch(event -> AuditCaptures.PERMISSION_DENIED.equals(event.event().eventType())),
                events.toString());
        assertTrue(events.stream().anyMatch(event -> AuditCaptures.GUARD_REJECT.equals(event.event().eventType())),
                events.toString());
        assertTrue(audits.verifyChain().intact());
    }
}
