package one.nexus.permission.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.engine.DataScopeKind;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.engine.FieldRule;
import one.nexus.permission.engine.Mask;
import one.nexus.permission.persist.PolicyRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/permission")
public class PermissionController {
    private final PermissionModule module;

    public PermissionController(PermissionModule module) {
        this.module = module;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "module", "nexus-permission-core");
    }

    @PostMapping("/check")
    public Map<String, Object> check(@RequestBody CheckBody body) {
        Decision decision = module.service().decide(body.resource(), body.action());
        return Map.of(
                "allowed", decision.allowed(),
                "reason", decision.reason(),
                "policy_version", decision.policyVersion() == null ? 0 : decision.policyVersion(),
                "fields", decision.fields());
    }

    @GetMapping("/snapshot")
    public Map<String, Object> snapshot() {
        var snapshot = module.service().snapshot();
        return Map.of(
                "tenant_id", snapshot.tenantId(),
                "version", snapshot.version(),
                "grant_count", snapshot.grants().size(),
                "deny_count", snapshot.denies().size());
    }

    @PostMapping("/roles")
    public PolicyRepository.RoleRecord createRole(@RequestBody RoleBody body) {
        return module.admin().createRole(body.code(), body.name(), body.systemRole());
    }

    @PostMapping("/assignments")
    public PolicyRepository.AssignmentRecord assign(@RequestBody AssignmentBody body) {
        return module.admin().assign(body.principalType(), body.principalId(), body.roleId());
    }

    @PostMapping("/grants")
    public PolicyRepository.GrantRecord grant(@RequestBody GrantBody body) {
        Map<String, FieldRule> fields = Map.of();
        if (body.fields() != null) {
            fields = body.fields().entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey,
                    entry -> new FieldRule(
                            entry.getValue().visible(),
                            entry.getValue().editable(),
                            entry.getValue().mask() == null ? Mask.NONE : Mask.valueOf(entry.getValue().mask()),
                            entry.getValue().secondAuthorization())));
        }
        return module.admin().grant(
                body.effect(),
                body.principalType(),
                body.principalId(),
                body.resource(),
                body.action(),
                body.scopeKind() == null ? DataScopeKind.SELF : DataScopeKind.valueOf(body.scopeKind()),
                body.specifiedDepartments() == null ? List.of() : body.specifiedDepartments(),
                fields);
    }

    public record CheckBody(String resource, String action) {}

    public record RoleBody(String code, String name, boolean systemRole) {}

    public record AssignmentBody(String principalType, String principalId, UUID roleId) {}

    public record GrantBody(
            String effect,
            String principalType,
            String principalId,
            String resource,
            String action,
            String scopeKind,
            List<String> specifiedDepartments,
            Map<String, FieldBody> fields
    ) {}

    public record FieldBody(boolean visible, boolean editable, String mask, boolean secondAuthorization) {}
}
