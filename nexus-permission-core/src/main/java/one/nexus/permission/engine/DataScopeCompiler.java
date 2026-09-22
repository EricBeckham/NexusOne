package one.nexus.permission.engine;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Compiles product DataScope kinds to the P0-02 Predicate tree.
 * Column names are fixed ledger system fields, never request identifiers.
 */
public final class DataScopeCompiler {
    public static final String OWNER_COLUMN = "owner_id";
    public static final String DEPARTMENT_COLUMN = "department_id";

    private DataScopeCompiler() {}

    public static Predicate compile(
            DataScopeKind kind,
            Subject subject,
            DepartmentCatalog catalog,
            List<String> specifiedDepartmentIds
    ) {
        if (kind == null) {
            return Predicate.FALSE;
        }
        return switch (kind) {
            case ALL -> Predicate.TRUE;
            case SELF -> Predicate.atom(OWNER_COLUMN, "=", subject.userId());
            case DEPT -> inDepartments(subject.departments());
            case DEPT_AND_CHILDREN -> inDepartments(
                    (catalog == null ? DepartmentCatalog.EMPTY : catalog).selfAndDescendants(subject.departments()));
            case SPECIFIED -> inDepartments(specifiedDepartmentIds == null ? Set.of() : Set.copyOf(specifiedDepartmentIds));
            case CUSTOM -> Predicate.TRUE;
        };
    }

    public static Predicate scopeFor(Rule rule, Subject subject, DepartmentCatalog catalog) {
        if (rule.scopeKind() == DataScopeKind.CUSTOM) {
            return rule.scope();
        }
        return compile(rule.scopeKind(), subject, catalog, rule.specifiedDepartmentIds());
    }

    private static Predicate inDepartments(Set<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Predicate.FALSE;
        }
        if (ids.size() == 1) {
            return Predicate.atom(DEPARTMENT_COLUMN, "=", ids.iterator().next());
        }
        return Predicate.atom(DEPARTMENT_COLUMN, "IN", new ArrayList<>(new LinkedHashSet<>(ids)));
    }
}
