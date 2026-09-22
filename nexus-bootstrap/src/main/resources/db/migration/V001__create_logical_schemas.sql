-- M0-01: logical schemas for the modular monolith.
-- Flyway manages platform static objects only.
-- P3 dynamic ledger tables (l_* / a_* in business_data) are owned by SchemaService, never by Flyway.
-- Flowable engine tables are owned by the engine in schema flowable; do not create them here.

CREATE SCHEMA IF NOT EXISTS platform;
CREATE SCHEMA IF NOT EXISTS iam;
CREATE SCHEMA IF NOT EXISTS business_meta;
CREATE SCHEMA IF NOT EXISTS business_data;

COMMENT ON SCHEMA platform IS 'P1 platform static tables. Flyway-managed.';
COMMENT ON SCHEMA iam IS 'P2 identity/org tables. Flyway-managed when M0-04 adds migrations (V200+).';
COMMENT ON SCHEMA business_meta IS 'P3 application/ledger metadata. Flyway-managed when M0-06 adds migrations (V400+). Not physical ledger data.';
COMMENT ON SCHEMA business_data IS 'P3 physical ledger tables. SchemaService only. Flyway must not CREATE TABLE here.';

GRANT USAGE ON SCHEMA platform TO CURRENT_USER;
GRANT USAGE ON SCHEMA iam TO CURRENT_USER;
GRANT USAGE ON SCHEMA business_meta TO CURRENT_USER;
GRANT USAGE ON SCHEMA business_data TO CURRENT_USER;
