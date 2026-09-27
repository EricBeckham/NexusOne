-- DPO-L2: durable print field→slot mapping per product definition (CF-R3 contract), plus an append-only
-- revision history. Schema only: the service keeps its in-memory store until DPO-L3 switches to these tables.
-- Paper / orientation / slot ids are validated by the application against the CF-R3 contract, not here, so
-- the contract can grow without a migration.

CREATE TABLE market.print_mapping (
    product_definition_id uuid PRIMARY KEY REFERENCES market.product_definition(id),
    template_code varchar(120) NOT NULL,
    paper varchar(16) NOT NULL,
    orientation varchar(16) NOT NULL,
    mappings jsonb NOT NULL DEFAULT '[]'::jsonb,
    revision bigint NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by uuid,
    CONSTRAINT print_mapping_revision_positive CHECK (revision > 0),
    CONSTRAINT print_mapping_mappings_array CHECK (jsonb_typeof(mappings) = 'array')
);

-- One row per accepted save; revision 0 is the contract default and is never stored.
CREATE TABLE market.print_mapping_revision (
    product_definition_id uuid NOT NULL REFERENCES market.product_definition(id),
    revision bigint NOT NULL,
    template_code varchar(120) NOT NULL,
    paper varchar(16) NOT NULL,
    orientation varchar(16) NOT NULL,
    mappings jsonb NOT NULL,
    saved_at timestamptz NOT NULL,
    saved_by uuid,
    PRIMARY KEY (product_definition_id, revision),
    CONSTRAINT print_mapping_revision_history_positive CHECK (revision > 0),
    CONSTRAINT print_mapping_revision_mappings_array CHECK (jsonb_typeof(mappings) = 'array')
);

CREATE INDEX ix_print_mapping_revision_saved
    ON market.print_mapping_revision (product_definition_id, saved_at DESC);
