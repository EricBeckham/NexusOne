package one.nexus.permission.engine;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record Rule(
        String principalType,
        String principalId,
        String resource,
        String action,
        DataScopeKind scopeKind,
        Predicate scope,
        List<String> specifiedDepartmentIds,
        Map<String, FieldRule> fields
) {
    public Rule {
        Objects.requireNonNull(principalType, "principalType");
        Objects.requireNonNull(principalId, "principalId");
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(action, "action");
        scopeKind = scopeKind == null ? DataScopeKind.CUSTOM : scopeKind;
        scope = scope == null ? Predicate.TRUE : scope;
        specifiedDepartmentIds = List.copyOf(specifiedDepartmentIds == null ? List.of() : specifiedDepartmentIds);
        fields = Map.copyOf(fields == null ? Map.of() : fields);
    }

    /** P0-02 constructor: pre-compiled scope Predicate. */
    public Rule(String principalType, String principalId, String resource, String action,
                Predicate scope, Map<String, FieldRule> fields) {
        this(principalType, principalId, resource, action, DataScopeKind.CUSTOM, scope, List.of(), fields);
    }

    public Rule(String principalType, String principalId, String resource, String action, Predicate scope) {
        this(principalType, principalId, resource, action, scope, Map.of());
    }

    public static Rule scoped(
            String principalType,
            String principalId,
            String resource,
            String action,
            DataScopeKind kind,
            List<String> specifiedDepartmentIds,
            Map<String, FieldRule> fields
    ) {
        return new Rule(principalType, principalId, resource, action, kind, Predicate.TRUE,
                specifiedDepartmentIds, fields);
    }
}
