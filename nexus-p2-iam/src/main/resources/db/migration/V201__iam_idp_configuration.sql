-- N8: tenant-scoped identity-provider metadata only.
-- Client credentials belong in the secret store and are intentionally absent from this table.

CREATE TABLE iam.iam_idp_configuration (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    provider_type       VARCHAR(32) NOT NULL,
    display_name        VARCHAR(256) NOT NULL,
    issuer              VARCHAR(2048),
    status              VARCHAR(32) NOT NULL DEFAULT 'DISABLED',
    last_sync_at        TIMESTAMPTZ,
    last_sync_ok        BOOLEAN,
    sync_fail_count     INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_iam_idp_configuration_status
        CHECK (status IN ('DISABLED', 'ENABLED')),
    CONSTRAINT ck_iam_idp_configuration_sync_fail_count
        CHECK (sync_fail_count >= 0)
);

CREATE INDEX idx_iam_idp_configuration_tenant
    ON iam.iam_idp_configuration (tenant_id);
