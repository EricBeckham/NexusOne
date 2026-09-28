package one.nexus.permission.api;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.pack.RolePackService.AppliedRole;
import one.nexus.permission.pack.RolePackService.GrantSpec;
import one.nexus.permission.pack.RolePackService.RolePackCommand;
import one.nexus.permission.pack.RolePackService.RoleSpec;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PLA7 / PLX1 C3 over HTTP — operations and debugging only; the market calls the Java port. Lands the pack in the
 * caller's own tenant and needs {@code permission.role:write} (or a tenant-admin position).
 */
@RestController
@RequestMapping("/api/v1/permission/role-packs")
public class RolePackController {
    private final PermissionModule permissions;
    private final PermissionAdminGuard guard;

    public RolePackController(PermissionModule permissions, PermissionAdminGuard guard) {
        this.permissions = permissions;
        this.guard = guard;
    }

    @PostMapping("/apply")
    public Map<String, Object> apply(@RequestBody PackBody body) {
        guard.requireWrite(PermissionAdminGuard.ROLE);
        RolePackCommand command = new RolePackCommand(body.appCode(), body.appVersion(),
                body.rolePack() == null ? List.of() : body.rolePack().stream()
                        .map(role -> new RoleSpec(role.code(), role.name(), role.grants() == null ? List.of()
                                : role.grants().stream().map(g -> new GrantSpec(g.resource(), g.action(), g.scope())).toList()))
                        .toList());
        List<Map<String, Object>> roles = permissions.rolePacks().apply(command).roles().stream()
                .map(RolePackController::view).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("roles", roles);
        return out;
    }

    /** Explicit keys so the wire is the same under every JSON naming strategy. */
    private static Map<String, Object> view(AppliedRole role) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tenant_role_code", role.tenantRoleCode());
        row.put("created", role.created());
        row.put("grants_added", role.grantsAdded());
        row.put("grants_not_in_pack", role.grantsNotInPack());
        return row;
    }

    public record PackBody(
            @JsonAlias({"app_code", "appCode"}) String appCode,
            @JsonAlias({"app_version", "appVersion"}) String appVersion,
            @JsonAlias({"role_pack", "rolePack"}) List<RoleBody> rolePack) {}

    public record RoleBody(String code, String name, List<GrantBody> grants) {}

    public record GrantBody(String resource, String action, String scope) {}
}
