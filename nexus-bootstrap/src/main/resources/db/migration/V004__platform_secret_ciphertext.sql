-- M1: durable SecretPort ciphertext only. Plaintext remains in the application process.

CREATE TABLE platform.secret_ciphertext (
    secret_name varchar(512) PRIMARY KEY,
    version integer NOT NULL CHECK (version > 0),
    ciphertext bytea NOT NULL CHECK (octet_length(ciphertext) > 0),
    key_id varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL
);

COMMENT ON TABLE platform.secret_ciphertext IS
    'Encrypted SecretPort values only; application-level encryption is required before persistence.';
COMMENT ON COLUMN platform.secret_ciphertext.ciphertext IS
    'Authenticated ciphertext bytes; never store plaintext secret material here.';

INSERT INTO platform.table_registry (
    id, table_schema, table_name, scope, tenant_required, schema_version,
    owner_module, created_at, updated_at, version
) VALUES (
    '0199a1c0-0000-7000-8000-000000000012', 'platform', 'secret_ciphertext', 'platform', false, 1,
    'nexus-crypto-core', TIMESTAMPTZ '2026-09-24 00:00:00+00', TIMESTAMPTZ '2026-09-24 00:00:00+00', 1
);
