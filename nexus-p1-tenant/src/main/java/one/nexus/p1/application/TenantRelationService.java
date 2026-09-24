package one.nexus.p1.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import one.nexus.p1.domain.DomainException;
import one.nexus.p1.domain.ErrorCode;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p1.port.IsolationGuard.IsolationSnapshot;
import one.nexus.p1.port.PlatformContext;
import one.nexus.p1.port.TenantRelationStore;
import one.nexus.p1.port.TenantRelationStore.TenantLink;

/** Read-only parent/child/ancestor projection. No reparent writes and no business payload. */
@Service
public class TenantRelationService {
    private final TenantRelationStore relations;
    private final IsolationGuard isolation;

    public TenantRelationService(TenantRelationStore relations, IsolationGuard isolation) {
        this.relations = relations;
        this.isolation = isolation;
    }

    @Transactional(readOnly = true)
    public List<TenantTreeNode> tree() {
        Scope scope = scope("tenant.relation.tree");
        return forest(scope.links());
    }

    @Transactional(readOnly = true)
    public List<TenantRelationNode> children(UUID tenantId) {
        Scope scope = scope("tenant.relation.children");
        requireVisible(scope, tenantId);
        return scope.links().stream()
                .filter(link -> tenantId.equals(link.parentTenantId()))
                .map(TenantRelationService::flat)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TenantRelationNode> ancestors(UUID tenantId) {
        Scope scope = scope("tenant.relation.ancestors");
        TenantLink current = requireVisible(scope, tenantId);
        Map<UUID, TenantLink> byId = index(scope.links());
        List<TenantRelationNode> chain = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        UUID parentId = current.parentTenantId();
        while (parentId != null && seen.add(parentId)) {
            TenantLink parent = byId.get(parentId);
            if (parent == null) {
                break;
            }
            chain.add(flat(parent));
            parentId = parent.parentTenantId();
        }
        return List.copyOf(chain);
    }

    private Scope scope(String operation) {
        IsolationSnapshot snapshot = isolation.current();
        PlatformContext platform = snapshot.platform();
        List<TenantLink> all = relations.listActive();
        if (platform != null && platform.active()) {
            isolation.requirePlatformContext(operation, PlatformDuty.SYSTEM_ADMIN);
            return new Scope(all, null);
        }
        if (snapshot.tenantId() == null) {
            throw new DomainException(
                    ErrorCode.CONTEXT_PLATFORM_REQUIRED,
                    "platform or tenant context required for " + operation,
                    Map.of("operation", operation));
        }
        return new Scope(subtree(all, snapshot.tenantId()), snapshot.tenantId());
    }

    private static TenantLink requireVisible(Scope scope, UUID tenantId) {
        return scope.links().stream()
                .filter(link -> tenantId.equals(link.id()))
                .findFirst()
                .orElseThrow(() -> scope.tenantId() == null
                        ? new DomainException(ErrorCode.TENANT_NOT_FOUND, "tenant not found",
                                Map.of("tenant_id", tenantId.toString()))
                        : new DomainException(ErrorCode.CONTEXT_TENANT_MISMATCH,
                                "tenant context cannot read another tenant relation",
                                Map.of("operation", "tenant.relation", "tenant_id", tenantId.toString())));
    }

    static List<TenantLink> subtree(List<TenantLink> all, UUID rootId) {
        Map<UUID, List<TenantLink>> children = childrenByParent(all);
        Set<UUID> visible = new HashSet<>();
        List<TenantLink> pending = new ArrayList<>();
        all.stream().filter(link -> rootId.equals(link.id())).findFirst().ifPresent(pending::add);
        List<TenantLink> kept = new ArrayList<>();
        while (!pending.isEmpty()) {
            TenantLink link = pending.remove(pending.size() - 1);
            if (!visible.add(link.id())) {
                continue;
            }
            kept.add(link);
            pending.addAll(children.getOrDefault(link.id(), List.of()));
        }
        return List.copyOf(kept);
    }

    static List<TenantTreeNode> forest(List<TenantLink> links) {
        Map<UUID, List<TenantLink>> children = childrenByParent(links);
        Set<UUID> ids = new HashSet<>();
        for (TenantLink link : links) {
            ids.add(link.id());
        }
        Set<UUID> placed = new HashSet<>();
        List<TenantTreeNode> roots = new ArrayList<>();
        for (TenantLink link : links) {
            if (link.parentTenantId() == null || !ids.contains(link.parentTenantId())) {
                roots.add(node(link, children, placed));
            }
        }
        for (TenantLink link : links) {
            if (!placed.contains(link.id())) {
                roots.add(node(link, children, placed));
            }
        }
        return List.copyOf(roots);
    }

    private static TenantTreeNode node(TenantLink link, Map<UUID, List<TenantLink>> children, Set<UUID> placed) {
        if (!placed.add(link.id())) {
            return new TenantTreeNode(link.id(), link.code(), link.name(), link.status(), link.parentTenantId(), List.of());
        }
        List<TenantTreeNode> nested = new ArrayList<>();
        for (TenantLink child : children.getOrDefault(link.id(), List.of())) {
            nested.add(node(child, children, placed));
        }
        return new TenantTreeNode(link.id(), link.code(), link.name(), link.status(), link.parentTenantId(), List.copyOf(nested));
    }

    private static Map<UUID, List<TenantLink>> childrenByParent(List<TenantLink> links) {
        Map<UUID, List<TenantLink>> children = new HashMap<>();
        for (TenantLink link : links) {
            if (link.parentTenantId() != null) {
                children.computeIfAbsent(link.parentTenantId(), ignored -> new ArrayList<>()).add(link);
            }
        }
        return children;
    }

    private static Map<UUID, TenantLink> index(List<TenantLink> links) {
        Map<UUID, TenantLink> byId = new HashMap<>();
        for (TenantLink link : links) {
            byId.put(link.id(), link);
        }
        return byId;
    }

    private static TenantRelationNode flat(TenantLink link) {
        return new TenantRelationNode(link.id(), link.code(), link.name(), link.status(), link.parentTenantId());
    }

    private record Scope(List<TenantLink> links, UUID tenantId) {}

    public record TenantRelationNode(UUID id, String code, String name, String status, UUID parentTenantId) {}

    public record TenantTreeNode(
            UUID id, String code, String name, String status, UUID parentTenantId, List<TenantTreeNode> children) {}
}
