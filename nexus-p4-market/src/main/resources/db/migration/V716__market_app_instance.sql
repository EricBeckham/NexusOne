-- PLB7: tenant application instances (PLX1 C4 state sync) and product-native activation requests.
-- One row per tenant x application. Installing a product creates an instance for each of its applications,
-- or reuses the tenant's installed instance of that application. `revision` starts at 1 and is bumped in the
-- same transaction as every install / upgrade / uninstall of the instance; C reconciles on it (AppInstanceQuery).
CREATE TABLE market.app_instance (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES platform.tenant(id),
    product_id uuid NOT NULL REFERENCES market.product(id),
    sku_code varchar(80) NOT NULL REFERENCES market.sku(code),
    subscription_id uuid NOT NULL REFERENCES market.subscription(id),
    app_id uuid NOT NULL REFERENCES market.app_package(id),
    app_version_id uuid NOT NULL REFERENCES market.app_version(id),
    status varchar(16) NOT NULL,
    mode varchar(16),
    entries jsonb NOT NULL DEFAULT '[]'::jsonb,
    revision bigint NOT NULL,
    created_at timestamptz NOT NULL,
    changed_at timestamptz NOT NULL,
    CONSTRAINT ck_app_instance_status CHECK (status IN ('INSTALLED', 'UNINSTALLED')),
    CONSTRAINT ck_app_instance_mode CHECK (
        (status = 'INSTALLED' AND mode IS NOT NULL) OR (status = 'UNINSTALLED' AND mode IS NULL)),
    CONSTRAINT ck_app_instance_entries CHECK (jsonb_typeof(entries) = 'array'),
    CONSTRAINT ck_app_instance_revision CHECK (revision >= 1)
);

-- At most one installed instance of an application per tenant; uninstalled rows stay for reconciliation.
CREATE UNIQUE INDEX ux_market_app_instance_active ON market.app_instance(tenant_id, app_id)
    WHERE status = 'INSTALLED';
CREATE INDEX ix_market_app_instance_tenant ON market.app_instance(tenant_id);

-- Product-native SKUs pin one version per application in market.product_app, so an activation request for
-- such a SKU has no single version. Legacy single-app requests keep sending one.
ALTER TABLE market.activation_application ALTER COLUMN version_id DROP NOT NULL;

COMMENT ON TABLE market.app_instance IS
    'PLB7 / PLX1 C4: tenant application instance; the state AppInstanceQuery returns. Tenant-scoped.';
COMMENT ON COLUMN market.app_instance.entries IS
    'All entry keys (fn:{app}:{form}) of the pinned manifest version; [] when UNINSTALLED.';
COMMENT ON COLUMN market.activation_application.version_id IS
    'Legacy single-app SKU version; NULL for product-native SKUs (PLB7), whose versions come from market.product_app.';
