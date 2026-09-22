-- Flyway-ready contribution for M0-01. Isolation kernel registers in-memory at startup;
-- product DDL must keep this table in the same transaction as schema changes (P0-08/M0-06).
CREATE TABLE IF NOT EXISTS table_registry (
    table_name text PRIMARY KEY,
    scope text NOT NULL CHECK (scope IN ('tenant', 'platform', 'app_shared')),
    tenant_required boolean NOT NULL,
    schema_version integer NOT NULL CHECK (schema_version > 0),
    owner_module text NOT NULL,
    CONSTRAINT registry_scope_consistent CHECK (
        (scope = 'tenant' AND tenant_required)
        OR (scope = 'app_shared' AND tenant_required)
        OR (scope = 'platform' AND NOT tenant_required)
    )
);
