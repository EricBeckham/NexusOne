package one.nexus.permission.engine;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Authorization subject. {@code userId} is the tenant-scoped person id (M0-04 Person),
 * never a client-supplied tenant header.
 */
public record Subject(
        String tenantId,
        String userId,
        Set<String> roles,
        Set<String> positions,
        Set<String> departments,
        Map<String, String> departmentPaths
) {
    public Subject {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        roles = Set.copyOf(roles == null ? Set.of() : roles);
        positions = Set.copyOf(positions == null ? Set.of() : positions);
        departments = Set.copyOf(departments == null ? Set.of() : departments);
        departmentPaths = Map.copyOf(departmentPaths == null ? Map.of() : departmentPaths);
    }

    public Subject(String tenantId, String userId, Set<String> roles, Set<String> positions, Set<String> departments) {
        this(tenantId, userId, roles, positions, departments, Map.of());
    }

    public Subject withDepartmentPaths(Map<String, String> paths) {
        Map<String, String> next = new LinkedHashMap<>(departmentPaths);
        if (paths != null) {
            next.putAll(paths);
        }
        return new Subject(tenantId, userId, roles, positions, departments, next);
    }

    public Subject withRoles(Set<String> extra) {
        Set<String> next = new LinkedHashSet<>(roles);
        if (extra != null) {
            next.addAll(extra);
        }
        return new Subject(tenantId, userId, next, positions, departments, departmentPaths);
    }
}
