-- G18: form submit may point at the M0-06 physical ledger row.
-- Flyway window V530–V549. Does not create business_data / l_* / a_* tables.
ALTER TABLE business_meta.form_submission
    ADD COLUMN IF NOT EXISTS ledger_id uuid;
ALTER TABLE business_meta.form_submission
    ADD COLUMN IF NOT EXISTS ledger_record_id uuid;

CREATE INDEX IF NOT EXISTS form_submission_ledger_record_idx
    ON business_meta.form_submission (tenant_id, ledger_id, ledger_record_id)
    WHERE ledger_record_id IS NOT NULL;

COMMENT ON COLUMN business_meta.form_submission.ledger_id IS
    'M0-06 ledger id resolved from running form ledger_code (logical).';
COMMENT ON COLUMN business_meta.form_submission.ledger_record_id IS
    'Physical ledger row written via LedgerRecord/SchemaService on submit (G18). Null when no sink.';
COMMENT ON TABLE business_meta.form_submission IS
    'Fill against the running snapshot. G18 also writes business_data via SchemaService-managed tables.';
