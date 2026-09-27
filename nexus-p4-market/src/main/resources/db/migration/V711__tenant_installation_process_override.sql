-- V711: V710 was allocated to completion actions on main before this PR merged.
-- The immutable install-time product pin is the platform default.
-- A tenant override is separate: it never rewrites a global definition or another tenant's pin.
ALTER TABLE market.installation
    ADD CONSTRAINT uk_installation_id_tenant UNIQUE (id, tenant_id);

CREATE TABLE market.installation_process_override (
    installation_id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    process_definition_key varchar(120) NOT NULL,
    process_definition_version integer NOT NULL CHECK (process_definition_version > 0),
    updated_by uuid NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT ck_installation_process_override_key CHECK (length(trim(process_definition_key)) > 0),
    CONSTRAINT fk_installation_process_override_owner FOREIGN KEY (installation_id, tenant_id)
        REFERENCES market.installation (id, tenant_id) ON DELETE CASCADE
);
