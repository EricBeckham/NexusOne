-- N16: metadata catalog for APIs consumed by NexusOne's own integrations.
-- This is not an external developer portal and contains no credentials or traffic metrics.

CREATE TABLE integration.api_catalog_entry (
    id              UUID PRIMARY KEY,
    name            VARCHAR(160) NOT NULL,
    http_method     VARCHAR(10) NOT NULL,
    path            VARCHAR(512) NOT NULL,
    version         VARCHAR(32) NOT NULL,
    scope           VARCHAR(160) NOT NULL,
    status          VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    description     VARCHAR(1024),
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_api_catalog_entry_method_path_version
        UNIQUE (http_method, path, version),
    CONSTRAINT ck_api_catalog_entry_method
        CHECK (http_method IN ('GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS')),
    CONSTRAINT ck_api_catalog_entry_path
        CHECK (path LIKE '/%'),
    CONSTRAINT ck_api_catalog_entry_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'DEPRECATED', 'RETIRED'))
);

CREATE INDEX ix_api_catalog_entry_status_name
    ON integration.api_catalog_entry (status, name);
