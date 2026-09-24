-- M16: credential metadata only. The secret_name value is a SecretPort reference,
-- never the secret material itself.
CREATE TABLE integration.credential_metadata (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES platform.tenant(id),
    name varchar(160) NOT NULL,
    provider varchar(100) NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'ACTIVE',
    last_rotated_at timestamptz,
    expires_at timestamptz,
    secret_name varchar(255) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_credential_metadata_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT ck_credential_metadata_status CHECK (status IN ('ACTIVE', 'REVOKED'))
);

CREATE INDEX ix_credential_metadata_tenant_updated
    ON integration.credential_metadata (tenant_id, updated_at DESC);
