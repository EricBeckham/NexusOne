-- P5 V-SEED-P5 cleanup. Run only with scripts/demo/clean-p5.ps1 (demo gate + loopback guard).
BEGIN;
DELETE FROM integration.api_call_log WHERE id = '01900000-0000-7000-8000-000000000502'
  AND tenant_id = '01900000-0000-7000-8000-000000000101';
DELETE FROM integration.connector_run WHERE id = '01900000-0000-7000-8000-000000000503'
  AND tenant_id = '01900000-0000-7000-8000-000000000101';
DELETE FROM integration.api_client WHERE id = '01900000-0000-7000-8000-000000000501'
  AND tenant_id = '01900000-0000-7000-8000-000000000101' AND key_prefix = 'DEMO-REVOKED';
COMMIT;
