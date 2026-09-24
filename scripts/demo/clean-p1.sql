-- P1 V-SEED-P1 cleanup. Run only with scripts/demo/clean-p1.ps1 (demo gate + loopback guard).
BEGIN;
DELETE FROM platform.quota_idempotency WHERE tenant_id = '01900000-0000-7000-8000-000000000101';
DELETE FROM platform.quota WHERE tenant_id = '01900000-0000-7000-8000-000000000101';
DELETE FROM platform.entitlement_projection WHERE tenant_id = '01900000-0000-7000-8000-000000000101';
DELETE FROM platform.tenant_config WHERE tenant_id = '01900000-0000-7000-8000-000000000101';
DELETE FROM platform.tenant WHERE id = '01900000-0000-7000-8000-000000000101' AND code = 'demo_acme';
COMMIT;
