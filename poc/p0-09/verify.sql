\set ON_ERROR_STOP on
BEGIN;

-- One physical app ledger table shared by tenants A/B/C. TEMP keeps this PoC reversible.
CREATE TEMP TABLE a_demo_order (
  id uuid PRIMARY KEY,
  tenant_id text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by text NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by text NOT NULL,
  deleted_at timestamptz,
  version bigint NOT NULL DEFAULT 1,
  schema_version integer NOT NULL,
  ext_data jsonb NOT NULL DEFAULT '{}'::jsonb,
  legacy_label text NOT NULL
);
CREATE INDEX a_demo_order_tenant_idx ON a_demo_order (tenant_id, id);
CREATE TEMP TABLE tenant_runtime (
  tenant_id text PRIMARY KEY,
  app_version integer NOT NULL,
  old_version_retire_after timestamptz NOT NULL
);
INSERT INTO tenant_runtime VALUES
  ('A', 1, '2030-01-01 UTC'), ('B', 1, '2030-01-01 UTC'), ('C', 1, '2030-01-01 UTC');
INSERT INTO a_demo_order (id, tenant_id, created_by, updated_by, schema_version, legacy_label, ext_data) VALUES
  ('00000000-0000-0000-0000-000000000001', 'A', 'u-a', 'u-a', 1, 'Alpha', '{"tenant_note":"A-only"}'),
  ('00000000-0000-0000-0000-000000000002', 'B', 'u-b', 'u-b', 1, 'Beta', '{}'),
  ('00000000-0000-0000-0000-000000000003', 'C', 'u-c', 'u-c', 1, 'Gamma', '{}');

-- EXPAND: additive nullable column, platform-wide physical schema change.
ALTER TABLE a_demo_order ADD COLUMN display_name text;
DO $$ BEGIN
  IF (SELECT count(*) FROM a_demo_order WHERE tenant_id IN ('B','C') AND display_name IS NULL) <> 2 THEN
    RAISE EXCEPTION 'EXPAND unexpectedly changed v1 tenant rows';
  END IF;
END $$;

-- MIGRATE: only A gets v2 record rewrite; WHERE NULL makes re-run idempotent.
UPDATE a_demo_order SET display_name = legacy_label, schema_version = 2, version = version + 1
WHERE tenant_id = 'A' AND display_name IS NULL;
DO $$ DECLARE changed integer; BEGIN
  UPDATE a_demo_order SET display_name = legacy_label, schema_version = 2, version = version + 1
  WHERE tenant_id = 'A' AND display_name IS NULL;
  GET DIAGNOSTICS changed = ROW_COUNT;
  IF changed <> 0 THEN RAISE EXCEPTION 'MIGRATE is not idempotent'; END IF;
END $$;

-- SWITCH only A to v2. B/C stay on v1 and write old field.
UPDATE tenant_runtime SET app_version = 2 WHERE tenant_id = 'A';
INSERT INTO a_demo_order (id, tenant_id, created_by, updated_by, schema_version, legacy_label, display_name) VALUES
  ('00000000-0000-0000-0000-000000000004', 'A', 'u-a', 'u-a', 2, 'Alpha two', 'Alpha two'),
  ('00000000-0000-0000-0000-000000000005', 'B', 'u-b', 'u-b', 1, 'Beta two', NULL),
  ('00000000-0000-0000-0000-000000000006', 'C', 'u-c', 'u-c', 1, 'Gamma two', NULL);
DO $$ BEGIN
  IF (SELECT count(*) FROM tenant_runtime WHERE app_version = 2) <> 1 THEN
    RAISE EXCEPTION 'tenant rollout is not isolated';
  END IF;
  -- v1 projection remains the old column; v2 reads new or old during migration.
  IF (SELECT count(*) FROM a_demo_order WHERE tenant_id IN ('B','C') AND legacy_label IS NOT NULL) <> 4
    OR (SELECT count(*) FROM a_demo_order WHERE tenant_id = 'A' AND coalesce(display_name, legacy_label) IS NOT NULL) <> 2 THEN
    RAISE EXCEPTION 'mixed-version read failed';
  END IF;
  IF (SELECT count(*) FROM a_demo_order WHERE tenant_id = 'A' AND display_name IS DISTINCT FROM legacy_label) <> 0 THEN
    RAISE EXCEPTION 'v2 dual write lost v1 compatibility';
  END IF;
END $$;

-- A rollback changes runtime only. Shared physical schema and existing v2 data persist.
UPDATE tenant_runtime SET app_version = 1 WHERE tenant_id = 'A';
INSERT INTO a_demo_order (id, tenant_id, created_by, updated_by, schema_version, legacy_label) VALUES
  ('00000000-0000-0000-0000-000000000007', 'A', 'u-a', 'u-a', 1, 'Alpha after rollback');
DO $$ BEGIN
  IF (SELECT count(*) FROM a_demo_order) <> 7
    OR (SELECT count(*) FROM a_demo_order WHERE tenant_id = 'A' AND legacy_label IS NOT NULL) <> 3
    OR (SELECT count(*) FROM a_demo_order WHERE schema_version = 2 AND tenant_id = 'A') <> 2 THEN
    RAISE EXCEPTION 'rollback lost business rows or v2 data';
  END IF;
  IF (SELECT legacy_label FROM a_demo_order WHERE id = '00000000-0000-0000-0000-000000000001') <> 'Alpha'
    OR (SELECT legacy_label FROM a_demo_order WHERE id = '00000000-0000-0000-0000-000000000004') <> 'Alpha two'
    OR (SELECT legacy_label FROM a_demo_order WHERE id = '00000000-0000-0000-0000-000000000005') <> 'Beta two'
    OR (SELECT legacy_label FROM a_demo_order WHERE id = '00000000-0000-0000-0000-000000000006') <> 'Gamma two'
    OR (SELECT legacy_label FROM a_demo_order WHERE id = '00000000-0000-0000-0000-000000000007') <> 'Alpha after rollback' THEN
    RAISE EXCEPTION 'rollback changed a v1-visible business value';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_name = 'a_demo_order' AND column_name = 'display_name') THEN
    RAISE EXCEPTION 'rollback incorrectly removed shared schema';
  END IF;
  IF (SELECT ext_data->>'tenant_note' FROM a_demo_order WHERE tenant_id = 'A' ORDER BY id LIMIT 1) <> 'A-only' THEN
    RAISE EXCEPTION 'tenant override data changed';
  END IF;
END $$;

-- CONTRACT gate: supported v1 tenants OR unexpired retention both block removal.
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM tenant_runtime WHERE app_version = 1) THEN
    RAISE EXCEPTION 'contract incorrectly allowed while v1 tenants remain';
  END IF;
END $$;
UPDATE tenant_runtime SET app_version = 2;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM tenant_runtime WHERE old_version_retire_after > now()) THEN
    RAISE EXCEPTION 'contract incorrectly allowed before retention expires';
  END IF;
END $$;
UPDATE tenant_runtime SET old_version_retire_after = now() - interval '1 day';
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM tenant_runtime WHERE app_version < 2 OR old_version_retire_after > now()) THEN
    RAISE EXCEPTION 'contract guard still blocked after both conditions cleared';
  END IF;
  RAISE NOTICE 'P0-09 assertions passed; CONTRACT is eligible but no column was dropped';
END $$;

-- Roll back all PoC objects and data. Production Contract needs explicit separate review.
ROLLBACK;
