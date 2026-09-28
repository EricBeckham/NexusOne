-- PLB1: application category and version manifest storage.
-- Existing packages and versions predate PLX1, so both new references stay nullable until PLB9 migrates them.
CREATE TABLE market.app_category (
    code varchar(24) PRIMARY KEY,
    name varchar(160) NOT NULL,
    description text,
    sort_order integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 1,
    CONSTRAINT ck_app_category_code CHECK (code ~ '^[a-z][a-z0-9_]{0,23}$'),
    CONSTRAINT ck_app_category_name CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_app_category_version CHECK (version > 0)
);

ALTER TABLE market.app_package
    ADD COLUMN category_code varchar(24),
    ADD CONSTRAINT fk_app_package_category FOREIGN KEY (category_code)
        REFERENCES market.app_category (code);

CREATE INDEX ix_app_package_category ON market.app_package (category_code)
    WHERE category_code IS NOT NULL;

ALTER TABLE market.app_version
    ADD COLUMN manifest jsonb,
    ADD CONSTRAINT ck_app_version_manifest_v1 CHECK (
        manifest IS NULL OR (
            jsonb_typeof(manifest) = 'object'
            AND manifest ->> 'schema' = 'nexus.app-manifest.v1'
        )
    );

COMMENT ON COLUMN market.app_package.category_code IS
    'PLB1 app classification; NULL only for pre-PLX1 packages awaiting PLB9 migration.';
COMMENT ON COLUMN market.app_version.manifest IS
    'PLX1 application manifest snapshot; NULL only for legacy versions awaiting PLB9 migration. Published-version immutability is enforced by PLB2.';
