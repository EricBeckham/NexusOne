package one.nexus.permission.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.p2.IdentityModule;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.effective.EffectivePermissionService;
import one.nexus.permission.effective.EffectivePermissionService.Effective;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** PLA3: effective-permission preview for a person or a position; needs permission read access. */
@RestController
@RequestMapping("/api/v1/permission/effective")
public class EffectivePermissionController {
    private final EffectivePermissionService preview;
    private final PermissionAdminGuard guard;

    public EffectivePermissionController(PermissionModule permissions, IdentityModule identity, PermissionAdminGuard guard) {
        this.preview = new EffectivePermissionService(permissions, identity.repository());
        this.guard = guard;
    }

    @GetMapping("/persons/{personId}")
    public Map<String, Object> person(@PathVariable UUID personId) {
        guard.requireRead();
        return view(preview.forPerson(personId));
    }

    @GetMapping("/positions/{positionId}")
    public Map<String, Object> position(@PathVariable UUID positionId) {
        guard.requireRead();
        return view(preview.forPosition(positionId));
    }

    /** Explicit snake_case keys so the wire is the same under every JSON naming strategy. */
    private static Map<String, Object> view(Effective effective) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("subject_type", effective.subjectType());
        out.put("subject_id", effective.subjectId());
        out.put("duty_conflict", effective.dutyConflict());
        out.put("policy_version", effective.policyVersion());
        out.put("roles", effective.roles().stream().map(role -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("role_code", role.roleCode());
            row.put("role_name", role.roleName());
            row.put("via", role.via());
            row.put("via_id", role.viaId());
            row.put("via_label", role.viaLabel());
            return row;
        }).toList());
        out.put("permissions", effective.permissions().stream().map(permission -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("resource", permission.resource());
            row.put("action", permission.action());
            row.put("outcome", permission.outcome());
            List<Map<String, Object>> sources = permission.sources().stream().map(source -> {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("effect", source.effect());
                s.put("scope", source.scope());
                s.put("principal_type", source.principalType());
                s.put("principal_id", source.principalId());
                s.put("label", source.label());
                return s;
            }).toList();
            row.put("sources", sources);
            return row;
        }).toList());
        return out;
    }
}
