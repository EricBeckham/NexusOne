package one.nexus.permission.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.p2.IdentityModule;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.personal.ExceptionGrantService;
import one.nexus.permission.personal.ExceptionGrantService.ExceptionItem;
import one.nexus.permission.support.PermissionException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * PLA4 (D5): exception grants — grants and roles attached directly to a person. Listing needs permission read
 * access; revoking a grant needs {@code permission.grant:write}, a role assignment {@code permission.assignment:write}.
 */
@RestController
@RequestMapping("/api/v1/permission/exceptions")
public class ExceptionGrantController {
    private final ExceptionGrantService exceptions;
    private final PermissionAdminGuard guard;

    public ExceptionGrantController(PermissionModule permissions, IdentityModule identity, PermissionAdminGuard guard) {
        this.exceptions = new ExceptionGrantService(permissions, identity.repository());
        this.guard = guard;
    }

    @GetMapping
    public List<Map<String, Object>> list(
            @RequestParam(name = "person_id", required = false) UUID personId,
            @RequestParam(name = "kind", required = false) String kind,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "include_system", required = false, defaultValue = "false") boolean includeSystem) {
        guard.requireRead();
        return exceptions.list(new ExceptionGrantService.Filter(personId, kind, query, includeSystem)).stream()
                .map(ExceptionGrantController::view).toList();
    }

    @DeleteMapping("/grants/{grantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeGrant(@PathVariable UUID grantId) {
        guard.requireWrite(PermissionAdminGuard.GRANT);
        if (!exceptions.revokeGrant(grantId)) {
            throw PermissionException.notFound("grant not found in current tenant");
        }
    }

    @DeleteMapping("/assignments/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeAssignment(@PathVariable UUID assignmentId) {
        guard.requireWrite(PermissionAdminGuard.ASSIGNMENT);
        if (!exceptions.revokeAssignment(assignmentId)) {
            throw PermissionException.notFound("assignment not found in current tenant");
        }
    }

    /** Explicit snake_case keys so the wire is the same under every JSON naming strategy. */
    private static Map<String, Object> view(ExceptionItem item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("kind", item.kind());
        row.put("id", item.id());
        row.put("person_id", item.personId());
        row.put("person_name", item.personName());
        row.put("effect", item.effect());
        row.put("resource", item.resource());
        row.put("action", item.action());
        row.put("scope", item.scope());
        row.put("role_code", item.roleCode());
        row.put("role_name", item.roleName());
        row.put("system", item.system());
        return row;
    }
}
