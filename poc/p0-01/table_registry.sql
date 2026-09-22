-- Prototype metadata for tenant-scoped and platform-owned business tables.
CREATE TABLE IF NOT EXISTS table_registry (
    table_name text PRIMARY KEY,
    scope text NOT NULL CHECK (scope IN ('tenant', 'platform', 'app_shared')),
    tenant_required boolean NOT NULL,
    schema_version integer NOT NULL CHECK (schema_version > 0),
    owner_module text NOT NULL,
    CONSTRAINT registry_scope_consistent CHECK (
        (scope = 'tenant' AND tenant_required)
        OR (scope <> 'tenant' AND NOT tenant_required)
    )
);
