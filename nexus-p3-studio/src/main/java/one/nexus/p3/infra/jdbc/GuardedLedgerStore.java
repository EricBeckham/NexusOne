package one.nexus.p3.infra.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.postgresql.util.PGobject;
import org.springframework.stereotype.Component;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.p3.domain.FieldType;
import one.nexus.p3.domain.LedgerDefinition;
import one.nexus.p3.domain.LedgerField;
import one.nexus.p3.domain.LedgerIdentifiers;
import one.nexus.p3.domain.P3Exception;
import one.nexus.p3.domain.SystemColumns;
import one.nexus.p3.port.DataScopeHook;
import one.nexus.p3.port.DataScopeHook.ScopedRead;
import one.nexus.permission.scope.BoundSql;
import one.nexus.permission.scope.ScopedQuery;

/**
 * Ledger row access. Writes go through Query AST + GuardedJdbc. Reads use the
 * M0-05 scoped WHERE (tenant_id first) so list/detail/total share one filter.
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
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put(SystemColumns.ID, id);
        if (expectedVersion != null) {
            filters.put(SystemColumns.VERSION, expectedVersion);
        }
        // version bump via read-modify: Query AST equality only, so encode next version in values
        Map<String, Object> current = detail(ledger, fields, id);
        long nextVersion = ((Number) current.get(SystemColumns.VERSION)).longValue() + 1;
        if (expectedVersion != null && ((Number) current.get(SystemColumns.VERSION)).longValue() != expectedVersion) {
            throw P3Exception.recordConflict("stale record version");
        }
        patch.put(SystemColumns.VERSION, nextVersion);
        int updated = executeWrite(QueryAst.update(ledger.physicalTable(), patch, filters));
        if (updated == 0) {
            throw P3Exception.recordNotFound(id);
        }
        return detail(ledger, fields, id);
    }

    public void softDelete(LedgerDefinition ledger, UUID id) {
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
        BoundSql bound = scoped.query().detail(ledger.physicalTable(), projection(fields), SystemColumns.ID, id);
        BoundSql filtered = andDeletedNull(bound, false);
        List<Map<String, Object>> rows = executeRead(filtered);
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
        ScopedQuery query = scoped.query();
        BoundSql count = andDeletedNull(andEquals(query.count(ledger.physicalTable()), equalsFilters, fields), false);
        Map<String, Object> countRow = executeRead(count).getFirst();
        Object countValue = countRow.get("count");
        if (countValue == null) {
            countValue = countRow.get("COUNT");
        }
        long total = ((Number) countValue).longValue();
        BoundSql list = andDeletedNull(
                andEquals(query.list(ledger.physicalTable(), projection(fields), SystemColumns.ID, size, page * size),
                        equalsFilters, fields),
                true);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : executeRead(list)) {
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

    private List<Map<String, Object>> executeRead(BoundSql sql) {
        if (sql.sql() == null || !sql.sql().toLowerCase(Locale.ROOT).contains("tenant_id")) {
            throw P3Exception.missingTenant();
        }
        Connection connection = SearchPathConnections.open(dataSource);
        try (PreparedStatement statement = connection.prepareStatement(sql.sql())) {
            List<Object> params = sql.parameters();
            for (int i = 0; i < params.size(); i++) {
                statement.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                ResultSetMetaData meta = rs.getMetaData();
                int columns = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columns; i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
                return rows;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("ledger read failed", ex);
        } finally {
            SearchPathConnections.release(dataSource, connection);
        }
    }

    private Map<String, Object> requireTenantRow(Map<String, Object> row) {
        String expected = requireTenant();
        Object actual = row.get(SystemColumns.TENANT_ID);
        if (actual != null && !expected.equals(String.valueOf(actual))) {
            throw P3Exception.missingTenant();
        }
        return row;
    }

    private static String requireTenant() {
        return AccessContextHolder.current().boundTenantId().orElseThrow(P3Exception::missingTenant);
    }

    private static List<String> projection(List<LedgerField> fields) {
        List<String> columns = new ArrayList<>(SystemColumns.ALL);
        fields.forEach(field -> columns.add(field.fieldCode()));
        return columns;
    }

    private static BoundSql andDeletedNull(BoundSql sql, boolean hasLimit) {
        String text = sql.sql();
        String extra = " AND deleted_at IS NULL";
        String next;
        if (hasLimit) {
            int order = text.toLowerCase(Locale.ROOT).indexOf(" order by ");
            next = text.substring(0, order) + extra + text.substring(order);
        } else {
            next = text + extra;
        }
        if (!next.toLowerCase(Locale.ROOT).contains("tenant_id")) {
            throw P3Exception.missingTenant();
        }
        return new BoundSql(next, sql.parameters());
    }

    private static BoundSql andEquals(BoundSql sql, Map<String, Object> equalsFilters, List<LedgerField> fields) {
        if (equalsFilters == null || equalsFilters.isEmpty()) {
            return sql;
        }
        StringBuilder extra = new StringBuilder();
        List<Object> params = new ArrayList<>(sql.parameters());
        Map<String, LedgerField> byCode = new LinkedHashMap<>();
        fields.forEach(field -> byCode.put(field.fieldCode(), field));
        equalsFilters.forEach((name, value) -> {
            LedgerIdentifiers.requireFieldCode(name);
            if (!byCode.containsKey(name)) {
                throw P3Exception.queryInvalid("unknown query field: " + name);
            }
            extra.append(" AND ").append(name).append(" = ?");
            params.add(coerce(byCode.get(name).fieldType(), value));
        });
        String text = sql.sql();
        int order = text.toLowerCase(Locale.ROOT).indexOf(" order by ");
        String next = order >= 0
                ? text.substring(0, order) + extra + text.substring(order)
                : text + extra;
        if (!next.toLowerCase(Locale.ROOT).contains("tenant_id")) {
            throw P3Exception.missingTenant();
        }
        return new BoundSql(next, params);
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
