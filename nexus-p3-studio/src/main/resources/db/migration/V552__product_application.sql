-- K14: tenant product applications for a pinned form template version.
-- First version does not expose WITHDRAWN. No API in this migration.
-- form_template_version_id is a uuid only: the platform version table is V005,
-- and this script must still migrate on a P3 classpath that does not load V005.
-- installation_id / product_definition_id stay unconstrained here; those tables
-- arrive in later Flyway versions (V600 / V601).

CREATE TABLE business_meta.product_application (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    installation_id uuid,
    product_definition_id uuid,
    form_template_version_id uuid NOT NULL,
    process_instance_id uuid REFERENCES business_meta.process_instance (id),
    values_json jsonb NOT NULL,
    status varchar(16) NOT NULL,
    initiator_person_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT product_application_status CHECK (status IN ('RUNNING', 'APPROVED', 'REJECTED')),
    CONSTRAINT product_application_product_ref CHECK (
        installation_id IS NOT NULL OR product_definition_id IS NOT NULL
    )
);

CREATE INDEX product_application_tenant_created_idx
    ON business_meta.product_application (tenant_id, created_at DESC);

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
            gen_random_uuid(), 'business_meta', 'product_application', 'tenant', true, 1,
            'nexus-p3-studio', now(), now(), 1
        ) ON CONFLICT (table_schema, table_name) DO NOTHING;
    END IF;
END $$;
