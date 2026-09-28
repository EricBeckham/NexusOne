-- PLC6: tenant application instance pin, per-object override, seen instance events, and position mapping.
-- No API behavior in this migration. Resolution and portal code live in later cards.

CREATE TABLE business_meta.instance_object_override (
    tenant_id uuid NOT NULL,
    instance_id uuid NOT NULL,
    object_type varchar(16) NOT NULL,
    object_code varchar(24) NOT NULL,
    base_version varchar(32) NOT NULL,
    content jsonb NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(64) NOT NULL,
    CONSTRAINT instance_object_override_pk PRIMARY KEY (tenant_id, instance_id, object_type, object_code),
    CONSTRAINT instance_object_override_type CHECK (object_type IN ('FORM', 'PROCESS', 'ROLE')),
    CONSTRAINT instance_object_override_content_object CHECK (jsonb_typeof(content) = 'object')
);

CREATE TABLE business_meta.app_instance_state (
    tenant_id uuid NOT NULL,
    instance_id uuid NOT NULL,
    instance_revision bigint NOT NULL,
    product_code varchar(24) NOT NULL,
    app_code varchar(24) NOT NULL,
    version varchar(32),
    previous_version varchar(32),
    mode varchar(16),
    entries jsonb NOT NULL,
    status varchar(16) NOT NULL,
    CONSTRAINT app_instance_state_pk PRIMARY KEY (tenant_id, instance_id),
    CONSTRAINT app_instance_state_status CHECK (status IN ('INSTALLED', 'UNINSTALLED')),
    CONSTRAINT app_instance_state_entries CHECK (jsonb_typeof(entries) = 'array')
);

CREATE TABLE business_meta.app_instance_event (
    tenant_id uuid NOT NULL,
    event_id uuid NOT NULL,
    instance_id uuid NOT NULL,
    CONSTRAINT app_instance_event_pk PRIMARY KEY (tenant_id, event_id)
);

CREATE TABLE business_meta.instance_position_mapping (
    tenant_id uuid NOT NULL,
    instance_id uuid NOT NULL,
    position_code varchar(24) NOT NULL,
    position_id uuid NOT NULL,
    CONSTRAINT instance_position_mapping_pk PRIMARY KEY (tenant_id, instance_id, position_code)
);
