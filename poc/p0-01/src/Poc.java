import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Deliberately small, single-table P0 prototype. No public raw SQL entry point. */
public final class Poc {
    private Poc() {}
    private static final Pattern IDENT = Pattern.compile("[a-z_][a-z0-9_]*");

    public enum Scope { TENANT, PLATFORM }
    public enum Kind { SELECT, INSERT, UPDATE, DELETE }

    public static final class Table {
        public final String name;
        public final Scope scope;
        public final Set<String> columns;
        private Table(String name, Scope scope, Set<String> columns) {
            this.name = identifier(name);
            this.scope = scope;
            this.columns = Collections.unmodifiableSet(new LinkedHashSet<String>(columns));
            for (String column : columns) identifier(column);
            if (scope == Scope.TENANT && !columns.contains("tenant_id")) throw new IllegalArgumentException("tenant_id column required");
        }
    }

    public static final class TableRegistry {
        private final Map<String, Table> tables = new LinkedHashMap<String, Table>();
        public void register(String name, Scope scope, String... columns) {
            if (tables.containsKey(name)) throw new IllegalArgumentException("duplicate table");
            tables.put(name, new Table(name, scope, new LinkedHashSet<String>(Arrays.asList(columns))));
        }
        private Table get(String name) {
            Table table = tables.get(name);
            if (table == null) throw new SecurityException("unregistered table");
            return table;
        }
    }

    public interface Context {}
    public static final class TenantContext implements Context {
        public final String tenantId;
        public TenantContext(String tenantId) {
            if (tenantId == null || tenantId.trim().isEmpty()) throw new IllegalArgumentException("tenant id required");
            this.tenantId = tenantId;
        }
    }
    public static final class PlatformContext implements Context {
        public final String path, actor, reason, traceId;
        private PlatformContext(String path, String actor, String reason, String traceId) {
            this.path = path; this.actor = actor; this.reason = reason; this.traceId = traceId;
        }
    }
    public interface AuditSink { void record(String event); }
    public static final class PlatformContextIssuer {
        private final Set<String> allowedPaths;
        private final AuditSink audit;
        public PlatformContextIssuer(Set<String> allowedPaths, AuditSink audit) {
            this.allowedPaths = new LinkedHashSet<String>(allowedPaths); this.audit = audit;
        }
        public PlatformContext issue(String path, String actor, String reason, String traceId) {
            if (!allowedPaths.contains(path)) throw new SecurityException("platform path is not whitelisted");
            required(actor); required(reason); required(traceId);
            audit.record("PLATFORM_CONTEXT actor=" + actor + " path=" + path + " trace=" + traceId + " reason=" + reason);
            return new PlatformContext(path, actor, reason, traceId);
        }
    }

    public static final class Query {
        public final Kind kind;
        public final String table;
        public final List<String> projection;
        public final Map<String, Object> values;
        public final Map<String, Object> filters;
        private Query(Kind kind, String table, List<String> projection, Map<String, Object> values, Map<String, Object> filters) {
            this.kind = kind; this.table = table;
            this.projection = Collections.unmodifiableList(new ArrayList<String>(projection));
            this.values = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(values));
            this.filters = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(filters));
        }
        public static Query select(String table, List<String> projection, Map<String, Object> filters) {
            return new Query(Kind.SELECT, table, projection, Collections.<String, Object>emptyMap(), filters);
        }
        public static Query insert(String table, Map<String, Object> values) {
            return new Query(Kind.INSERT, table, Collections.<String>emptyList(), values, Collections.<String, Object>emptyMap());
        }
        public static Query update(String table, Map<String, Object> values, Map<String, Object> filters) {
            return new Query(Kind.UPDATE, table, Collections.<String>emptyList(), values, filters);
        }
        public static Query delete(String table, Map<String, Object> filters) {
            return new Query(Kind.DELETE, table, Collections.<String>emptyList(), Collections.<String, Object>emptyMap(), filters);
        }
    }

    public static final class TableAccessPlan {
        public final String table;
        public final Scope scope;
        public final Kind kind;
        public final boolean tenantRequired;
        public final String tenantSource;
        public final Set<String> writeColumns;
        public final boolean platformContext;
        private TableAccessPlan(Table table, Query query, Context context) {
            this.table = table.name; this.scope = table.scope; this.kind = query.kind;
            this.tenantRequired = table.scope == Scope.TENANT;
            this.tenantSource = context instanceof TenantContext ? "TenantContext" : "PlatformContext";
            this.writeColumns = Collections.unmodifiableSet(new LinkedHashSet<String>(query.values.keySet()));
            this.platformContext = context instanceof PlatformContext;
        }
    }
    public static final class RenderedSql {
        public final String sql;
        public final List<Object> parameters;
        public final TableAccessPlan plan;
        private RenderedSql(String sql, List<Object> parameters, TableAccessPlan plan) {
            this.sql = sql;
            this.parameters = Collections.unmodifiableList(new ArrayList<Object>(parameters));
            this.plan = plan;
        }
    }

    public static final class Executor {
        private final TableRegistry registry;
        private final Set<String> platformPaths;
        public Executor(TableRegistry registry, Set<String> platformPaths) {
            this.registry = registry; this.platformPaths = new LinkedHashSet<String>(platformPaths);
        }
        public RenderedSql compile(Query input, Context context) {
            Table table = registry.get(input.table);
            validateColumns(input, table);
            Query injected = TenantInjector.inject(input, table, context);
            TableAccessPlan plan = new TableAccessPlan(table, injected, context);
            guard(plan, injected, context);
            return PostgresRenderer.render(injected, table, plan);
        }
        private void guard(TableAccessPlan plan, Query query, Context context) {
            if (context == null) throw new SecurityException("missing context");
            if (context instanceof PlatformContext) {
                PlatformContext p = (PlatformContext) context;
                if (!platformPaths.contains(p.path)) throw new SecurityException("platform path revoked");
                required(p.actor); required(p.reason); required(p.traceId);
            } else if (!(context instanceof TenantContext)) throw new SecurityException("invalid context");
            if (plan.scope == Scope.PLATFORM && !plan.platformContext) throw new SecurityException("platform table requires platform context");
            if (plan.tenantRequired && context instanceof TenantContext) {
                String tenantId = ((TenantContext) context).tenantId;
                if (query.kind == Kind.INSERT && !tenantId.equals(query.values.get("tenant_id"))) throw new SecurityException("cross-tenant insert");
                if (query.kind != Kind.INSERT && !tenantId.equals(query.filters.get("tenant_id"))) throw new SecurityException("tenant predicate absent");
            }
        }
        private void validateColumns(Query q, Table t) {
            for (String c : q.projection) requireColumn(t, c);
            for (String c : q.values.keySet()) requireColumn(t, c);
            for (String c : q.filters.keySet()) requireColumn(t, c);
            if (q.kind == Kind.SELECT && q.projection.isEmpty()) throw new IllegalArgumentException("explicit projection required");
            if ((q.kind == Kind.INSERT || q.kind == Kind.UPDATE) && q.values.isEmpty()) throw new IllegalArgumentException("write values required");
            if (q.kind != Kind.INSERT && q.kind != Kind.SELECT && q.filters.isEmpty()) throw new SecurityException("unbounded write forbidden");
            if (q.kind != Kind.INSERT && q.values.containsKey("tenant_id")) throw new SecurityException("tenant_id immutable");
            if (q.filters.containsKey("tenant_id") && q.filters.get("tenant_id") == null) throw new SecurityException("null tenant filter");
        }
        private void requireColumn(Table t, String c) {
            if (!t.columns.contains(c)) throw new SecurityException("unregistered column");
        }
    }

    /** Infrastructure-owned native templates only. Callers cannot supply SQL or table names. */
    public static final class NativeSqlPort {
        private final Executor executor;
        private final Map<String, String> countTemplates;
        public NativeSqlPort(Executor executor, Map<String, String> countTemplates) {
            this.executor = executor;
            this.countTemplates = Collections.unmodifiableMap(new LinkedHashMap<String, String>(countTemplates));
        }
        public RenderedSql compileCount(String templateId, Context context) {
            String table = countTemplates.get(templateId);
            if (table == null) throw new SecurityException("native template is not whitelisted");
            // Derive the access plan from the same guarded AST path. A tenant predicate
            // is mandatory for tenant callers; platform access uses its audited context.
            RenderedSql checked = executor.compile(Query.select(table, Collections.singletonList("id"),
                Collections.<String, Object>emptyMap()), context);
            String sql = "SELECT count(*) FROM " + quote(checked.plan.table);
            if (checked.plan.tenantRequired && !checked.plan.platformContext) {
                sql += " WHERE \"tenant_id\"::text = ?";
                if (checked.parameters.size() != 1) throw new SecurityException("native tenant binding mismatch");
            } else if (!checked.parameters.isEmpty()) {
                throw new SecurityException("unexpected native parameters");
            }
            return new RenderedSql(sql, checked.parameters, checked.plan);
        }
    }

    private static final class TenantInjector {
        private static Query inject(Query q, Table table, Context context) {
            if (context == null) throw new SecurityException("missing context");
            if (table.scope != Scope.TENANT || context instanceof PlatformContext) return q;
            if (!(context instanceof TenantContext)) throw new SecurityException("invalid context");
            String tenantId = ((TenantContext) context).tenantId;
            if (q.kind == Kind.INSERT) {
                if (q.values.containsKey("tenant_id") && !tenantId.equals(q.values.get("tenant_id"))) throw new SecurityException("cross-tenant insert");
                Map<String, Object> values = new LinkedHashMap<String, Object>(q.values);
                values.put("tenant_id", tenantId);
                return new Query(q.kind, q.table, q.projection, values, q.filters);
            }
            if (q.filters.containsKey("tenant_id") && !tenantId.equals(q.filters.get("tenant_id"))) throw new SecurityException("cross-tenant access");
            Map<String, Object> filters = new LinkedHashMap<String, Object>(q.filters);
            filters.put("tenant_id", tenantId);
            return new Query(q.kind, q.table, q.projection, q.values, filters);
        }
    }

    private static final class PostgresRenderer {
        private static RenderedSql render(Query q, Table t, TableAccessPlan plan) {
            List<Object> args = new ArrayList<Object>();
            StringBuilder sql = new StringBuilder();
            if (q.kind == Kind.SELECT) {
                sql.append("SELECT "); appendNames(sql, q.projection); sql.append(" FROM ").append(quote(t.name));
                where(sql, q.filters, args);
            } else if (q.kind == Kind.INSERT) {
                sql.append("INSERT INTO ").append(quote(t.name)).append(" (");
                appendNames(sql, q.values.keySet()); sql.append(") VALUES (");
                placeholders(sql, q.values.values(), args); sql.append(')');
            } else if (q.kind == Kind.UPDATE) {
                sql.append("UPDATE ").append(quote(t.name)).append(" SET ");
                int i = 0;
                for (Map.Entry<String, Object> entry : q.values.entrySet()) {
                    if (i++ > 0) sql.append(", ");
                    sql.append(quote(entry.getKey())).append(" = ?"); args.add(entry.getValue());
                }
                where(sql, q.filters, args);
            } else {
                sql.append("DELETE FROM ").append(quote(t.name)); where(sql, q.filters, args);
            }
            return new RenderedSql(sql.toString(), args, plan);
        }
        private static void where(StringBuilder sql, Map<String, Object> filters, List<Object> args) {
            if (filters.isEmpty()) return;
            sql.append(" WHERE "); int i = 0;
            for (Map.Entry<String, Object> entry : filters.entrySet()) {
                if (i++ > 0) sql.append(" AND ");
                sql.append(quote(entry.getKey()));
                if (entry.getValue() == null) sql.append(" IS NULL");
                else { sql.append(" = ?"); args.add(entry.getValue()); }
            }
        }
        private static void appendNames(StringBuilder sql, Iterable<String> names) {
            int i = 0; for (String name : names) { if (i++ > 0) sql.append(", "); sql.append(quote(name)); }
        }
        private static void placeholders(StringBuilder sql, Iterable<Object> values, List<Object> args) {
            int i = 0; for (Object value : values) { if (i++ > 0) sql.append(", "); sql.append('?'); args.add(value); }
        }
    }
    private static String quote(String name) { return '"' + identifier(name) + '"'; }
    private static String identifier(String name) {
        if (name == null || !IDENT.matcher(name).matches()) throw new SecurityException("invalid SQL identifier");
        return name;
    }
    private static void required(String value) {
        if (value == null || value.trim().isEmpty()) throw new SecurityException("actor, reason and trace required");
    }
}
