-- PLB5: a product can combine multiple pinned application versions.
-- Existing single-app SKU paths remain intact until PLB9 maps legacy rows.
CREATE TABLE market.product (
    id uuid PRIMARY KEY,
    code varchar(24) NOT NULL UNIQUE,
    name varchar(160) NOT NULL,
    description text,
    status varchar(24) NOT NULL DEFAULT 'DRAFT',
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 1,
    CONSTRAINT ck_market_product_code CHECK (code ~ '^[a-z][a-z0-9_]{0,23}$'),
    CONSTRAINT ck_market_product_name CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_market_product_status CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED')),
    CONSTRAINT ck_market_product_version CHECK (version > 0)
);

CREATE TABLE market.product_app (
    product_id uuid NOT NULL REFERENCES market.product(id),
    app_id uuid NOT NULL REFERENCES market.app_package(id),
    app_version_id uuid REFERENCES market.app_version(id),
    sort_order integer NOT NULL,
    PRIMARY KEY (product_id, app_id),
    CONSTRAINT uq_market_product_app_order UNIQUE (product_id, sort_order)
);
CREATE INDEX ix_market_product_app_version ON market.product_app(app_version_id)
    WHERE app_version_id IS NOT NULL;

ALTER TABLE market.sku
    ALTER COLUMN app_id DROP NOT NULL,
    ADD COLUMN product_id uuid REFERENCES market.product(id),
    ADD CONSTRAINT ck_market_sku_app_or_product CHECK (app_id IS NOT NULL OR product_id IS NOT NULL);
CREATE INDEX ix_market_sku_product ON market.sku(product_id) WHERE product_id IS NOT NULL;

COMMENT ON COLUMN market.product_app.app_version_id IS
    'NULL only for legacy single-app auto-products whose version remains selected at activation until PLB9.';
COMMENT ON COLUMN market.sku.app_id IS
    'Legacy single-app link; NULL for product-native SKUs. PLB7 installs every app in product_app.';
