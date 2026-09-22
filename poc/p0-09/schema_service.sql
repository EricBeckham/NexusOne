\set ON_ERROR_STOP on
BEGIN;
SELECT p008.schema_service_create_shared('p009', 'order', '[{"name":"legacy_label","type":"text"}]');
ALTER TABLE p008.a_p009_order ADD COLUMN display_name text;
INSERT INTO p008.a_p009_order (id, tenant_id, created_by, updated_by, schema_version, legacy_label)
VALUES
  ('00000000-0000-0000-0000-0000000000a1', 'A', 'u-a', 'u-a', 1, 'Alpha'),
  ('00000000-0000-0000-0000-0000000000b1', 'B', 'u-b', 'u-b', 1, 'Beta'),
  ('00000000-0000-0000-0000-0000000000c1', 'C', 'u-c', 'u-c', 1, 'Gamma');
UPDATE p008.a_p009_order
SET display_name = legacy_label, schema_version = 2, version = version + 1
WHERE tenant_id = 'A' AND display_name IS NULL;
DO $$ BEGIN
  IF (SELECT count(*) FROM p008.a_p009_order WHERE tenant_id = 'A' AND display_name = legacy_label) <> 1 THEN
    RAISE EXCEPTION 'schema-service shared table migrate failed';
  END IF;
  IF (SELECT count(*) FROM p008.a_p009_order WHERE tenant_id IN ('B','C') AND display_name IS NULL) <> 2 THEN
    RAISE EXCEPTION 'v1 tenants were rewritten';
  END IF;
  IF (SELECT count(*) FROM p008.schema_drift_check(100, 0) WHERE table_name = 'a_p009_order') <> 0 THEN
    RAISE EXCEPTION 'new shared table failed drift check';
  END IF;
END $$;
ROLLBACK;
\echo PASS schema-service shared table expand/migrate rolled back
