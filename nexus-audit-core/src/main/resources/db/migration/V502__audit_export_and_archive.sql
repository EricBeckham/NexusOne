-- G4 follow-up: EXPORT category on the existing hash chain + 90-day archive copy table.
-- Does not change V501 append-only / same-TX rules. Application code still only INSERT.
-- Hot rows are never deleted here (REVOKE DELETE stays). Archive is a same-PG copy
-- toward 365-day retention, not object-store cold storage and not a purge job.

ALTER TABLE platform.audit_record DROP CONSTRAINT IF EXISTS audit_record_category;
ALTER TABLE platform.audit_record ADD CONSTRAINT audit_record_category
    CHECK (category IN ('LOGIN', 'PERMISSION', 'ADMIN', 'DATA', 'APPROVAL', 'GUARD_REJECT', 'EXPORT'));

CREATE TABLE platform.audit_record_archive (
    chain_index         bigint NOT NULL,
    event_id            uuid PRIMARY KEY,
    event_version       integer NOT NULL,
    occurred_at         timestamptz NOT NULL,
    category            varchar(32) NOT NULL,
    event_type          varchar(96) NOT NULL,
    tenant_id           uuid,
    actor_subject_id    uuid,
    actor_tenant_id     uuid,
    platform_context    boolean NOT NULL,
    reason              varchar(200),
    trace_id            varchar(64) NOT NULL,
    correlation_id      varchar(64),
    causation_id        varchar(64),
    outcome             varchar(16) NOT NULL,
    resource_type       varchar(64) NOT NULL,
    resource_id         varchar(64),
    details             jsonb NOT NULL DEFAULT '{}'::jsonb,
    previous_hash       bytea NOT NULL,
    event_hash          bytea NOT NULL,
    cipher_suite        varchar(32) NOT NULL,
    digest_key_version  varchar(64) NOT NULL,
    archived_at         timestamptz NOT NULL,
    CONSTRAINT audit_record_archive_outcome CHECK (outcome IN ('ACCEPTED', 'REJECTED')),
    CONSTRAINT audit_record_archive_category CHECK (category IN (
        'LOGIN', 'PERMISSION', 'ADMIN', 'DATA', 'APPROVAL', 'GUARD_REJECT', 'EXPORT')),
    CONSTRAINT audit_record_archive_suite CHECK (cipher_suite IN ('INTERNATIONAL', 'GM'))
);

CREATE INDEX audit_record_archive_trace_idx ON platform.audit_record_archive (trace_id, chain_index);
CREATE INDEX audit_record_archive_occurred_idx ON platform.audit_record_archive (occurred_at);

REVOKE UPDATE, DELETE ON platform.audit_record_archive FROM PUBLIC;
