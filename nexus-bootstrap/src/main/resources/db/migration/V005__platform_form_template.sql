-- K1: platform-owned form templates, editable fields, and immutable published snapshots.

CREATE TABLE platform.form_template (
    id uuid PRIMARY KEY,
    code varchar(80) NOT NULL UNIQUE,
    name varchar(160) NOT NULL,
    description text,
    status varchar(24) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'VALIDATED', 'PUBLISHED', 'ARCHIVED')),
    running_version_id uuid,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 1
);

CREATE TABLE platform.form_template_field (
    id uuid PRIMARY KEY,
    template_id uuid NOT NULL REFERENCES platform.form_template(id) ON DELETE CASCADE,
    field_key varchar(80) NOT NULL,
    label varchar(160) NOT NULL,
    field_type varchar(24) NOT NULL
        CHECK (field_type IN ('TEXT', 'TEXTAREA', 'NUMBER', 'MONEY', 'DATE', 'SELECT', 'PERSON', 'ATTACHMENT')),
    required boolean NOT NULL DEFAULT false,
    options jsonb,
    position integer NOT NULL CHECK (position >= 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT form_template_field_key_unique UNIQUE (template_id, field_key),
    CONSTRAINT form_template_field_position_unique UNIQUE (template_id, position),
    CONSTRAINT form_template_select_options CHECK (field_type <> 'SELECT' OR options IS NOT NULL)
);

CREATE TABLE platform.form_template_version (
    id uuid PRIMARY KEY,
    template_id uuid NOT NULL REFERENCES platform.form_template(id),
    version integer NOT NULL CHECK (version > 0),
    fields_snapshot jsonb NOT NULL,
    published_at timestamptz NOT NULL,
    published_by uuid,
    CONSTRAINT form_template_version_unique UNIQUE (template_id, version)
);

ALTER TABLE platform.form_template
    ADD CONSTRAINT form_template_running_version_fk
    FOREIGN KEY (running_version_id) REFERENCES platform.form_template_version(id);

INSERT INTO platform.table_registry (
    id, table_schema, table_name, scope, tenant_required, schema_version,
    owner_module, created_at, updated_at, version
) VALUES
    ('0199a1c0-0000-7000-8000-000000000050', 'platform', 'form_template', 'platform', false, 1,
     'nexus-platform-forms', TIMESTAMPTZ '2026-09-25 00:00:00+00', TIMESTAMPTZ '2026-09-25 00:00:00+00', 1),
    ('0199a1c0-0000-7000-8000-000000000051', 'platform', 'form_template_field', 'platform', false, 1,
     'nexus-platform-forms', TIMESTAMPTZ '2026-09-25 00:00:00+00', TIMESTAMPTZ '2026-09-25 00:00:00+00', 1),
    ('0199a1c0-0000-7000-8000-000000000052', 'platform', 'form_template_version', 'platform', false, 1,
     'nexus-platform-forms', TIMESTAMPTZ '2026-09-25 00:00:00+00', TIMESTAMPTZ '2026-09-25 00:00:00+00', 1);
