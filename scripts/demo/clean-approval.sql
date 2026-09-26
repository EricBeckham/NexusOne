-- K20 / leave-expense demo product cleanup. Run only via scripts/demo/clean-approval.ps1 (demo gate + loopback).
-- Does not drop Flyway history. Flowable deployments for demoSimpleApproval may linger until DB rebuild; seed is idempotent on restart.
BEGIN;
DELETE FROM market.installation_product_pin
 WHERE installation_id IN (
   SELECT id FROM market.installation WHERE app_id = '01900000-0000-7000-8000-000000000503'
 );
DELETE FROM business_meta.product_application
 WHERE product_definition_id = '01900000-0000-7000-8000-000000000505'
    OR form_template_version_id = '01900000-0000-7000-8000-000000000502'
    OR installation_id IN (
         SELECT id FROM market.installation WHERE app_id = '01900000-0000-7000-8000-000000000503'
       );
DELETE FROM market.installation WHERE app_id = '01900000-0000-7000-8000-000000000503';
DELETE FROM market.subscription WHERE sku_code = 'demo.simple-approval';
DELETE FROM market.sku_product_binding WHERE sku_code = 'demo.simple-approval';
DELETE FROM market.product_definition WHERE id = '01900000-0000-7000-8000-000000000505';
DELETE FROM market.sku WHERE code = 'demo.simple-approval';
DELETE FROM market.app_version WHERE id = '01900000-0000-7000-8000-000000000504';
DELETE FROM market.app_package WHERE id = '01900000-0000-7000-8000-000000000503';
DELETE FROM business_meta.process_definition_draft
 WHERE id = '01900000-0000-7000-8000-000000000506'
    OR (tenant_id = '01900000-0000-7000-8000-000000000101' AND process_key = 'demoSimpleApproval');
UPDATE platform.form_template SET running_version_id = NULL
 WHERE id = '01900000-0000-7000-8000-000000000501';
DELETE FROM platform.form_template_field WHERE template_id = '01900000-0000-7000-8000-000000000501';
DELETE FROM platform.form_template_version WHERE id = '01900000-0000-7000-8000-000000000502';
DELETE FROM platform.form_template WHERE id = '01900000-0000-7000-8000-000000000501';
COMMIT;
