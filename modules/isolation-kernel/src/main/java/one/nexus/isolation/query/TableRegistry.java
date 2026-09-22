package one.nexus.isolation.query;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;
import one.nexus.isolation.api.TableDescriptor;
import one.nexus.isolation.api.TableScope;

public final class TableRegistry {
    private final Map<String, TableDescriptor> tables = new LinkedHashMap<>();
    private boolean frozen;

    public TableRegistry register(TableDescriptor table) {
        Objects.requireNonNull(table, "table");
        if (frozen) {
            throw new IllegalStateException("registry is frozen");
        }
        if (tables.containsKey(table.name())) {
            throw new IllegalArgumentException("duplicate table: " + table.name());
        }
        Identifiers.require(table.name());
        table.columns().forEach(Identifiers::require);
        tables.put(table.name(), table);
        return this;
    }

    public TableRegistry registerTenant(String name, String ownerModule, int schemaVersion, String... columns) {
        return register(new TableDescriptor(name, TableScope.TENANT, java.util.Set.of(columns), ownerModule, schemaVersion));
    }

    public TableRegistry registerPlatform(String name, String ownerModule, int schemaVersion, String... columns) {
        return register(new TableDescriptor(name, TableScope.PLATFORM, java.util.Set.of(columns), ownerModule, schemaVersion));
    }

    public TableRegistry freeze() {
        frozen = true;
        return this;
    }

    public TableDescriptor require(String name) {
        TableDescriptor table = tables.get(name);
        if (table == null) {
            throw new IsolationException(IsolationErrorCode.UNREGISTERED_TABLE, "missing-trace");
        }
        return table;
    }

    public Map<String, TableDescriptor> snapshot() {
        return Map.copyOf(tables);
    }
}
