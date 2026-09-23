CREATE SCHEMA IF NOT EXISTS market;

CREATE TABLE market.app_package (
  id uuid PRIMARY KEY, code varchar(80) NOT NULL UNIQUE, name varchar(160) NOT NULL,
  summary text, created_at timestamptz NOT NULL
);
CREATE TABLE market.app_version (
  id uuid PRIMARY KEY, app_id uuid NOT NULL REFERENCES market.app_package(id), version varchar(40) NOT NULL,
  status varchar(24) NOT NULL, release_notes text, created_at timestamptz NOT NULL, published_at timestamptz,
  UNIQUE(app_id, version)
);
CREATE TABLE market.sku (
  code varchar(80) PRIMARY KEY, app_id uuid NOT NULL REFERENCES market.app_package(id), name varchar(160),
  entitlements jsonb NOT NULL
);
CREATE TABLE market.subscription (
  id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES platform.tenant(id), sku_code varchar(80) NOT NULL REFERENCES market.sku(code),
  status varchar(24) NOT NULL, started_at timestamptz NOT NULL, ended_at timestamptz
);
CREATE TABLE market.installation (
  id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES platform.tenant(id), app_id uuid NOT NULL REFERENCES market.app_package(id),
  version_id uuid NOT NULL REFERENCES market.app_version(id), status varchar(24) NOT NULL,
  installed_at timestamptz NOT NULL, updated_at timestamptz NOT NULL
);
CREATE UNIQUE INDEX ux_market_active_install ON market.installation(tenant_id, app_id) WHERE status='INSTALLED';
