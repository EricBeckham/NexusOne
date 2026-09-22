package one.nexus.permission.engine;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Tenant-scoped department tree used to compile DEPT_AND_CHILDREN. */
public record DepartmentCatalog(Map<String, String> idToPath) {
    public static final DepartmentCatalog EMPTY = new DepartmentCatalog(Map.of());

    public DepartmentCatalog {
        idToPath = Map.copyOf(idToPath == null ? Map.of() : idToPath);
    }

    public Set<String> selfAndDescendants(Set<String> departmentIds) {
        Set<String> out = new LinkedHashSet<>();
        if (departmentIds == null) {
            return out;
        }
        for (String id : departmentIds) {
            out.add(id);
            String path = idToPath.get(id);
            if (path == null || path.isBlank()) {
                continue;
            }
            idToPath.forEach((otherId, otherPath) -> {
                if (otherPath != null && otherPath.startsWith(path)) {
                    out.add(otherId);
                }
            });
        }
        return out;
    }
}
