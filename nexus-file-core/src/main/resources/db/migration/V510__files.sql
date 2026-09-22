-- M0-10 file metadata. Object bytes live in S3/MinIO. Flyway window V500+ (leave V500-V509 for M0-09).
CREATE SCHEMA IF NOT EXISTS files;
COMMENT ON SCHEMA files IS 'M0-10 file metadata. Object bytes live in S3; Flyway-managed.';
GRANT USAGE ON SCHEMA files TO CURRENT_USER;

CREATE TABLE files.file_object (
    file_id          uuid PRIMARY KEY,
    tenant_id        uuid NOT NULL,
    bucket_alias     varchar(64) NOT NULL,
    object_key       varchar(512) NOT NULL,
    original_name    varchar(255) NOT NULL,
    size_bytes       bigint NOT NULL,
    content_type     varchar(128) NOT NULL,
    checksum_sha256  varchar(64) NOT NULL,
    status           varchar(16) NOT NULL,
    owner_subject    varchar(128) NOT NULL,
    resource_id      varchar(128),
    created_at       timestamptz NOT NULL,
    created_by       varchar(128) NOT NULL,
    version          bigint NOT NULL DEFAULT 1,
    CONSTRAINT files_file_status CHECK (status IN ('UPLOADED', 'DELETED')),
    CONSTRAINT files_file_size CHECK (size_bytes >= 0),
    CONSTRAINT files_file_key_tenant CHECK (object_key LIKE 'tenants/' || tenant_id::text || '/%'
        OR object_key LIKE 'platform/%')
);

CREATE UNIQUE INDEX files_file_object_key_uq ON files.file_object (object_key);
CREATE INDEX files_file_tenant_idx ON files.file_object (tenant_id, created_at DESC);
