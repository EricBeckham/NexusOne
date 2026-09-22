package one.nexus.permission.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import one.nexus.permission.engine.DataScopeKind;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PolicyAdminAuditTest {
    private static final String TRACE = "0199a1c0-0000-7000-8000-00000000c014";

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
        TraceIds.clearMdc();
    }

    @Test
    void grantAndAssignAppendPermissionEventsQueryableByTrace() {
        AuditPort audits = new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        InMemoryIdentityRepository identity = new InMemoryIdentityRepository();
        PermissionModule permission = PermissionModule.create(
                new InMemoryPolicyRepository(), identity, new InMemoryPolicyInvalidation(), audits);
        StubIdentityProvider idp = new StubIdentityProvider();
        IdentityModule iam = new IdentityModule(
                identity, idp, new KeycloakSmtpHook(idp, false), java.time.Clock.systemUTC(), Duration.ofHours(1));
        UUID tenant = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");
        iam.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenant, "Perm Admin", "g4-perm@example.com", "g4-perm"));
        var person = identity.findPersonByAccount(
                tenant, identity.findAccountByEmail("g4-perm@example.com").orElseThrow().id()).orElseThrow();
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenant, person.accountId(), person.id(), "g4-perm",
                Instant.now(), Instant.now().plusSeconds(3600)));
        TraceIds.putMdc(TRACE);

        var role = permission.admin().createRole("reader", "Reader", false);
        permission.admin().assign("user", person.id().toString(), role.id());
        permission.admin().grant(
                "GRANT", "role", "reader", "ledger", "read", DataScopeKind.SELF, List.of(), Map.of());

        List<ChainedAuditEvent> events = audits.findByTraceId(TRACE);
        assertTrue(events.stream().anyMatch(event -> AuditCaptures.PERMISSION_GRANTED.equals(event.event().eventType())),
                events.toString());
        assertTrue(events.stream().anyMatch(event -> "role_assignment".equals(event.event().resourceType())),
                events.toString());
        assertTrue(events.stream().anyMatch(event -> "ledger".equals(event.event().resourceType())
                && "read".equals(event.event().resourceId())), events.toString());
        assertEquals(2, events.size());
        assertTrue(audits.verifyChain().intact());
    }
}
