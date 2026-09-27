-- FA2: post-completion actions (docs/planning/FA1-办结后动作设计.md).
-- When a form's approval reaches a terminal outcome, insert one row into another ledger of the same tenant.
-- Migration only: no API, no business code in this card.

CREATE TABLE business_meta.completion_action (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    name varchar(128) NOT NULL,
    source_ledger_code varchar(128) NOT NULL,
    process_key varchar(128) NOT NULL,
    trigger_outcome varchar(16) NOT NULL,
    action_type varchar(32) NOT NULL,
    target_ledger_code varchar(128) NOT NULL,
    field_mapping jsonb NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL,
    created_by varchar(64) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(64) NOT NULL,
    CONSTRAINT completion_action_trigger CHECK (trigger_outcome IN ('APPROVED', 'REJECTED', 'ANY')),
    CONSTRAINT completion_action_type CHECK (action_type IN ('INSERT_ROW')),
    CONSTRAINT completion_action_not_self CHECK (source_ledger_code <> target_ledger_code),
    CONSTRAINT completion_action_mapping_array CHECK (jsonb_typeof(field_mapping) = 'array')
);

CREATE INDEX completion_action_source_idx
    ON business_meta.completion_action (tenant_id, source_ledger_code, process_key)
    WHERE enabled;

CREATE TABLE business_meta.completion_action_run (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    action_id uuid NOT NULL REFERENCES business_meta.completion_action (id),
    source_ledger_code varchar(128) NOT NULL,
    source_record_id uuid NOT NULL,
    process_instance_id uuid,
    outcome varchar(16) NOT NULL,
    status varchar(16) NOT NULL,
    target_record_id uuid,
    attempts integer NOT NULL DEFAULT 0,
    error_code varchar(64),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT completion_action_run_status CHECK (status IN ('PENDING', 'DONE', 'FAILED')),
    CONSTRAINT completion_action_run_outcome CHECK (outcome IN ('APPROVED', 'REJECTED')),
    CONSTRAINT completion_action_run_once UNIQUE (tenant_id, action_id, source_record_id)
);

CREATE INDEX completion_action_run_action_idx
    ON business_meta.completion_action_run (tenant_id, action_id, created_at DESC);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'platform' AND table_name = 'table_registry'
    ) THEN
        INSERT INTO platform.table_registry (
            id, table_schema, table_name, scope, tenant_required, schema_version,
            owner_module, created_at, updated_at, version
        ) VALUES
            (gen_random_uuid(), 'business_meta', 'completion_action', 'tenant', true, 1,
             'nexus-p3-studio', now(), now(), 1),
            (gen_random_uuid(), 'business_meta', 'completion_action_run', 'tenant', true, 1,
             'nexus-p3-studio', now(), now(), 1)
        ON CONFLICT (table_schema, table_name) DO NOTHING;
    END IF;
END $$;
