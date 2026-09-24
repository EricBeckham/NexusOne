package one.nexus.p1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import one.nexus.p1.api.P1ApiExceptionHandler;
import one.nexus.p1.api.TenantRelationController;
import one.nexus.p1.application.TenantRelationService;
import one.nexus.p1.domain.Actor;
import one.nexus.p1.domain.DomainException;
import one.nexus.p1.domain.ErrorCode;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p1.port.PlatformContext;
import one.nexus.p1.port.TenantRelationStore;
import one.nexus.p1.port.TenantRelationStore.TenantLink;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TenantRelationServiceTest {
    private final UUID root = UUID.fromString("01900000-0000-7000-8000-0000000000a1");
    private final UUID child = UUID.fromString("01900000-0000-7000-8000-0000000000a2");
    private final UUID outsider = UUID.fromString("01900000-0000-7000-8000-0000000000b1");
    private final List<TenantLink> links = List.of(
            new TenantLink(root, "group", "Group", "ENABLED", null),
            new TenantLink(child, "child", "Child", "ENABLED", root),
            new TenantLink(outsider, "other", "Other", "ENABLED", null));

    @Test
    void platformAdminSeesTheParentTreeAndAncestorChain() throws Exception {
        TenantRelationService service = service(platform());
        var tree = service.tree();
        assertEquals(2, tree.size());
        var group = tree.stream().filter(node -> node.id().equals(root)).findFirst().orElseThrow();
        assertEquals(1, group.children().size());
        assertEquals(child, group.children().get(0).id());
        assertEquals(List.of(root), service.ancestors(child).stream().map(node -> node.id()).toList());
        assertTrue(service.children(outsider).isEmpty());

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new TenantRelationController(service))
                .setControllerAdvice(new P1ApiExceptionHandler())
                .build();
        mvc.perform(get("/api/v1/p1/tenants/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes[?(@.code == 'group')].children[0].code").value("child"));
        mvc.perform(get("/api/v1/p1/tenants/" + child + "/ancestors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("group"));
    }

    @Test
    void tenantContextCannotReadAnotherTenant() {
        TenantRelationService service = service(tenant(child));
        var tree = service.tree();
        assertEquals(1, tree.size());
        assertEquals(child, tree.get(0).id());
        assertTrue(tree.get(0).children().isEmpty());
        assertTrue(service.ancestors(child).isEmpty());

        DomainException denied = assertThrows(DomainException.class, () -> service.children(root));
        assertEquals(ErrorCode.CONTEXT_TENANT_MISMATCH, denied.errorCode());
        DomainException missing = assertThrows(DomainException.class, () -> service.ancestors(outsider));
        assertEquals(ErrorCode.CONTEXT_TENANT_MISMATCH, missing.errorCode());
    }

    @Test
    void missingContextIsForbidden() throws Exception {
        TenantRelationService service = service(new IsolationGuard.IsolationSnapshot(
                null, PlatformContext.inactive(), null, "trace"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new TenantRelationController(service))
                .setControllerAdvice(new P1ApiExceptionHandler())
                .build();
        mvc.perform(get("/api/v1/p1/tenants/tree"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P1.CONTEXT.0001"));
    }

    @Test
    void aParentCycleDoesNotRecurseForever() {
        UUID left = UUID.randomUUID();
        UUID right = UUID.randomUUID();
        TenantRelationService service = new TenantRelationService(new FixedStore(List.of(
                new TenantLink(left, "left", "Left", "ENABLED", right),
                new TenantLink(right, "right", "Right", "ENABLED", left))), new FixedGuard(platform()));
        var nodes = service.tree();
        assertEquals(1, nodes.size());
        assertEquals(1, nodes.get(0).children().size());
        assertTrue(nodes.get(0).children().get(0).children().get(0).children().isEmpty());
    }

    private TenantRelationService service(IsolationGuard.IsolationSnapshot snapshot) {
        return new TenantRelationService(new FixedStore(links), new FixedGuard(snapshot));
    }

    private static IsolationGuard.IsolationSnapshot platform() {
        Actor actor = new Actor(UUID.randomUUID(), null, "platform", Set.of(PlatformDuty.SYSTEM_ADMIN));
        return new IsolationGuard.IsolationSnapshot(
                actor, new PlatformContext(true, "n2", actor, "trace", null), null, "trace");
    }

    private static IsolationGuard.IsolationSnapshot tenant(UUID tenantId) {
        Actor actor = new Actor(UUID.randomUUID(), tenantId, "member", Set.of());
        return new IsolationGuard.IsolationSnapshot(actor, PlatformContext.inactive(), tenantId, "trace");
    }

    private record FixedStore(List<TenantLink> links) implements TenantRelationStore {
        @Override
        public List<TenantLink> listActive() {
            return links;
        }

        @Override
        public boolean updateParent(UUID tenantId, UUID parentTenantId, java.time.Instant updatedAt, UUID updatedBy) {
            throw new UnsupportedOperationException();
        }
    }

    private record FixedGuard(IsolationSnapshot snapshot) implements IsolationGuard {
        @Override
        public PlatformContext requirePlatformContext(String operation, PlatformDuty duty) {
            PlatformContext platform = snapshot.platform();
            if (platform == null || !platform.active() || snapshot.actor() == null || !snapshot.actor().has(duty)) {
                throw new DomainException(ErrorCode.CONTEXT_PLATFORM_REQUIRED, "platform context required");
            }
            return platform;
        }

        @Override
        public void requireTenantOrPlatform(UUID tenantId, String operation) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IsolationSnapshot current() {
            return snapshot;
        }
    }
}
