-- K12a: immutable product/form/process references captured when a tenant installs a catalog SKU.
CREATE TABLE market.installation_product_pin (
    installation_id uuid PRIMARY KEY REFERENCES market.installation(id) ON DELETE CASCADE,
    product_definition_id uuid NOT NULL REFERENCES market.product_definition(id),
    form_template_version_id uuid NOT NULL REFERENCES platform.form_template_version(id),
    process_definition_key varchar(120),
    process_definition_version integer,
    pinned_at timestamptz NOT NULL,
    CONSTRAINT installation_product_pin_process_version_pair CHECK (
        (process_definition_key IS NULL AND process_definition_version IS NULL)
        OR (process_definition_key IS NOT NULL AND process_definition_version > 0)
    )
);

CREATE INDEX ix_installation_product_pin_definition
    ON market.installation_product_pin (product_definition_id);
