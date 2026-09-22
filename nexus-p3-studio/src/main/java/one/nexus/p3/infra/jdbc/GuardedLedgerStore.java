package one.nexus.p3.infra.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.postgresql.util.PGobject;
import org.springframework.stereotype.Component;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.QueryPage;
import one.nexus.isolation.api.QuerySort;
import one.nexus.isolation.api.RenderedSql;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.p3.domain.FieldType;
import one.nexus.p3.domain.LedgerDefinition;
import one.nexus.p3.domain.LedgerField;
import one.nexus.p3.domain.LedgerIdentifiers;
import one.nexus.p3.domain.P3ErrorCodes;
import one.nexus.p3.domain.P3Exception;
import one.nexus.p3.domain.SystemColumns;
import one.nexus.p3.port.DataScopeHook;
import one.nexus.p3.port.DataScopeHook.ScopedRead;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.scope.QueryAstScopedQuery;

/**
 * Ledger row access. Writes go through Query AST + GuardedJdbc. List / detail
 * / count opt into {@link QueryAstScopedQuery} so SAFE SELF/DEPT + single-column
 * multi-value {@code IN} + FieldPolicy visibility compile through
 * {@code QueryCompiler}. Mixed-column OR (SELF∨DEPT) leftovers still bind
 * beside the AST, then execute as leftover-bound {@link RenderedSql} through
 * {@code kernel.jdbc()} — raw {@code BoundSql} cannot bypass DataScope.
 * Mask / second-auth stay on {@code FieldPolicyProjector}.
 */
@Component
public class GuardedLedgerStore {
    private final DataSource dataSource;
    private final IsolationKernel kernel;
    private final DataScopeHook dataScope;

    public GuardedLedgerStore(DataSource dataSource, IsolationKernel kernel, DataScopeHook dataScope) {
        this.dataSource = dataSource;
        this.kernel = kernel;
        this.dataScope = dataScope;
    }

    public UUID insert(LedgerDefinition ledger, List<LedgerField> fields, Map<String, Object> values) {
        requireWrite(values);
        AccessContext context = AccessContextHolder.current();
        Map<String, Object> row = new LinkedHashMap<>();
        UUID id = values.get(SystemColumns.ID) instanceof UUID uuid
                ? uuid
                : values.get(SystemColumns.ID) != null
                        ? UUID.fromString(String.valueOf(values.get(SystemColumns.ID)))
                        : one.nexus.common.id.UuidV7.generate();
        row.put(SystemColumns.ID, id);
        row.put(SystemColumns.OWNER_ID, stringOrNull(values.get(SystemColumns.OWNER_ID)));
        row.put(SystemColumns.DEPARTMENT_ID, stringOrNull(values.get(SystemColumns.DEPARTMENT_ID)));
        Instant now = Instant.now();
        row.put(SystemColumns.CREATED_AT, Timestamp.from(now));
        row.put(SystemColumns.CREATED_BY, context.actor().actorId());
        row.put(SystemColumns.UPDATED_AT, Timestamp.from(now));
        row.put(SystemColumns.UPDATED_BY, context.actor().actorId());
        row.put(SystemColumns.VERSION, 1L);
        row.put(SystemColumns.SCHEMA_VERSION, ledger.schemaVersion());
        row.put(SystemColumns.EXT_DATA, jsonb("{}"));
        putBusiness(row, fields, values, true);
        executeWrite(QueryAst.insert(ledger.physicalTable(), row));
        return id;
    }

    public Map<String, Object> update(
            LedgerDefinition ledger, List<LedgerField> fields, UUID id, Map<String, Object> values, Long expectedVersion) {
        requireWrite(values);
        AccessContext context = AccessContextHolder.current();
        Map<String, Object> patch = new LinkedHashMap<>();
        putBusiness(patch, fields, values, false);
        if (values.containsKey(SystemColumns.OWNER_ID)) {
            patch.put(SystemColumns.OWNER_ID, stringOrNull(values.get(SystemColumns.OWNER_ID)));
        }
        if (values.containsKey(SystemColumns.DEPARTMENT_ID)) {
            patch.put(SystemColumns.DEPARTMENT_ID, stringOrNull(values.get(SystemColumns.DEPARTMENT_ID)));
        }
        patch.put(SystemColumns.UPDATED_AT, Timestamp.from(Instant.now()));
        patch.put(SystemColumns.UPDATED_BY, context.actor().actorId());
        Map<String, Object> current = detail(ledger, fields, id);
        long currentVersion = asVersion(current.get(SystemColumns.VERSION));
        if (expectedVersion != null && currentVersion != expectedVersion) {
            throw P3Exception.recordConflict(
                    "stale record version",
                    Map.of("expected", expectedVersion, "actual", currentVersion, "id", id.toString()));
        }
        long lockVersion = expectedVersion != null ? expectedVersion : currentVersion;
        long nextVersion = currentVersion + 1;
        patch.put(SystemColumns.VERSION, nextVersion);
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put(SystemColumns.ID, id);
        filters.put(SystemColumns.VERSION, lockVersion);
        int updated = executeWrite(QueryAst.update(ledger.physicalTable(), patch, filters));
        if (updated == 0) {
            Map<String, Object> latest;
            try {
                latest = detail(ledger, fields, id);
            } catch (P3Exception ex) {
                if (ex.errorCode().equals(P3ErrorCodes.RECORD_NOT_FOUND)) {
                    throw ex;
                }
                throw ex;
            }
            throw P3Exception.recordConflict(
                    "stale record version",
                    Map.of(
                            "expected",
                            lockVersion,
                            "actual",
                            asVersion(latest.get(SystemColumns.VERSION)),
                            "id",
                            id.toString()));
        }
        return detail(ledger, fields, id);
    }

    public void softDelete(LedgerDefinition ledger, UUID id) {
        requireWrite(Map.of());
        AccessContext context = AccessContextHolder.current();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(SystemColumns.DELETED_AT, Timestamp.from(Instant.now()));
        values.put(SystemColumns.UPDATED_AT, Timestamp.from(Instant.now()));
        values.put(SystemColumns.UPDATED_BY, context.actor().actorId());
        int updated = executeWrite(QueryAst.update(
                ledger.physicalTable(), values, Map.of(SystemColumns.ID, id)));
        if (updated == 0) {
            throw P3Exception.recordNotFound(id);
        }
    }

    public Map<String, Object> detail(LedgerDefinition ledger, List<LedgerField> fields, UUID id) {
        ScopedRead scoped = dataScope.bind(requireTenant(), "ledger", "read");
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put(SystemColumns.ID, id);
        filters.put(SystemColumns.DELETED_AT, null);
        QueryAst ast = QueryAst.select(ledger.physicalTable(), projection(fields), filters);
        List<Map<String, Object>> rows = executeEncoded(ast, scoped.decision());
        if (rows.isEmpty()) {
            throw P3Exception.recordNotFound(id);
        }
        return dataScope.project(requireTenantRow(rows.getFirst()), scoped.decision());
    }

    public Page list(
            LedgerDefinition ledger,
            List<LedgerField> fields,
            Map<String, Object> equalsFilters,
            int page,
            int size) {
        if (page < 0 || size <= 0 || size > 200) {
            throw P3Exception.queryInvalid("page must be >= 0 and size in 1..200");
        }
        ScopedRead scoped = dataScope.bind(requireTenant(), "ledger", "read");
        Map<String, Object> filters = readFilters(equalsFilters, fields);
        long total = readCount(compileRead(
                QueryAst.select(ledger.physicalTable(), List.of(SystemColumns.ID), filters),
                scoped.decision()));
        QueryAst pageAst = QueryAst.select(ledger.physicalTable(), projection(fields), filters)
                .withSorts(List.of(QuerySort.asc(SystemColumns.ID)))
                .withPage(QueryPage.of(size, page * size));
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : executeEncoded(pageAst, scoped.decision())) {
            items.add(dataScope.project(requireTenantRow(row), scoped.decision()));
        }
        return new Page(items, total, page, size);
    }

    public record Page(List<Map<String, Object>> items, long total, int page, int size) {}

    private int executeWrite(QueryAst query) {
        Connection connection = SearchPathConnections.open(dataSource);
        try {
            return kernel.jdbc().executeUpdate(connection, query);
        } catch (SQLException ex) {
            throw new IllegalStateException("ledger write failed", ex);
        } finally {
            SearchPathConnections.release(dataSource, connection);
        }
    }

    private List<Map<String, Object>> executeEncoded(QueryAst ast, Decision decision) {
        return executeGuarded(QueryAstScopedQuery.executed(compileRead(ast, decision)));
    }

    private QueryAstScopedQuery.CompiledRead compileRead(QueryAst ast, Decision decision) {
        return QueryAstScopedQuery.compile(
                ast,
                decision,
                kernel.registry().require(ast.table()),
                kernel.compiler(),
                AccessContextHolder.current());
    }

    private List<Map<String, Object>> executeGuarded(RenderedSql sql) {
        Connection connection = SearchPathConnections.open(dataSource);
        try {
            return kernel.jdbc().executeQuery(connection, sql);
        } catch (SQLException ex) {
            throw new IllegalStateException("ledger read failed", ex);
        } finally {
            SearchPathConnections.release(dataSource, connection);
        }
    }

    private long readCount(QueryAstScopedQuery.CompiledRead compiled) {
        Map<String, Object> countRow = executeGuarded(QueryAstScopedQuery.countExecuted(compiled)).getFirst();
        Object countValue = countRow.get("count");
        if (countValue == null) {
            countValue = countRow.get("COUNT");
        }
        return ((Number) countValue).longValue();
    }

    private Map<String, Object> requireTenantRow(Map<String, Object> row) {
        String expected = requireTenant();
        Object actual = row.get(SystemColumns.TENANT_ID);
        if (actual != null && !expected.equals(String.valueOf(actual))) {
            throw P3Exception.missingTenant();
        }
        return row;
    }

    private void requireWrite(Map<String, Object> values) {
        Decision decision = dataScope.require(requireTenant(), "ledger", "write");
        if (values == null || values.isEmpty()) {
            return;
        }
        List<String> names = new ArrayList<>();
        for (String key : values.keySet()) {
            if (!SystemColumns.ALL.contains(key)) {
                names.add(key);
            }
        }
        dataScope.requireWritableFields(decision, names);
    }

    private static String requireTenant() {
        return AccessContextHolder.current().boundTenantId().orElseThrow(P3Exception::missingTenant);
    }

    private static List<String> projection(List<LedgerField> fields) {
        List<String> columns = new ArrayList<>(SystemColumns.ALL);
        fields.forEach(field -> columns.add(field.fieldCode()));
        return columns;
    }

    private static Map<String, Object> readFilters(Map<String, Object> equalsFilters, List<LedgerField> fields) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put(SystemColumns.DELETED_AT, null);
        if (equalsFilters == null || equalsFilters.isEmpty()) {
            return filters;
        }
        Map<String, LedgerField> byCode = new LinkedHashMap<>();
        fields.forEach(field -> byCode.put(field.fieldCode(), field));
        equalsFilters.forEach((name, value) -> {
            LedgerIdentifiers.requireFieldCode(name);
            if (!byCode.containsKey(name)) {
                throw P3Exception.queryInvalid("unknown query field: " + name);
            }
            filters.put(name, coerce(byCode.get(name).fieldType(), value));
        });
        return filters;
    }

    private static void putBusiness(
            Map<String, Object> target, List<LedgerField> fields, Map<String, Object> values, boolean creating) {
        for (LedgerField field : fields) {
            Object raw = values.get(field.fieldCode());
            if (raw == null) {
                if (creating && field.required()) {
                    throw P3Exception.queryInvalid("missing required field: " + field.fieldCode());
                }
                continue;
            }
            target.put(field.fieldCode(), coerce(field.fieldType(), raw));
        }
        for (String key : values.keySet()) {
            if (SystemColumns.ALL.contains(key)) {
                continue;
            }
            boolean known = fields.stream().anyMatch(field -> field.fieldCode().equals(key));
            if (!known) {
                throw P3Exception.queryInvalid("unknown field: " + key);
            }
        }
    }

    private static Object coerce(FieldType type, Object raw) {
        if (raw == null) {
            return null;
        }
        return switch (type) {
            case TEXT -> String.valueOf(raw);
            case NUMERIC -> new java.math.BigDecimal(String.valueOf(raw));
            case INTEGER -> Long.parseLong(String.valueOf(raw));
            case BOOLEAN -> raw instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(raw));
            case TIMESTAMPTZ -> Timestamp.from(Instant.parse(String.valueOf(raw)));
        };
    }

    private static long asVersion(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw == null) {
            throw P3Exception.recordConflict("record is missing version");
        }
        return Long.parseLong(String.valueOf(raw));
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static PGobject jsonb(String json) {
        try {
            PGobject object = new PGobject();
            object.setType("jsonb");
            object.setValue(json);
            return object;
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
