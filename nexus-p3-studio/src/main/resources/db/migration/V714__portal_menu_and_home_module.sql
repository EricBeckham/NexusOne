-- PLC2: tenant portal menu and home modules. No API behavior in this migration.
-- Version is provisional. Merge after V712 and PLB1's V713, and renumber to max+1 if either number moved.

CREATE TABLE business_meta.portal_menu_item (
    tenant_id uuid NOT NULL,
    item_id uuid NOT NULL,
    parent_id uuid,
    label varchar(80) NOT NULL,
    entry_key varchar(80),
    section varchar(40),
    sort_order integer NOT NULL,
    hidden boolean NOT NULL,
    CONSTRAINT portal_menu_item_pk PRIMARY KEY (tenant_id, item_id)
);

CREATE INDEX portal_menu_item_tenant_sort ON business_meta.portal_menu_item (tenant_id, sort_order, item_id);

CREATE TABLE business_meta.portal_home_module (
    tenant_id uuid NOT NULL,
    module_code varchar(24) NOT NULL,
    title varchar(80) NOT NULL,
    sort_order integer NOT NULL,
    hidden boolean NOT NULL,
    config jsonb NOT NULL,
    CONSTRAINT portal_home_module_pk PRIMARY KEY (tenant_id, module_code),
    CONSTRAINT portal_home_module_config_object CHECK (jsonb_typeof(config) = 'object')
);
