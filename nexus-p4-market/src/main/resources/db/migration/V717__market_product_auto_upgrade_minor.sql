-- PLB8 / D3: a product may follow newer published versions that keep the same major number.
ALTER TABLE market.product
    ADD COLUMN auto_upgrade_minor boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN market.product.auto_upgrade_minor IS
    'PLB8 / D3: when true, INHERIT instances of this product move to a newer published version with the same major number. Major bumps stay manual.';
