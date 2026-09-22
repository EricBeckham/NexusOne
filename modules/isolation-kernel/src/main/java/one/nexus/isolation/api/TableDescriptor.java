package one.nexus.isolation.api;

import java.util.Objects;
import java.util.Set;

public final class TableDescriptor {
    private final String name;
    private final TableScope scope;
    private final Set<String> columns;
    private final String ownerModule;
    private final int schemaVersion;

    public TableDescriptor(String name, TableScope scope, Set<String> columns, String ownerModule, int schemaVersion) {
        this.name = Objects.requireNonNull(name, "name");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.columns = Set.copyOf(columns);
        this.ownerModule = Actor.requireToken(ownerModule, "ownerModule");
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        this.schemaVersion = schemaVersion;
        if (scope.tenantRequired() && !this.columns.contains("tenant_id")) {
            throw new IllegalArgumentException("tenant_id column required for tenant-scoped tables");
        }
        if (!scope.tenantRequired() && this.columns.contains("tenant_id")) {
            throw new IllegalArgumentException("platform tables must not declare tenant_id");
        }
    }

    public String name() {
        return name;
    }

    public TableScope scope() {
        return scope;
    }

    public Set<String> columns() {
        return columns;
    }

    public String ownerModule() {
        return ownerModule;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public boolean tenantRequired() {
        return scope.tenantRequired();
    }
}
