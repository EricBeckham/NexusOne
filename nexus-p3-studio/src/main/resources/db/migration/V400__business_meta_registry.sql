-- M0-06 P3 metadata registry. Flyway window V400–V499, schema business_meta.
-- Physical ledger tables (l_* / a_* in business_data) are owned by SchemaService, never by this file.

CREATE SCHEMA IF NOT EXISTS business_meta;
CREATE SCHEMA IF NOT EXISTS business_data;
CREATE SCHEMA IF NOT EXISTS platform;

-- Standalone P3 tests may not have M0-01 V002. Bootstrap already has this table;
-- IF NOT EXISTS keeps V400 additive and never creates l_* / a_* ledger tables.
CREATE TABLE IF NOT EXISTS platform.table_registry (
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
    CONSTRAINT table_registry_name UNIQUE (table_schema, table_name)
);

COMMENT ON SCHEMA business_meta IS 'P3 application/ledger/field metadata. Flyway V400+. Not physical ledger data.';
COMMENT ON SCHEMA business_data IS 'P3 physical ledger tables. SchemaService only. Flyway must not CREATE TABLE here.';

CREATE TABLE business_meta.application (
    id              uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL,
    tenant_code     varchar(24) NOT NULL,
    app_code        varchar(24) NOT NULL,
    display_name    varchar(128) NOT NULL,
    kind            varchar(16) NOT NULL,
    status          varchar(16) NOT NULL,
    created_at      timestamptz NOT NULL,
    created_by      varchar(64) NOT NULL,
    updated_at      timestamptz NOT NULL,
    updated_by      varchar(64) NOT NULL,
    deleted_at      timestamptz,
    version         bigint NOT NULL DEFAULT 1,
    CONSTRAINT p3_app_kind CHECK (kind IN ('CUSTOM', 'PACKAGE')),
    CONSTRAINT p3_app_status CHECK (status IN ('DRAFT', 'ACTIVE', 'DISABLED')),
    CONSTRAINT p3_app_code_fmt CHECK (app_code ~ '^[a-z][a-z0-9_]{0,23}$'),
    CONSTRAINT p3_app_tenant_code_fmt CHECK (tenant_code ~ '^[a-z][a-z0-9_]{0,23}$')
);

CREATE UNIQUE INDEX p3_app_tenant_code_uq
    ON business_meta.application (tenant_id, app_code)
    WHERE deleted_at IS NULL;
CREATE INDEX p3_app_tenant_idx
    ON business_meta.application (tenant_id)
    WHERE deleted_at IS NULL;

CREATE TABLE business_meta.ledger (
    id              uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL,
    tenant_code     varchar(24) NOT NULL,
    application_id  uuid NOT NULL REFERENCES business_meta.application (id),
    ledger_code     varchar(24) NOT NULL,
    display_name    varchar(128) NOT NULL,
    scope           varchar(16) NOT NULL,
    physical_table  varchar(63) NOT NULL,
    schema_version  integer NOT NULL DEFAULT 1,
    status          varchar(16) NOT NULL,
    created_at      timestamptz NOT NULL,
    created_by      varchar(64) NOT NULL,
    updated_at      timestamptz NOT NULL,
    updated_by      varchar(64) NOT NULL,
    deleted_at      timestamptz,
    version         bigint NOT NULL DEFAULT 1,
    CONSTRAINT p3_ledger_scope CHECK (scope IN ('TENANT', 'APP_SHARED')),
    CONSTRAINT p3_ledger_status CHECK (status IN ('DRAFT', 'READY', 'DISABLED')),
    CONSTRAINT p3_ledger_code_fmt CHECK (ledger_code ~ '^[a-z][a-z0-9_]{0,23}$'),
    CONSTRAINT p3_ledger_schema_version CHECK (schema_version > 0),
    CONSTRAINT p3_ledger_table_fmt CHECK (
        physical_table ~ '^[la]_[a-z0-9_]+$'
        AND char_length(physical_table) <= 52
    )
);

CREATE UNIQUE INDEX p3_ledger_tenant_code_uq
    ON business_meta.ledger (tenant_id, ledger_code)
    WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX p3_ledger_physical_uq
    ON business_meta.ledger (physical_table)
    WHERE deleted_at IS NULL;
CREATE INDEX p3_ledger_tenant_idx
    ON business_meta.ledger (tenant_id)
    WHERE deleted_at IS NULL;

CREATE TABLE business_meta.ledger_field (
    id              uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL,
    ledger_id       uuid NOT NULL REFERENCES business_meta.ledger (id),
    field_code      varchar(24) NOT NULL,
    display_name    varchar(128) NOT NULL,
    field_type      varchar(16) NOT NULL,
    required        boolean NOT NULL DEFAULT false,
    indexed         boolean NOT NULL DEFAULT false,
    sort_order      integer NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL,
    created_by      varchar(64) NOT NULL,
    updated_at      timestamptz NOT NULL,
    updated_by      varchar(64) NOT NULL,
    deleted_at      timestamptz,
    version         bigint NOT NULL DEFAULT 1,
    CONSTRAINT p3_field_type CHECK (field_type IN ('TEXT', 'NUMERIC', 'INTEGER', 'BOOLEAN', 'TIMESTAMPTZ')),
    CONSTRAINT p3_field_code_fmt CHECK (field_code ~ '^[a-z][a-z0-9_]{0,23}$')
);

CREATE UNIQUE INDEX p3_field_ledger_code_uq
    ON business_meta.ledger_field (ledger_id, field_code)
    WHERE deleted_at IS NULL;
CREATE INDEX p3_field_tenant_idx
    ON business_meta.ledger_field (tenant_id)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE business_meta.application IS 'P3 application registry. One tenant owns each CUSTOM app.';
COMMENT ON TABLE business_meta.ledger IS 'P3 ledger (台账) metadata. physical_table is created by SchemaService.';
COMMENT ON TABLE business_meta.ledger_field IS 'P3 ledger field definitions. Physical columns are added only by SchemaService.';
