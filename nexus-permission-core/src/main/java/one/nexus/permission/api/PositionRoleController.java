package one.nexus.permission.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.p2.IdentityModule;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.position.PositionRoleService;
import one.nexus.permission.position.PositionRoleService.BoundRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PLA1: position ↔ role binding. Reading needs the permission read access of {@link PermissionAdminGuard}; changing
 * needs {@code permission.assignment:write} (or a tenant-admin position).
 */
@RestController
@RequestMapping("/api/v1/permission/positions")
public class PositionRoleController {
    private final PositionRoleService bindings;
    private final PermissionAdminGuard guard;

    public PositionRoleController(PermissionModule permissions, IdentityModule identity, PermissionAdminGuard guard) {
        this.bindings = new PositionRoleService(permissions, identity.repository());
        this.guard = guard;
    }

    /** Every position of the current tenant that has roles, with its roles. */
    @GetMapping("/roles")
    public List<Map<String, Object>> all() {
        guard.requireRead();
        return bindings.all().entrySet().stream()
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("position_id", entry.getKey());
                    row.put("roles", entry.getValue().stream().map(PositionRoleController::view).toList());
                    return row;
                })
                .toList();
    }

    @GetMapping("/{positionId}/roles")
    public List<Map<String, Object>> rolesOf(@PathVariable UUID positionId) {
        guard.requireRead();
        return bindings.rolesOf(positionId).stream().map(PositionRoleController::view).toList();
    }

    /** Replaces the position's roles with {@code role_ids}. */
    @PutMapping("/{positionId}/roles")
    public List<Map<String, Object>> set(@PathVariable UUID positionId, @RequestBody RoleIdsBody body) {
        guard.requireWrite(PermissionAdminGuard.ASSIGNMENT);
        return bindings.set(positionId, body == null ? List.of() : body.roleIds()).stream()
                .map(PositionRoleController::view).toList();
    }

    /** Explicit keys so the wire is the same under every JSON naming strategy. */
    private static Map<String, Object> view(BoundRole role) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("assignment_id", role.assignmentId());
        row.put("role_id", role.roleId());
        row.put("role_code", role.roleCode());
        row.put("role_name", role.roleName());
        return row;
    }

    /** Accepts {@code role_ids} (product wire) and {@code roleIds}. */
    public record RoleIdsBody(@com.fasterxml.jackson.annotation.JsonAlias({"role_ids", "roleIds"}) List<UUID> roleIds) {}
}
