-- Runtime metadata proving Flyway applied the static baseline.
-- Seed UUIDs are RFC 9562 v7 with a fixed timestamp for a stable checksum.

CREATE TABLE platform.runtime_meta (
    id uuid PRIMARY KEY,
    meta_key varchar(100) NOT NULL UNIQUE,
    meta_value varchar(500) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

COMMENT ON TABLE platform.runtime_meta IS 'Platform static key/value. Not tenant data.';

INSERT INTO platform.runtime_meta (id, meta_key, meta_value, created_at, updated_at) VALUES
    ('0199a1c0-0000-7000-8000-000000000001', 'schema_manager', 'flyway_static_only',
     TIMESTAMPTZ '2026-09-22 00:00:00+00', TIMESTAMPTZ '2026-09-22 00:00:00+00'),
    ('0199a1c0-0000-7000-8000-000000000002', 'dynamic_ledger_owner', 'schema_service',
     TIMESTAMPTZ '2026-09-22 00:00:00+00', TIMESTAMPTZ '2026-09-22 00:00:00+00'),
    ('0199a1c0-0000-7000-8000-000000000003', 'flyway_version_window', '001-099',
     TIMESTAMPTZ '2026-09-22 00:00:00+00', TIMESTAMPTZ '2026-09-22 00:00:00+00');

INSERT INTO platform.table_registry (
    id, table_schema, table_name, scope, tenant_required, schema_version,
    owner_module, created_at, updated_at, version
) VALUES
    ('0199a1c0-0000-7000-8000-000000000010', 'platform', 'table_registry', 'platform', false, 1,
     'nexus-bootstrap', TIMESTAMPTZ '2026-09-22 00:00:00+00', TIMESTAMPTZ '2026-09-22 00:00:00+00', 1),
    ('0199a1c0-0000-7000-8000-000000000011', 'platform', 'runtime_meta', 'platform', false, 1,
     'nexus-bootstrap', TIMESTAMPTZ '2026-09-22 00:00:00+00', TIMESTAMPTZ '2026-09-22 00:00:00+00', 1);
