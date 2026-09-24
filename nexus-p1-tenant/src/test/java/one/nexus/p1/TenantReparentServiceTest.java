package one.nexus.p1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import one.nexus.p1.api.P1ApiExceptionHandler;
import one.nexus.p1.api.TenantReparentController;
import one.nexus.p1.application.TenantReparentService;
import one.nexus.p1.domain.Actor;
import one.nexus.p1.domain.AuditEvent;
import one.nexus.p1.domain.DomainException;
import one.nexus.p1.domain.ErrorCode;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.AuditPort;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p1.port.PlatformContext;
import one.nexus.p1.port.TenantRelationStore;
import one.nexus.p1.port.TenantRelationStore.TenantLink;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TenantReparentServiceTest {
    private final UUID root = UUID.fromString("01900000-0000-7000-8000-0000000000a1");
    private final UUID child = UUID.fromString("01900000-0000-7000-8000-0000000000a2");
    private final UUID outsider = UUID.fromString("01900000-0000-7000-8000-0000000000b1");
    private final List<AuditEvent> audits = new ArrayList<>();

    @Test
    void platformAdminCanMoveAndClearAParent() throws Exception {
        MutableStore store = store();
        TenantReparentService service = service(store, platform());
        var moved = service.reparent(outsider, root);
        assertEquals(root, moved.parentTenantId());
        assertEquals(root, store.parentOf(outsider));
        assertEquals(AuditEvent.TENANT_PARENT_UPDATED, audits.get(0).eventType());
        assertEquals(AuditEvent.OUTCOME_ACCEPTED, audits.get(0).outcome());
        assertTrue(audits.get(0).details().keySet().stream().allMatch(key -> key.contains("parent")));

        var cleared = service.reparent(child, null);
        assertEquals(null, cleared.parentTenantId());
        assertEquals(null, store.parentOf(child));

        MockMvc mvc = mvc(service);
        mvc.perform(put("/api/v1/p1/tenants/" + child + "/parent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentTenantId\":\"" + outsider + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentTenantId").value(outsider.toString()))
                .andExpect(jsonPath("$.code").value("child"));
        mvc.perform(patch("/api/v1/p1/tenants/" + child + "/parent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentTenantId\":null}"))
                .andExpect(status().isOk());
        assertEquals(null, store.parentOf(child));
    }

    @Test
    void aCycleIsRejectedAndAudited() throws Exception {
        MutableStore links = store();
        TenantReparentService service = service(links, platform());
        DomainException cycle = assertThrows(DomainException.class, () -> service.reparent(root, child));
        assertEquals(ErrorCode.TENANT_PARENT_CYCLE, cycle.errorCode());
        assertEquals(AuditEvent.TENANT_PARENT_REJECTED, audits.get(0).eventType());
        assertEquals(AuditEvent.OUTCOME_REJECTED, audits.get(0).outcome());
        assertEquals(root, links.parentOf(child));

        MockMvc mvc = mvc(service);
        mvc.perform(put("/api/v1/p1/tenants/" + root + "/parent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentTenantId\":\"" + root + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("P1.TENANT.0007"));
    }

    @Test
    void tenantContextCannotReparentAnotherTenant() throws Exception {
        TenantReparentService service = service(store(), tenant(child));
        DomainException denied = assertThrows(DomainException.class, () -> service.reparent(outsider, root));
        assertEquals(ErrorCode.CONTEXT_PLATFORM_REQUIRED, denied.errorCode());
        assertTrue(audits.isEmpty());

        MockMvc mvc = mvc(service);
        mvc.perform(patch("/api/v1/p1/tenants/" + outsider + "/parent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentTenantId\":\"" + root + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P1.CONTEXT.0001"));
    }

    @Test
    void missingTenantIsNotFound() {
        TenantReparentService service = service(store(), platform());
        DomainException missing = assertThrows(DomainException.class,
                () -> service.reparent(UUID.fromString("01900000-0000-7000-8000-0000000000c1"), null));
        assertEquals(ErrorCode.TENANT_NOT_FOUND, missing.errorCode());
        DomainException missingParent = assertThrows(DomainException.class,
                () -> service.reparent(child, UUID.fromString("01900000-0000-7000-8000-0000000000c1")));
        assertEquals(ErrorCode.TENANT_NOT_FOUND, missingParent.errorCode());
    }

    private MutableStore store() {
        return new MutableStore(new ArrayList<>(List.of(
                new TenantLink(root, "group", "Group", "ENABLED", null),
                new TenantLink(child, "child", "Child", "ENABLED", root),
                new TenantLink(outsider, "other", "Other", "ENABLED", null))));
    }

    private TenantReparentService service(MutableStore store, IsolationGuard.IsolationSnapshot snapshot) {
        return new TenantReparentService(store, new FixedGuard(snapshot), new MemoryAudit(), () -> UUID.randomUUID(),
                () -> Instant.parse("2026-09-24T00:00:00Z"));
    }

    private static MockMvc mvc(TenantReparentService service) {
        return MockMvcBuilders.standaloneSetup(new TenantReparentController(service))
                .setControllerAdvice(new P1ApiExceptionHandler())
                .build();
    }

    private static IsolationGuard.IsolationSnapshot platform() {
        Actor actor = new Actor(UUID.randomUUID(), null, "platform", Set.of(PlatformDuty.SYSTEM_ADMIN));
        return new IsolationGuard.IsolationSnapshot(
                actor, new PlatformContext(true, "m12", actor, "trace-m12", null), null, "trace-m12");
    }

    private static IsolationGuard.IsolationSnapshot tenant(UUID tenantId) {
        Actor actor = new Actor(UUID.randomUUID(), tenantId, "member", Set.of());
        return new IsolationGuard.IsolationSnapshot(actor, PlatformContext.inactive(), tenantId, "trace-m12");
    }

    private final class MemoryAudit implements AuditPort {
        @Override
        public void append(AuditEvent event) {
            audits.add(event);
        }

        @Override
        public void appendNewTransaction(AuditEvent event) {
            audits.add(event);
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

    private static final class MutableStore implements TenantRelationStore {
        private final List<TenantLink> links;

        private MutableStore(List<TenantLink> links) {
            this.links = links;
        }

        @Override
        public List<TenantLink> listActive() {
            return List.copyOf(links);
        }

        @Override
        public boolean updateParent(UUID tenantId, UUID parentTenantId, Instant updatedAt, UUID updatedBy) {
            for (int index = 0; index < links.size(); index++) {
                TenantLink link = links.get(index);
                if (link.id().equals(tenantId)) {
                    links.set(index, new TenantLink(link.id(), link.code(), link.name(), link.status(), parentTenantId));
                    return true;
                }
            }
            return false;
        }

        private UUID parentOf(UUID tenantId) {
            return links.stream().filter(link -> link.id().equals(tenantId)).findFirst().orElseThrow().parentTenantId();
        }
    }
}
