-- P3 V-SEED-P3 cleanup. Run only with scripts/demo/clean-p3.ps1 (demo gate + loopback guard).
BEGIN;
-- Reverse only the two quota charges introduced by this seed; leave P1's baseline tenant quotas intact.
UPDATE platform.quota q
SET used_value = GREATEST(0, q.used_value - COALESCE((
  SELECT SUM(i.amount) FROM platform.quota_idempotency i
  WHERE i.tenant_id = q.tenant_id AND i.metric = q.metric
    AND i.idempotency_key = 'p3.app:01900000-0000-7000-8000-000000000101:demo_studio'
    AND i.outcome = 'ACCEPTED'
), 0)::integer), version = q.version + 1
WHERE q.tenant_id = '01900000-0000-7000-8000-000000000101' AND q.metric = 'APP'
  AND EXISTS (SELECT 1 FROM platform.quota_idempotency i
              WHERE i.tenant_id = q.tenant_id AND i.metric = q.metric
                AND i.idempotency_key = 'p3.app:01900000-0000-7000-8000-000000000101:demo_studio'
                AND i.outcome = 'ACCEPTED');
UPDATE platform.quota q
SET used_value = GREATEST(0, q.used_value - COALESCE((
  SELECT SUM(i.amount) FROM platform.quota_idempotency i
  WHERE i.tenant_id = q.tenant_id AND i.metric = q.metric
    AND i.idempotency_key = 'p3.ledger:01900000-0000-7000-8000-000000000101:project'
    AND i.outcome = 'ACCEPTED'
), 0)::integer), version = q.version + 1
WHERE q.tenant_id = '01900000-0000-7000-8000-000000000101' AND q.metric = 'CUSTOM_LEDGER'
  AND EXISTS (SELECT 1 FROM platform.quota_idempotency i
              WHERE i.tenant_id = q.tenant_id AND i.metric = q.metric
                AND i.idempotency_key = 'p3.ledger:01900000-0000-7000-8000-000000000101:project'
                AND i.outcome = 'ACCEPTED');
DELETE FROM platform.quota_idempotency
WHERE tenant_id = '01900000-0000-7000-8000-000000000101'
  AND idempotency_key IN (
    'p3.app:01900000-0000-7000-8000-000000000101:demo_studio',
    'p3.ledger:01900000-0000-7000-8000-000000000101:project'
  );
DELETE FROM business_meta.ledger_field WHERE ledger_id = '01900000-0000-7000-8000-000000000302';
DELETE FROM business_meta.ledger WHERE id = '01900000-0000-7000-8000-000000000302'
  AND tenant_id = '01900000-0000-7000-8000-000000000101';
DELETE FROM business_meta.application WHERE id = '01900000-0000-7000-8000-000000000301'
  AND tenant_id = '01900000-0000-7000-8000-000000000101';
COMMIT;
