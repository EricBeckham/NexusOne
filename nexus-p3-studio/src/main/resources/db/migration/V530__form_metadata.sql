-- M0-07 form metadata + publish log. Flyway window V530–V549.
-- Do not use V500 (M0-09 execution), V501 (M0-11 audit), V510 (files), V520 (notify).
-- V400–V499 remain reserved for M0-06 ledger/field metadata. This file is not ledger DDL.
-- Bindings store ledger_code + field_code only. No physical column/table names.

CREATE SCHEMA IF NOT EXISTS business_meta;
CREATE SCHEMA IF NOT EXISTS platform;

COMMENT ON SCHEMA business_meta IS 'P3 application/form metadata. Flyway-managed. Not physical ledger data.';

GRANT USAGE ON SCHEMA business_meta TO CURRENT_USER;

CREATE TABLE business_meta.form (
    form_id           uuid PRIMARY KEY,
    tenant_id         uuid NOT NULL,
    app_code          varchar(64) NOT NULL,
    form_code         varchar(64) NOT NULL,
    name              varchar(200) NOT NULL,
    description       varchar(1000),
    draft_status      varchar(16) NOT NULL,
    running_version   integer,
    created_at        timestamptz NOT NULL,
    created_by        varchar(128) NOT NULL,
    updated_at        timestamptz NOT NULL,
    updated_by        varchar(128) NOT NULL,
    CONSTRAINT form_draft_status CHECK (draft_status IN ('DRAFT', 'VALIDATED')),
    CONSTRAINT form_code_format CHECK (form_code ~ '^[a-z][a-z0-9_]{0,62}$'),
    CONSTRAINT form_app_code_format CHECK (app_code ~ '^[a-z][a-z0-9_]{0,62}$'),
    CONSTRAINT form_running_version CHECK (running_version IS NULL OR running_version >= 1)
);

CREATE UNIQUE INDEX form_tenant_code_uq ON business_meta.form (tenant_id, form_code);
CREATE INDEX form_tenant_idx ON business_meta.form (tenant_id, updated_at DESC);

CREATE TABLE business_meta.form_binding (
    binding_id        uuid PRIMARY KEY,
    form_id           uuid NOT NULL REFERENCES business_meta.form (form_id),
    tenant_id         uuid NOT NULL,
    widget_code       varchar(64) NOT NULL,
    ledger_code       varchar(64) NOT NULL,
    field_code        varchar(64) NOT NULL,
    required          boolean NOT NULL DEFAULT false,
    readonly          boolean NOT NULL DEFAULT false,
    widget_type       varchar(32) NOT NULL,
    ordinal           integer NOT NULL,
    CONSTRAINT form_binding_widget CHECK (widget_code ~ '^[a-z][a-z0-9_]{0,62}$'),
    CONSTRAINT form_binding_ledger CHECK (ledger_code ~ '^[a-z][a-z0-9_]{0,62}$'),
    CONSTRAINT form_binding_field CHECK (field_code ~ '^[a-z][a-z0-9_]{0,62}$'),
    CONSTRAINT form_binding_no_physical_ledger CHECK (ledger_code !~ '^(l|a)_'),
    CONSTRAINT form_binding_widget_type CHECK (widget_type IN (
        'TEXT', 'TEXTAREA', 'NUMBER', 'DATE', 'BOOLEAN', 'PERSON', 'DEPT', 'POSITION'
    )),
    CONSTRAINT form_binding_ordinal CHECK (ordinal >= 0)
);

CREATE UNIQUE INDEX form_binding_widget_uq ON business_meta.form_binding (form_id, widget_code);
CREATE UNIQUE INDEX form_binding_field_uq ON business_meta.form_binding (form_id, ledger_code, field_code);
CREATE INDEX form_binding_form_idx ON business_meta.form_binding (form_id, ordinal);

CREATE TABLE business_meta.form_version (
    version_id        uuid PRIMARY KEY,
    form_id           uuid NOT NULL REFERENCES business_meta.form (form_id),
    tenant_id         uuid NOT NULL,
    version_no        integer NOT NULL,
    status            varchar(16) NOT NULL,
    snapshot_json     jsonb NOT NULL,
    notes             varchar(1000),
    created_at        timestamptz NOT NULL,
    created_by        varchar(128) NOT NULL,
    CONSTRAINT form_version_status CHECK (status IN (
        'VALIDATED', 'PUBLISHING', 'RUNNING', 'SUPERSEDED', 'FAILED'
    )),
    CONSTRAINT form_version_no CHECK (version_no >= 1)
);

CREATE UNIQUE INDEX form_version_no_uq ON business_meta.form_version (form_id, version_no);
CREATE UNIQUE INDEX form_version_one_running ON business_meta.form_version (form_id) WHERE status = 'RUNNING';
CREATE INDEX form_version_form_idx ON business_meta.form_version (form_id, version_no DESC);

CREATE TABLE business_meta.form_publish_log (
    log_id            uuid PRIMARY KEY,
    form_id           uuid NOT NULL,
    tenant_id         uuid NOT NULL,
    version_no        integer,
    from_status       varchar(16) NOT NULL,
    to_status         varchar(16) NOT NULL,
    outcome           varchar(16) NOT NULL,
    message           varchar(2000),
    details           jsonb NOT NULL DEFAULT '{}'::jsonb,
    trace_id          varchar(64) NOT NULL,
    occurred_at       timestamptz NOT NULL,
    CONSTRAINT form_publish_outcome CHECK (outcome IN ('ACCEPTED', 'REJECTED', 'ROLLED_BACK'))
);

CREATE INDEX form_publish_log_form_idx ON business_meta.form_publish_log (form_id, occurred_at DESC);

CREATE TABLE business_meta.form_submission (
    submission_id     uuid PRIMARY KEY,
    form_id           uuid NOT NULL REFERENCES business_meta.form (form_id),
    tenant_id         uuid NOT NULL,
    running_version   integer NOT NULL,
    values_json       jsonb NOT NULL,
    submitted_at      timestamptz NOT NULL,
    submitted_by      varchar(128) NOT NULL,
    CONSTRAINT form_submission_version CHECK (running_version >= 1)
);

CREATE INDEX form_submission_form_idx ON business_meta.form_submission (tenant_id, form_id, submitted_at DESC);

COMMENT ON TABLE business_meta.form IS 'P3 form header. Draft vs running version pointer. M0-07.';
COMMENT ON TABLE business_meta.form_binding IS 'Draft widgets bind ledger_code+field_code only. Never physical columns.';
COMMENT ON TABLE business_meta.form_version IS 'Immutable publish snapshots. Failed publish never becomes RUNNING.';
COMMENT ON TABLE business_meta.form_publish_log IS 'Publish attempt log. ROLLED_BACK means running pointer was not switched.';
COMMENT ON TABLE business_meta.form_submission IS 'Fill against the running snapshot. Does not write business_data ledger tables.';

-- Register in platform.table_registry when M0-01 V002 is present (bootstrap). Standalone module tests skip this.
DO $$
BEGIN
    IF to_regclass('platform.table_registry') IS NULL THEN
        RETURN;
    END IF;
    INSERT INTO platform.table_registry (
        id, table_schema, table_name, scope, tenant_required, schema_version,
        owner_module, created_at, updated_at, version
    ) VALUES
        ('0199a1c0-0000-7000-8000-000000000530', 'business_meta', 'form', 'tenant', true, 1,
         'nexus-p3-studio', TIMESTAMPTZ '2026-09-22 00:00:00+00', TIMESTAMPTZ '2026-09-22 00:00:00+00', 1),
        ('0199a1c0-0000-7000-8000-000000000531', 'business_meta', 'form_binding', 'tenant', true, 1,
         'nexus-p3-studio', TIMESTAMPTZ '2026-09-22 00:00:00+00', TIMESTAMPTZ '2026-09-22 00:00:00+00', 1),
        ('0199a1c0-0000-7000-8000-000000000532', 'business_meta', 'form_version', 'tenant', true, 1,
         'nexus-p3-studio', TIMESTAMPTZ '2026-09-22 00:00:00+00', TIMESTAMPTZ '2026-09-22 00:00:00+00', 1),
        ('0199a1c0-0000-7000-8000-000000000533', 'business_meta', 'form_publish_log', 'tenant', true, 1,
         'nexus-p3-studio', TIMESTAMPTZ '2026-09-22 00:00:00+00', TIMESTAMPTZ '2026-09-22 00:00:00+00', 1),
        ('0199a1c0-0000-7000-8000-000000000534', 'business_meta', 'form_submission', 'tenant', true, 1,
         'nexus-p3-studio', TIMESTAMPTZ '2026-09-22 00:00:00+00', TIMESTAMPTZ '2026-09-22 00:00:00+00', 1)
    ON CONFLICT (table_schema, table_name) DO NOTHING;
END $$;
