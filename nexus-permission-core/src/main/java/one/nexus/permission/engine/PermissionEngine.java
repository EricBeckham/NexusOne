package one.nexus.permission.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * P0-02 evaluate() port. Order is TenantBoundary → Explicit Deny → grant union
 * → DataScope OR record-share, AND context → FieldPolicy merge.
 */
public final class PermissionEngine {
    private PermissionEngine() {}

    public static boolean can(Subject subject, String resource, String action, PolicySnapshot snapshot) {
        return evaluate(subject, subject.tenantId(), resource, action, snapshot).allowed();
    }

    public static Decision evaluate(
            Subject subject,
            String resourceTenantId,
            String resource,
            String action,
            PolicySnapshot snapshot
    ) {
        return evaluate(subject, resourceTenantId, resource, action, snapshot, List.of(), Predicate.TRUE,
                DepartmentCatalog.EMPTY);
    }

    public static Decision evaluate(
            Subject subject,
            String resourceTenantId,
            String resource,
            String action,
            PolicySnapshot snapshot,
            List<Predicate> recordShares,
            Predicate context
    ) {
        return evaluate(subject, resourceTenantId, resource, action, snapshot, recordShares, context,
                DepartmentCatalog.EMPTY);
    }

    public static Decision evaluate(
            Subject subject,
            String resourceTenantId,
            String resource,
            String action,
            PolicySnapshot snapshot,
            List<Predicate> recordShares,
            Predicate context,
            DepartmentCatalog catalog
    ) {
        if (!subject.tenantId().equals(resourceTenantId) || !snapshot.tenantId().equals(subject.tenantId())) {
            return new Decision(false, "tenant_boundary");
        }
        Set<Principal> principals = principals(subject, snapshot);
        List<Rule> denies = snapshot.denies().stream()
                .filter(rule -> matches(rule, resource, action, principals))
                .toList();
        if (!denies.isEmpty()) {
            return new Decision(false, "explicit_deny", Predicate.FALSE, Map.of(), snapshot.version());
        }
        List<Rule> grants = snapshot.grants().stream()
                .filter(rule -> matches(rule, resource, action, principals))
                .toList();
        if (grants.isEmpty()) {
            return new Decision(false, "no_grant", Predicate.FALSE, Map.of(), snapshot.version());
        }
        List<Predicate> grantScopes = new ArrayList<>();
        for (Rule rule : grants) {
            grantScopes.add(DataScopeCompiler.scopeFor(rule, subject, catalog == null ? DepartmentCatalog.EMPTY : catalog));
        }
        List<Predicate> orArgs = new ArrayList<>(grantScopes);
        if (recordShares != null) {
            orArgs.addAll(recordShares);
        }
        Predicate scope = Predicate.allOf(Predicate.anyOf(orArgs), context == null ? Predicate.TRUE : context);
        if ("false".equals(scope.op())) {
            return new Decision(false, "empty_scope", Predicate.FALSE, Map.of(), snapshot.version());
        }
        Set<String> fieldNames = new LinkedHashSet<>();
        for (Rule rule : grants) {
            fieldNames.addAll(rule.fields().keySet());
        }
        Map<String, FieldRule> fields = new LinkedHashMap<>();
        for (String name : fieldNames) {
            List<FieldRule> rules = new ArrayList<>();
            for (Rule rule : grants) {
                FieldRule field = rule.fields().get(name);
                if (field != null) {
                    rules.add(field);
                }
            }
            fields.put(name, FieldRule.merge(rules));
        }
        return new Decision(true, "allowed", scope, fields, snapshot.version());
    }

    static Set<Principal> principals(Subject subject, PolicySnapshot snapshot) {
        Set<String> roles = new HashSet<>(subject.roles());
        List<String> pending = new ArrayList<>(roles);
        while (!pending.isEmpty()) {
            String role = pending.removeLast();
            for (String parent : snapshot.roleParents().getOrDefault(role, Set.of())) {
                if (roles.add(parent)) {
                    pending.add(parent);
                }
            }
        }
        Set<Principal> out = new LinkedHashSet<>();
        out.add(new Principal("user", subject.userId()));
        roles.forEach(role -> out.add(new Principal("role", role)));
        subject.positions().forEach(position -> out.add(new Principal("position", position)));
        subject.departments().forEach(department -> out.add(new Principal("department", department)));
        return out;
    }

    private static boolean matches(Rule rule, String resource, String action, Set<Principal> principals) {
        return rule.resource().equals(resource)
                && rule.action().equals(action)
                && principals.contains(new Principal(rule.principalType(), rule.principalId()));
    }

    record Principal(String type, String id) {}
}
