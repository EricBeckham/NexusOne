-- N17: optional owner for self-use API catalog rows.
-- NULL means the entry is shared with every issued tenant. A non-null value is visible
-- only to that tenant. This column does not store a key, a call count, or a success rate.

ALTER TABLE integration.api_catalog_entry
    ADD COLUMN tenant_id uuid NULL;

CREATE INDEX ix_api_catalog_entry_tenant
    ON integration.api_catalog_entry (tenant_id);
