package one.nexus.permission.scope;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.RenderedSql;
import one.nexus.isolation.api.TableDescriptor;
import one.nexus.isolation.query.QueryCompiler;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.engine.Predicate;
import one.nexus.permission.support.PermissionException;

/**
 * Product list / detail opt-in: encode safe SELF/DEPT + FieldPolicy visibility
 * onto a Query AST, compile through {@link QueryCompiler}, and keep OR / IN /
 * {@code DEPT_AND_CHILDREN} leftovers on {@link PermissionQueryBinder}.
 * Mask / second-auth stay on {@code FieldPolicyProjector}.
 */
public final class QueryAstScopedQuery {
    private QueryAstScopedQuery() {}

    public record CompiledRead(
            QueryAstPolicyCompiler.Encoding encoding,
            RenderedSql compiled,
            BoundSql bound
    ) {
        public CompiledRead {
            Objects.requireNonNull(encoding, "encoding");
            Objects.requireNonNull(compiled, "compiled");
            Objects.requireNonNull(bound, "bound");
        }

        public boolean scopeFullyEncoded() {
            return encoding.scopeFullyEncoded();
        }
    }

    public static QueryAstPolicyCompiler.Encoding encode(
            QueryAst query, Decision decision, TableDescriptor table) {
        return QueryAstPolicyCompiler.encode(query, decision, table);
    }

    public static CompiledRead compile(
            QueryAst query,
            Decision decision,
            TableDescriptor table,
            QueryCompiler compiler,
            AccessContext context
    ) {
        Objects.requireNonNull(compiler, "compiler");
        QueryAstPolicyCompiler.Encoding encoding = encode(query, decision, table);
        RenderedSql compiled = compiler.compile(encoding.query(), context);
        return new CompiledRead(encoding, compiled, bindCompiled(compiled, encoding.leftoverScope()));
    }

    /**
     * Attach leftover DataScope beside QueryCompiler SQL. Inserts before
     * {@code ORDER BY} / {@code LIMIT} so paged list SQL stays valid.
     */
    public static BoundSql bindCompiled(RenderedSql compiled, Predicate leftoverScope) {
        Objects.requireNonNull(compiled, "compiled");
        if (compiled.sql() == null || !compiled.sql().toLowerCase(Locale.ROOT).contains("tenant_id")) {
            throw PermissionException.tenantPredicateRequired("QueryCompiler SQL missing tenant_id");
        }
        Predicate leftover = leftoverScope == null ? Predicate.TRUE : leftoverScope;
        if (leftover.equals(Predicate.TRUE)) {
            return new BoundSql(compiled.sql(), compiled.parameters());
        }
        BoundSql extra = PredicateSqlCompiler.compile(leftover);
        String text = compiled.sql();
        String lower = text.toLowerCase(Locale.ROOT);
        int order = lower.indexOf(" order by ");
        int limit = lower.indexOf(" limit ");
        int cut = cutBeforeTail(order, limit);
        String next = cut >= 0
                ? text.substring(0, cut) + " AND (" + extra.sql() + ")" + text.substring(cut)
                : text + " AND (" + extra.sql() + ")";
        List<Object> params = new ArrayList<>(compiled.parameters());
        boolean hasLimitOffset = limit >= 0 && lower.contains(" limit ?");
        if (hasLimitOffset && params.size() >= 2) {
            params.addAll(params.size() - 2, extra.parameters());
        } else {
            params.addAll(extra.parameters());
        }
        if (!next.toLowerCase(Locale.ROOT).contains("tenant_id")) {
            throw PermissionException.tenantPredicateRequired("DataScope stripped tenant_id");
        }
        return new BoundSql(next, params);
    }

    /**
     * {@code COUNT(*)} is not a Query AST node. Wrap a compiled SELECT that
     * already carries {@code tenant_id} so list total shares the same filter.
     */
    public static BoundSql count(BoundSql compiledSelect) {
        Objects.requireNonNull(compiledSelect, "compiledSelect");
        if (compiledSelect.sql() == null
                || !compiledSelect.sql().toLowerCase(Locale.ROOT).contains("tenant_id")) {
            throw PermissionException.tenantPredicateRequired("scoped count missing tenant_id");
        }
        return new BoundSql(
                "SELECT count(*) FROM (" + compiledSelect.sql() + ") _scoped",
                compiledSelect.parameters());
    }

    private static int cutBeforeTail(int order, int limit) {
        if (order >= 0 && (limit < 0 || order < limit)) {
            return order;
        }
        return limit;
    }
}
