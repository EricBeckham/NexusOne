package one.nexus.p1.application;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import one.nexus.p1.application.TenantRelationService.TenantRelationNode;
import one.nexus.p1.domain.AuditEvent;
import one.nexus.p1.domain.DomainException;
import one.nexus.p1.domain.ErrorCode;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.AuditPort;
import one.nexus.p1.port.ClockPort;
import one.nexus.p1.port.IdGenerator;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p1.port.PlatformContext;
import one.nexus.p1.port.TenantRelationStore;
import one.nexus.p1.port.TenantRelationStore.TenantLink;

/** Platform-admin parent change. Rejects cycles and does not return business rows. */
@Service
public class TenantReparentService {
    private final TenantRelationStore relations;
    private final IsolationGuard isolation;
    private final AuditPort audit;
    private final IdGenerator ids;
    private final ClockPort clock;

    public TenantReparentService(
            TenantRelationStore relations,
            IsolationGuard isolation,
            AuditPort audit,
            IdGenerator ids,
            ClockPort clock) {
        this.relations = relations;
        this.isolation = isolation;
        this.audit = audit;
        this.ids = ids;
        this.clock = clock;
    }

    @Transactional
    public TenantRelationNode reparent(UUID tenantId, UUID parentTenantId) {
        PlatformContext platform = isolation.requirePlatformContext("tenant.reparent", PlatformDuty.SYSTEM_ADMIN);
        List<TenantLink> links = relations.listActive();
        TenantLink current = find(links, tenantId);
        if (current == null) {
            throw new DomainException(ErrorCode.TENANT_NOT_FOUND, "tenant not found");
        }
        if (parentTenantId != null && find(links, parentTenantId) == null) {
            throw new DomainException(ErrorCode.TENANT_NOT_FOUND, "parent tenant not found");
        }
        if (parentTenantId != null && (parentTenantId.equals(tenantId) || isDescendant(links, tenantId, parentTenantId))) {
            Map<String, Object> details = parentDetails(current.parentTenantId(), parentTenantId);
            audit.appendNewTransaction(event(
                    AuditEvent.TENANT_PARENT_REJECTED, current, platform, AuditEvent.OUTCOME_REJECTED, details));
            throw new DomainException(ErrorCode.TENANT_PARENT_CYCLE,
                    "tenant cannot be placed under itself or its descendant", details);
        }
        Instant now = clock.now();
        if (!relations.updateParent(tenantId, parentTenantId, now, platform.actor().subjectId())) {
            throw new DomainException(ErrorCode.TENANT_NOT_FOUND, "tenant not found");
        }
        audit.append(event(
                AuditEvent.TENANT_PARENT_UPDATED, current, platform, AuditEvent.OUTCOME_ACCEPTED,
                parentDetails(current.parentTenantId(), parentTenantId)));
        return new TenantRelationNode(current.id(), current.code(), current.name(), current.status(), parentTenantId);
    }

    private static TenantLink find(List<TenantLink> links, UUID id) {
        for (TenantLink link : links) {
            if (link.id().equals(id)) {
                return link;
            }
        }
        return null;
    }

    private static boolean isDescendant(List<TenantLink> links, UUID ancestor, UUID candidate) {
        Map<UUID, List<UUID>> children = new HashMap<>();
        for (TenantLink link : links) {
            if (link.parentTenantId() != null) {
                children.computeIfAbsent(link.parentTenantId(), ignored -> new ArrayList<>()).add(link.id());
            }
        }
        Set<UUID> seen = new HashSet<>();
        ArrayDeque<UUID> pending = new ArrayDeque<>();
        pending.add(ancestor);
        while (!pending.isEmpty()) {
            UUID current = pending.removeFirst();
            for (UUID child : children.getOrDefault(current, List.of())) {
                if (!seen.add(child)) {
                    continue;
                }
                if (child.equals(candidate)) {
                    return true;
                }
                pending.add(child);
            }
        }
        return false;
    }

    private static Map<String, Object> parentDetails(UUID previousParentId, UUID parentTenantId) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("previous_parent_tenant_id", previousParentId == null ? "" : previousParentId.toString());
        details.put("parent_tenant_id", parentTenantId == null ? "" : parentTenantId.toString());
        return details;
    }

    private AuditEvent event(
            String type, TenantLink tenant, PlatformContext platform, String outcome, Map<String, Object> details) {
        return new AuditEvent(
                ids.next(),
                type,
                AuditEvent.VERSION,
                clock.now(),
                tenant.id(),
                platform.actor().subjectId(),
                platform.actorHomeTenantId(),
                true,
                platform.reason(),
                platform.traceId(),
                platform.traceId(),
                null,
                outcome,
                "tenant",
                tenant.id().toString(),
                details);
    }
}
