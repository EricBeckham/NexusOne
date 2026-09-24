-- K8: reusable market product definition bound to immutable workflow/form versions.

CREATE TABLE market.product_definition (
    id uuid PRIMARY KEY,
    code varchar(80) NOT NULL UNIQUE,
    name varchar(160) NOT NULL,
    description text,
    form_template_version_id uuid NOT NULL REFERENCES platform.form_template_version(id),
    process_definition_key varchar(120),
    process_definition_version integer,
    default_permission_pack jsonb NOT NULL DEFAULT '{}'::jsonb,
    status varchar(24) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 1,
    CONSTRAINT product_definition_process_version_pair CHECK (
        (process_definition_key IS NULL AND process_definition_version IS NULL)
        OR (process_definition_key IS NOT NULL AND process_definition_version > 0)
    )
);

CREATE TABLE market.sku_product_binding (
    sku_code varchar(80) PRIMARY KEY REFERENCES market.sku(code),
    product_definition_id uuid NOT NULL REFERENCES market.product_definition(id),
    created_at timestamptz NOT NULL
);

CREATE INDEX ix_product_definition_form_template
    ON market.product_definition (form_template_version_id);
CREATE INDEX ix_sku_product_binding_product
    ON market.sku_product_binding (product_definition_id);
