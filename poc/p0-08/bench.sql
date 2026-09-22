\set ON_ERROR_STOP on
\timing on
\ir setup.sql
-- Inputs are psql numeric variables supplied by bench.ps1.
CREATE OR REPLACE FUNCTION p008.bench_create(p_tenant_count integer, p_custom_per_tenant integer, p_shared_tables integer)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE t integer; l integer; a integer; v_tenant text; v_code text;
BEGIN
  FOR t IN 1..p_tenant_count LOOP
    v_tenant := 'bench-' || t;
    v_code := 't' || t;
    INSERT INTO p008.tenant_quota(tenant_id, tenant_code, custom_ledger_quota)
    VALUES (v_tenant, v_code, p_custom_per_tenant) ON CONFLICT (tenant_id) DO NOTHING;
    FOR l IN 1..p_custom_per_tenant LOOP
      IF NOT EXISTS (SELECT 1 FROM p008.table_registry WHERE table_name = 'l_' || v_code || '_c' || l) THEN
        PERFORM p008.schema_service_create_custom(v_tenant, v_code, 'c' || l);
      END IF;
    END LOOP;
  END LOOP;
  FOR a IN 1..p_shared_tables LOOP
    IF NOT EXISTS (SELECT 1 FROM p008.table_registry WHERE table_name = 'a_bench_s' || a) THEN
      PERFORM p008.schema_service_create_shared('bench', 's' || a);
    END IF;
  END LOOP;
END $$;
SELECT p008.bench_create(:tenant_count, :custom_per_tenant, :shared_tables);

-- 1,000-tenant shared-table model: one row per tenant in each shared table.
CREATE OR REPLACE FUNCTION p008.bench_shared_rows(p_tenant_count integer, p_shared_tables integer)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE a integer; t integer; v_table text;
BEGIN
  FOR a IN 1..p_shared_tables LOOP
    v_table := 'a_bench_s' || a;
    FOR t IN 1..p_tenant_count LOOP
      EXECUTE format('INSERT INTO p008.%I (id,tenant_id,created_by,updated_by) '
        || 'VALUES (md5($1)::uuid,$2,''bench'',''bench'') ON CONFLICT (id) DO NOTHING', v_table)
        USING v_table || ':' || t, 'bench-' || t;
    END LOOP;
  END LOOP;
END $$;
SELECT p008.bench_shared_rows(:tenant_count, :shared_tables);

SELECT current_setting('server_version') AS postgres_version,
       count(*) FILTER (WHERE scope = 'tenant') AS custom_tables,
       count(*) FILTER (WHERE scope = 'app_shared') AS shared_tables
FROM p008.table_registry;
SELECT count(*) AS indexes_on_dynamic_tables FROM pg_indexes
WHERE schemaname = 'p008' AND tablename IN (SELECT table_name FROM p008.table_registry);
SELECT percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_ms) AS ddl_p95_ms,
       max(duration_ms) AS ddl_max_ms FROM p008.ddl_sample;
CREATE TEMP TABLE catalog_sample(duration_ms numeric);
DO $$
DECLARE i integer; v_started timestamptz; v_count bigint;
BEGIN
  FOR i IN 1..100 LOOP
    v_started := clock_timestamp();
    SELECT count(*) INTO v_count FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'p008' AND c.relkind = 'r';
    INSERT INTO catalog_sample VALUES (extract(epoch FROM clock_timestamp() - v_started) * 1000);
  END LOOP;
END $$;
SELECT percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_ms) AS catalog_p95_ms,
       max(duration_ms) AS catalog_max_ms FROM catalog_sample;
CREATE TEMP TABLE drift_sample(duration_ms numeric, issues integer);
DO $$
DECLARE v_offset integer; v_total integer; v_issues integer; v_started timestamptz;
BEGIN
  SELECT count(*) INTO v_total FROM p008.table_registry;
  v_offset := 0;
  WHILE v_offset < v_total LOOP
    v_started := clock_timestamp();
    SELECT count(*) INTO v_issues FROM p008.schema_drift_check(100, v_offset);
    INSERT INTO drift_sample VALUES (extract(epoch FROM clock_timestamp() - v_started) * 1000, v_issues);
    v_offset := v_offset + 100;
  END LOOP;
END $$;
SELECT count(*) AS drift_batches, sum(issues) AS drift_issues,
       percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_ms) AS drift_batch_p95_ms
FROM drift_sample;
EXPLAIN (ANALYZE, BUFFERS) SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'p008' AND c.relkind = 'r';
