package one.nexus.isolation.api;

import java.util.Set;

public final class TableAccessPlan {
    private final String table;
    private final TableScope scope;
    private final QueryKind kind;
    private final boolean tenantRequired;
    private final String tenantSource;
    private final Set<String> writeColumns;
    private final boolean platformContext;
    private final boolean dataScopeRequired;

    public TableAccessPlan(
            String table,
            TableScope scope,
            QueryKind kind,
            boolean tenantRequired,
            String tenantSource,
            Set<String> writeColumns,
            boolean platformContext,
            boolean dataScopeRequired
    ) {
        this.table = table;
        this.scope = scope;
        this.kind = kind;
        this.tenantRequired = tenantRequired;
        this.tenantSource = tenantSource;
        this.writeColumns = Set.copyOf(writeColumns);
        this.platformContext = platformContext;
        this.dataScopeRequired = dataScopeRequired;
    }

    public String table() {
        return table;
    }

    public TableScope scope() {
        return scope;
    }

    public QueryKind kind() {
        return kind;
    }

    public boolean tenantRequired() {
        return tenantRequired;
    }

    public String tenantSource() {
        return tenantSource;
    }

    public Set<String> writeColumns() {
        return writeColumns;
    }

    public boolean platformContext() {
        return platformContext;
    }

    public boolean dataScopeRequired() {
        return dataScopeRequired;
    }
}
