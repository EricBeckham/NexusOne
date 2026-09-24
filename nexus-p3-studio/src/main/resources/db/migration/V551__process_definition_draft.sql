-- M5: tenant-owned process definition drafts. Published process state belongs to M10.
CREATE TABLE business_meta.process_definition_draft (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES platform.tenant(id),
    process_key varchar(64) NOT NULL,
    name varchar(160) NOT NULL,
    bpmn_xml text NOT NULL,
    version integer NOT NULL DEFAULT 1 CHECK (version > 0),
    updated_by uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_process_definition_draft_tenant_key UNIQUE (tenant_id, process_key)
);

CREATE INDEX ix_process_definition_draft_tenant_updated
    ON business_meta.process_definition_draft (tenant_id, updated_at DESC);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'platform' AND table_name = 'table_registry'
    ) THEN
        INSERT INTO platform.table_registry (
            id, table_schema, table_name, scope, tenant_required, schema_version,
            owner_module, created_at, updated_at, version
        ) VALUES (
            gen_random_uuid(), 'business_meta', 'process_definition_draft', 'tenant', true, 1,
            'nexus-p3-studio', now(), now(), 1
        ) ON CONFLICT (table_schema, table_name) DO NOTHING;
    END IF;
END $$;
