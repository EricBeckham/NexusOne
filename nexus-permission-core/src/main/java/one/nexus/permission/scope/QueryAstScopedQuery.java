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
 * Product list / detail / count opt-in: encode safe SELF/DEPT + single-column
 * multi-value {@code IN} ({@code DEPT_AND_CHILDREN} / SPECIFIED) + mixed-column
 * OR (SELF∨DEPT) + FieldPolicy visibility onto a Query AST and compile through
 * {@link QueryCompiler}. Unencodable leftovers stay on {@link PermissionQueryBinder}.
 * Leftover BoundSql and {@code COUNT(*)} wrap must stay on that compiler SQL
 * (quoted {@code "tenant_id"}) so raw BoundSql cannot bypass DataScope.
 * Mask / second-auth stay on {@code FieldPolicyProjector}.
 */
public final class QueryAstScopedQuery {
    static final String COMPILER_TENANT = "\"tenant_id\"";

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

        /** Leftover-bound SQL that still carries the QueryCompiler access plan. */
        public RenderedSql executed() {
            return QueryAstScopedQuery.executed(this);
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
        requireCompilerTenant(compiled.sql(), "QueryCompiler SQL missing tenant_id");
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
        requireCompilerTenant(next, "DataScope stripped tenant_id");
        return new BoundSql(next, params);
    }

    /**
     * Execute leftover-bound (or fully encoded) SQL through GuardedJdbc.
     * Raw BoundSql that is not derived from this compile is rejected.
     */
    public static RenderedSql executed(CompiledRead compiled) {
        requireGuarded(compiled);
        return new RenderedSql(compiled.bound().sql(), compiled.bound().parameters(), compiled.compiled().plan());
    }

    /**
     * {@code COUNT(*)} is not a Query AST node. Wrap a compiled SELECT that
     * already carries QueryCompiler {@code "tenant_id"} so list total shares
     * the same filter and cannot start from raw BoundSql.
     */
    public static BoundSql count(CompiledRead compiledSelect) {
        RenderedSql executed = countExecuted(compiledSelect);
        return new BoundSql(executed.sql(), executed.parameters());
    }

    public static RenderedSql countExecuted(CompiledRead compiledSelect) {
        requireGuarded(compiledSelect);
        BoundSql wrap = wrapCount(compiledSelect.bound());
        requireCompilerTenant(wrap.sql(), "scoped count missing QueryCompiler tenant_id");
        return new RenderedSql(wrap.sql(), wrap.parameters(), compiledSelect.compiled().plan());
    }

    /**
     * Legacy wrap. Accepts only QueryCompiler-shaped SQL (quoted
     * {@code "tenant_id"}). Unquoted {@code tenant_id} BoundSql is the bypass.
     */
    public static BoundSql count(BoundSql compiledSelect) {
        Objects.requireNonNull(compiledSelect, "compiledSelect");
        requireCompilerTenant(compiledSelect.sql(), "scoped count missing QueryCompiler tenant_id");
        return wrapCount(compiledSelect);
    }

    static void requireGuarded(CompiledRead compiled) {
        Objects.requireNonNull(compiled, "compiled");
        requireCompilerTenant(compiled.compiled().sql(), "QueryCompiler SQL missing tenant_id");
        requireCompilerTenant(compiled.bound().sql(), "leftover BoundSql missing QueryCompiler tenant_id");
        if (compiled.scopeFullyEncoded()) {
            if (!compiled.compiled().sql().equals(compiled.bound().sql())) {
                throw PermissionException.tenantPredicateRequired(
                        "fully encoded BoundSql diverged from QueryCompiler");
            }
            return;
        }
        String compiledCore = stripTail(compiled.compiled().sql());
        if (!compiled.bound().sql().startsWith(compiledCore)) {
            throw PermissionException.tenantPredicateRequired("leftover BoundSql replaced QueryCompiler SQL");
        }
        if (!compiled.bound().sql().contains(" AND (")) {
            throw PermissionException.tenantPredicateRequired("leftover DataScope was dropped");
        }
    }

    static void requireCompilerTenant(String sql, String reason) {
        if (sql == null || !sql.contains(COMPILER_TENANT)) {
            throw PermissionException.tenantPredicateRequired(reason);
        }
    }

    private static BoundSql wrapCount(BoundSql compiledSelect) {
        return new BoundSql(
                "SELECT count(*) FROM (" + compiledSelect.sql() + ") _scoped",
                compiledSelect.parameters());
    }

    private static String stripTail(String sql) {
        String lower = sql.toLowerCase(Locale.ROOT);
        int cut = cutBeforeTail(lower.indexOf(" order by "), lower.indexOf(" limit "));
        return cut >= 0 ? sql.substring(0, cut) : sql;
    }

    private static int cutBeforeTail(int order, int limit) {
        if (order >= 0 && (limit < 0 || order < limit)) {
            return order;
        }
        return limit;
    }
}
