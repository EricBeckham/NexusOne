-- M0-08 process metadata only (Flyway window V550–V599).
-- Flowable engine tables are NOT created here; the engine owns schema flowable.
CREATE SCHEMA IF NOT EXISTS flowable;
COMMENT ON SCHEMA flowable IS 'Flowable 7.1.0 engine tables. Owned by the engine; business SQL must not query act_*.';
GRANT USAGE, CREATE ON SCHEMA flowable TO CURRENT_USER;

CREATE SCHEMA IF NOT EXISTS business_meta;
GRANT USAGE ON SCHEMA business_meta TO CURRENT_USER;

CREATE TABLE business_meta.process_instance (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    process_key varchar(64) NOT NULL,
    business_key varchar(128) NOT NULL,
    initiator_person_id uuid NOT NULL,
    assignee_type varchar(16) NOT NULL,
    assignee_ref varchar(128) NOT NULL,
    flowable_instance_id varchar(64) NOT NULL,
    status varchar(16) NOT NULL,
    started_at timestamptz NOT NULL,
    ended_at timestamptz,
    CONSTRAINT process_instance_status CHECK (status IN ('RUNNING', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    CONSTRAINT process_instance_assignee_type CHECK (assignee_type IN ('PERSON', 'POSITION', 'ROLE')),
    CONSTRAINT process_instance_tenant_business UNIQUE (tenant_id, business_key)
);

CREATE INDEX process_instance_tenant_initiator_idx
    ON business_meta.process_instance (tenant_id, initiator_person_id, started_at DESC);

CREATE TABLE business_meta.process_todo (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    instance_id uuid NOT NULL REFERENCES business_meta.process_instance (id),
    flowable_task_id varchar(64) NOT NULL,
    task_name varchar(128) NOT NULL,
    assignee_person_id uuid,
    candidate_person_ids text NOT NULL DEFAULT '',
    status varchar(16) NOT NULL,
    outcome varchar(16),
    acted_by_person_id uuid,
    acted_at timestamptz,
    created_at timestamptz NOT NULL,
    CONSTRAINT process_todo_status CHECK (status IN ('PENDING', 'DONE', 'CANCELLED')),
    CONSTRAINT process_todo_tenant_task UNIQUE (tenant_id, flowable_task_id)
);

CREATE INDEX process_todo_tenant_status_idx
    ON business_meta.process_todo (tenant_id, status, created_at DESC);

CREATE TABLE business_meta.process_action (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    instance_id uuid NOT NULL,
    todo_id uuid,
    actor_person_id uuid NOT NULL,
    action varchar(16) NOT NULL,
    idempotency_key varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT process_action_kind CHECK (action IN ('START', 'APPROVE', 'REJECT', 'WITHDRAW')),
    CONSTRAINT process_action_idem UNIQUE (tenant_id, idempotency_key)
);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'platform' AND table_name = 'table_registry'
    ) THEN
        INSERT INTO platform.table_registry (
            id, table_schema, table_name, scope, tenant_required, schema_version,
            owner_module, created_at, updated_at, version
        )
        SELECT gen_random_uuid(), 'business_meta', t.table_name, 'tenant', true, 1,
               'nexus-p3-studio', now(), now(), 1
        FROM (VALUES ('process_instance'), ('process_todo'), ('process_action')) AS t(table_name)
        ON CONFLICT (table_schema, table_name) DO NOTHING;
    END IF;
END $$;

