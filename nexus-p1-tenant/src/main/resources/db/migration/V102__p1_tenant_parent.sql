-- N1: optional parent relationship for tenant/group hierarchy.
-- Existing tenants remain valid with a NULL parent; the application owns cycle prevention.

ALTER TABLE platform.tenant
    ADD COLUMN parent_tenant_id uuid NULL
        REFERENCES platform.tenant (id);

CREATE INDEX p1_tenant_parent_tenant_idx
    ON platform.tenant (parent_tenant_id);
