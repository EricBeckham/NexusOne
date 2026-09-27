-- DPO-L6-pre: recorded upgrade steps for an installation. Schema only (DPO-L6 adds the controlled write + read).
-- Honesty rule (XB6 / XB13): a step with no row here is UNKNOWN. UNKNOWN is never stored, and nothing in this
-- schema defaults a step to DONE — a row exists only when an operator records one through a controlled action.
-- Rows are append-only; the latest row per (installation, version, step) is the step's state for that upgrade.

CREATE TABLE market.upgrade_step_record (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    installation_id uuid NOT NULL REFERENCES market.installation(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES market.app_version(id),
    step_key varchar(40) NOT NULL,
    status varchar(16) NOT NULL,
    recorded_at timestamptz NOT NULL,
    recorded_by uuid NOT NULL,
    reason text NOT NULL,
    note text,
    CONSTRAINT upgrade_step_record_step CHECK (
        step_key IN ('precheck', 'dependency', 'schema_migration', 'config', 'switch', 'verify')),
    CONSTRAINT upgrade_step_record_status CHECK (status IN ('ACTIVE', 'DONE', 'FAILED')),
    CONSTRAINT upgrade_step_record_reason CHECK (length(btrim(reason)) > 0)
);

CREATE INDEX ix_upgrade_step_record_latest
    ON market.upgrade_step_record (installation_id, version_id, step_key, recorded_at DESC);
CREATE INDEX ix_upgrade_step_record_tenant
    ON market.upgrade_step_record (tenant_id, installation_id);
