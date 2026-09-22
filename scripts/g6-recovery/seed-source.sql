-- G6 dedicated-source fixture only.
-- Whole-database restore (both tenants). Not a single-tenant logical export.
-- Must never be applied to haopan_platform or a product live database.

CREATE SCHEMA platform;
CREATE SCHEMA files;
CREATE SCHEMA ledger;

CREATE TABLE platform.flyway_schema_history (
    installed_rank integer PRIMARY KEY,
    version varchar(50),
    description varchar(200) NOT NULL,
    type varchar(20) NOT NULL,
    script varchar(1000) NOT NULL,
    checksum integer,
    installed_by varchar(100) NOT NULL,
    installed_on timestamptz NOT NULL DEFAULT TIMESTAMPTZ '2026-09-22 12:00:00+00',
    execution_time integer NOT NULL,
    success boolean NOT NULL
);

CREATE TABLE platform.audit_record (
    chain_index bigint PRIMARY KEY,
    event_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    event_type text NOT NULL,
    event_hash text NOT NULL,
    previous_hash text,
    payload jsonb NOT NULL
);

CREATE TABLE platform.audit_chain_meta (
    chain_id text PRIMARY KEY,
    head_index bigint NOT NULL,
    head_hash text NOT NULL
);

CREATE TABLE files.file_object (
    file_id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    object_key text NOT NULL,
    sha256 text NOT NULL,
    byte_length bigint NOT NULL
);

CREATE TABLE ledger.sample_row (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    title text NOT NULL,
    amount numeric(12, 2) NOT NULL
);

INSERT INTO platform.flyway_schema_history
    (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
VALUES
    (1, '001', 'create logical schemas', 'SQL', 'V001__create_logical_schemas.sql', 1, 'g6-drill', 10, true),
    (2, '501', 'audit hash chain', 'SQL', 'V501__audit_hash_chain.sql', 501, 'g6-drill', 12, true),
    (3, '510', 'file object', 'SQL', 'V510__file_object.sql', 510, 'g6-drill', 8, true);

-- Two tenants. Full-database restore must keep both. Do not filter by tenant_id.
INSERT INTO platform.audit_record
    (chain_index, event_id, tenant_id, event_type, event_hash, previous_hash, payload)
VALUES
    (1,
     'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1',
     '11111111-1111-4111-8111-111111111111',
     'DATA',
     'hash-1',
     NULL,
     '{"note":"tenant-a"}'),
    (2,
     'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2',
     '22222222-2222-4222-8222-222222222222',
     'DATA',
     'hash-2',
     'hash-1',
     '{"note":"tenant-b"}');

INSERT INTO platform.audit_chain_meta (chain_id, head_index, head_hash)
VALUES ('default', 2, 'hash-2');

INSERT INTO files.file_object (file_id, tenant_id, object_key, sha256, byte_length)
VALUES
    ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1',
     '11111111-1111-4111-8111-111111111111',
     'tenants/11111111-1111-4111-8111-111111111111/leave/2026/09/file-a.bin',
     'placeholder-a',
     0),
    ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2',
     '22222222-2222-4222-8222-222222222222',
     'tenants/22222222-2222-4222-8222-222222222222/expense/2026/09/file-b.bin',
     'placeholder-b',
     0);

INSERT INTO ledger.sample_row (id, tenant_id, title, amount)
VALUES
    ('cccccccc-cccc-4ccc-8ccc-ccccccccccc1',
     '11111111-1111-4111-8111-111111111111',
     'leave-tenant-a',
     3.00),
    ('cccccccc-cccc-4ccc-8ccc-ccccccccccc2',
     '22222222-2222-4222-8222-222222222222',
     'expense-tenant-b',
     42.50);
