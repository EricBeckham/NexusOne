-- Run ONLY on a disposable empty PostgreSQL database, never a business database.
CREATE SCHEMA market;
CREATE TABLE market.installation (id uuid PRIMARY KEY, tenant_id uuid NOT NULL);
INSERT INTO market.installation VALUES ('00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000011');
\ir ../../main/resources/db/migration/V711__tenant_installation_process_override.sql
INSERT INTO market.installation_process_override VALUES ('00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000011','tenant-custom',1,'00000000-0000-0000-0000-000000000021',CURRENT_TIMESTAMP);
DO $$ BEGIN
  BEGIN
    UPDATE market.installation_process_override SET tenant_id='00000000-0000-0000-0000-000000000012';
    RAISE EXCEPTION 'wrong tenant was accepted';
  EXCEPTION WHEN foreign_key_violation THEN RAISE NOTICE 'PASS: wrong tenant rejected'; END;
  BEGIN
    UPDATE market.installation_process_override SET process_definition_version=0;
    RAISE EXCEPTION 'zero version was accepted';
  EXCEPTION WHEN check_violation THEN RAISE NOTICE 'PASS: zero version rejected'; END;
  BEGIN
    INSERT INTO market.installation_process_override SELECT * FROM market.installation_process_override;
    RAISE EXCEPTION 'duplicate override was accepted';
  EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'PASS: duplicate override rejected'; END;
  BEGIN
    UPDATE market.installation_process_override SET process_definition_key=' ';
    RAISE EXCEPTION 'blank key was accepted';
  EXCEPTION WHEN check_violation THEN RAISE NOTICE 'PASS: blank key rejected'; END;
END $$;
DELETE FROM market.installation;
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM market.installation_process_override) THEN RAISE EXCEPTION 'cascade failed'; END IF;
 RAISE NOTICE 'PASS: cascade cleanup';
END $$;
