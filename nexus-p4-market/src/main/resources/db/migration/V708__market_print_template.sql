-- DPO-L4-pre: metadata for print templates copied from the built-in layout. Schema only (DPO-L4 adds the API).
-- The built-in template (builtin.approval-form.a4.v1) lives in code/classpath and is never stored here, so it
-- cannot be edited through this table. A copy carries metadata only (name, paper, orientation); slots stay those
-- of its source template — there is no layout / drag engine behind these rows.

CREATE TABLE market.print_template (
    id uuid PRIMARY KEY,
    code varchar(120) NOT NULL UNIQUE,
    name varchar(160) NOT NULL,
    source_template_code varchar(120) NOT NULL,
    paper varchar(16) NOT NULL,
    orientation varchar(16) NOT NULL,
    created_at timestamptz NOT NULL,
    created_by uuid,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 1,
    CONSTRAINT print_template_not_builtin_code CHECK (code NOT LIKE 'builtin.%'),
    CONSTRAINT print_template_version_positive CHECK (version > 0)
);

CREATE INDEX ix_print_template_source ON market.print_template (source_template_code);
