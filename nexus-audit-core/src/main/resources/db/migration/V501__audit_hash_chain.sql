-- M0-11 hash-chained audit store. Flyway window V500+ (remaining static tables).
-- V500 is owned by M0-09 execution channel; this migration is V501.
-- P1 staging table platform.audit_event is unchanged until business modules switch.
-- Application code must only INSERT. UPDATE/DELETE are revoked from PUBLIC.

CREATE SCHEMA IF NOT EXISTS platform;

CREATE TABLE platform.audit_chain_meta (
    id              smallint PRIMARY KEY CHECK (id = 1),
    next_index      bigint NOT NULL,
    last_hash       bytea NOT NULL,
    cipher_suite    varchar(32) NOT NULL,
    CONSTRAINT audit_chain_suite CHECK (cipher_suite IN ('INTERNATIONAL', 'GM'))
);

INSERT INTO platform.audit_chain_meta (id, next_index, last_hash, cipher_suite)
VALUES (1, 0, decode('0000000000000000000000000000000000000000000000000000000000000000', 'hex'), 'INTERNATIONAL');

CREATE TABLE platform.audit_record (
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
    CONSTRAINT audit_record_outcome CHECK (outcome IN ('ACCEPTED', 'REJECTED')),
    CONSTRAINT audit_record_category CHECK (category IN ('LOGIN', 'PERMISSION', 'ADMIN', 'DATA', 'APPROVAL', 'GUARD_REJECT')),
    CONSTRAINT audit_record_suite CHECK (cipher_suite IN ('INTERNATIONAL', 'GM'))
);

CREATE UNIQUE INDEX audit_record_chain_uq ON platform.audit_record (chain_index);
CREATE INDEX audit_record_trace_idx ON platform.audit_record (trace_id, chain_index);
CREATE INDEX audit_record_tenant_idx ON platform.audit_record (tenant_id, occurred_at DESC);
CREATE INDEX audit_record_correlation_idx ON platform.audit_record (correlation_id, chain_index);

REVOKE UPDATE, DELETE ON platform.audit_record FROM PUBLIC;
REVOKE UPDATE, DELETE ON platform.audit_chain_meta FROM PUBLIC;
