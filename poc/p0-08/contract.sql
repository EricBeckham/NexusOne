\set ON_ERROR_STOP on
\ir setup.sql
BEGIN;
INSERT INTO p008.tenant_quota(tenant_id, tenant_code, custom_ledger_quota)
VALUES ('contract-alpha', 'contractalpha', 1);
SELECT p008.schema_service_create_custom('contract-alpha', 'contractalpha', 'one', '[{"name":"amount","type":"numeric"}]');
DO $$
BEGIN
  IF (SELECT custom_ledgers_used FROM p008.tenant_quota WHERE tenant_id = 'contract-alpha') <> 1 THEN
    RAISE EXCEPTION 'quota counter mismatch';
  END IF;
  IF EXISTS (SELECT 1 FROM p008.schema_drift_check(100, 0)) THEN
    RAISE EXCEPTION 'schema drift found';
  END IF;
  BEGIN
    PERFORM p008.schema_service_create_custom('contract-alpha', 'contractalpha', 'two');
    RAISE EXCEPTION 'quota enforcement failed';
  EXCEPTION WHEN raise_exception THEN
    IF SQLERRM <> 'custom ledger quota exceeded' THEN RAISE; END IF;
  END;
  BEGIN
    PERFORM p008.schema_service_create_custom('contract-alpha', 'contractalpha', 'bad;drop');
    RAISE EXCEPTION 'identifier guard failed';
  EXCEPTION WHEN raise_exception THEN
    IF SQLERRM NOT LIKE 'invalid ledger identifier:%' THEN RAISE; END IF;
  END;
END $$;
ALTER TABLE p008.l_contractalpha_one DROP COLUMN tenant_id;
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM p008.schema_drift_check(100, 0)
                 WHERE table_name = 'l_contractalpha_one' AND issue = 'tenant_id_missing_or_nullable') THEN
    RAISE EXCEPTION 'drift detector missed dropped tenant_id';
  END IF;
END $$;
ROLLBACK;
\echo PASS schema/quota/drift contract (rolled back)
