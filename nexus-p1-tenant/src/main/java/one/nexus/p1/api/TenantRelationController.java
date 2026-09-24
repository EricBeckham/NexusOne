package one.nexus.p1.api;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import one.nexus.p1.application.TenantRelationService;
import one.nexus.p1.application.TenantRelationService.TenantRelationNode;
import one.nexus.p1.application.TenantRelationService.TenantTreeNode;

/** Read-only tenant parent tree. Does not reparent tenants or return business rows. */
@RestController
@RequestMapping("/api/v1/p1")
public class TenantRelationController {
    private final TenantRelationService relations;

    public TenantRelationController(TenantRelationService relations) {
        this.relations = relations;
    }

    @GetMapping("/tenants/tree")
    public RelationTree tree() {
        return new RelationTree(relations.tree());
    }

    @GetMapping("/tenants/{id}/children")
    public List<TenantRelationNode> children(@PathVariable UUID id) {
        return relations.children(id);
    }

    @GetMapping("/tenants/{id}/ancestors")
    public List<TenantRelationNode> ancestors(@PathVariable UUID id) {
        return relations.ancestors(id);
    }

    public record RelationTree(List<TenantTreeNode> nodes) {}
}
