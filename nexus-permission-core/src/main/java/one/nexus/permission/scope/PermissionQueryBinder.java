package one.nexus.permission.scope;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.engine.Predicate;
import one.nexus.permission.support.PermissionException;

/**
 * Binds DataScope onto a tenant predicate. {@code tenant_id} is always the first
 * conjunct and cannot be stripped. list / detail / total / pagination share the
 * same WHERE so totals and pages cannot diverge from the filtered row set.
 * Safe SELF/DEPT equality atoms, single-column multi-value {@code IN} lists,
 * and mixed-column OR (SELF∨DEPT) can instead be encoded onto {@code QueryAst}
 * via {@link QueryAstPolicyCompiler}; this binder remains the unencodable
 * leftover path (empty {@code IN}, unsupported operators, AND-of-OR).
 */
public final class PermissionQueryBinder {
    private static final Set<String> IDENT = Set.of();

    private PermissionQueryBinder() {}

    public static ScopedQuery bind(String tenantId, Decision decision) {
        Objects.requireNonNull(tenantId, "tenantId");
        Predicate scope = decision != null && decision.allowed() ? decision.scope() : Predicate.FALSE;
        return bind(tenantId, scope);
    }

    public static ScopedQuery bind(String tenantId, Predicate scope) {
        Objects.requireNonNull(tenantId, "tenantId");
        BoundSql compiled = PredicateSqlCompiler.compile(scope == null ? Predicate.FALSE : scope);
        if (compiled.sql().toLowerCase(Locale.ROOT).contains("tenant_id") && !compiled.sql().equals("TRUE")) {
            // Scope may mention tenant_id only if it equals the bound tenant; isolation kernel rejects strip.
            throw PermissionException.tenantPredicateRequired("DataScope must not author tenant_id; TenantInjector owns it");
        }
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        params.addAll(compiled.parameters());
        String where = "tenant_id = ? AND (" + compiled.sql() + ")";
        return new ScopedQuery(where, List.copyOf(params));
    }

    /**
     * AND DataScope onto SQL that isolation kernel already rendered with tenant_id.
     * Rejects any attempt to drop or rewrite the tenant predicate.
     */
    public static BoundSql andPreserveTenant(String renderedSql, List<Object> renderedParams, Predicate scope) {
        if (renderedSql == null || !renderedSql.toLowerCase(Locale.ROOT).contains("tenant_id")) {
            throw PermissionException.tenantPredicateRequired("DataScope refused to attach to SQL without tenant_id");
        }
        BoundSql extra = PredicateSqlCompiler.compile(scope == null ? Predicate.TRUE : scope);
        List<Object> params = new ArrayList<>(renderedParams == null ? List.of() : renderedParams);
        params.addAll(extra.parameters());
        String sql = renderedSql + " AND (" + extra.sql() + ")";
        if (!sql.toLowerCase(Locale.ROOT).contains("tenant_id")) {
            throw PermissionException.tenantPredicateRequired("DataScope stripped tenant_id");
        }
        return new BoundSql(sql, params);
    }

    public static String requireIdent(String name) {
        if (name == null || !name.matches("[a-z_][a-z0-9_]*") || IDENT.contains(name)) {
            throw PermissionException.badRequest("P2.PERM.0008", "rejected identifier or operator");
        }
        return name;
    }
}
