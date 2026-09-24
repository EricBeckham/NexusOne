-- P4 V-SEED-P4 cleanup. Run only with scripts/demo/clean-p4.ps1 (demo gate + loopback guard).
BEGIN;
DELETE FROM market.sku WHERE code = 'demo.project-hub.standard';
DELETE FROM market.app_version WHERE id = '01900000-0000-7000-8000-000000000402'
  AND app_id = '01900000-0000-7000-8000-000000000401';
DELETE FROM market.app_package WHERE id = '01900000-0000-7000-8000-000000000401'
  AND code = 'demo_project_hub';
COMMIT;
