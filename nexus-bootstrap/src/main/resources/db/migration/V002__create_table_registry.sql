-- Platform table registry. Dynamic ledger rows are inserted by SchemaService in the same
-- transaction as DDL (M0-06). Flyway may only seed static platform tables.

CREATE TABLE platform.table_registry (
    id uuid PRIMARY KEY,
    table_schema varchar(63) NOT NULL,
    table_name varchar(63) NOT NULL,
    scope varchar(32) NOT NULL CHECK (scope IN ('tenant', 'platform', 'app_shared')),
    tenant_required boolean NOT NULL,
    schema_version integer NOT NULL CHECK (schema_version > 0),
    owner_module varchar(64) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz,
    version bigint NOT NULL DEFAULT 1,
    CONSTRAINT table_registry_name UNIQUE (table_schema, table_name),
    CONSTRAINT table_registry_scope_tenant CHECK (
        (scope = 'platform' AND tenant_required = false)
        OR (scope IN ('tenant', 'app_shared') AND tenant_required = true)
    )
);

COMMENT ON TABLE platform.table_registry IS
    'Catalog of physical tables. Static rows from Flyway; dynamic ledger rows from SchemaService only.';

CREATE INDEX table_registry_owner_idx ON platform.table_registry (owner_module) WHERE deleted_at IS NULL;
