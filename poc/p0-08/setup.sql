-- Dedicated PoC schema. Requires CREATE privilege. Does not touch application schemas.
CREATE SCHEMA IF NOT EXISTS p008;

CREATE TABLE IF NOT EXISTS p008.capacity_limit (
  singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
  max_dynamic_tables integer NOT NULL CHECK (max_dynamic_tables > 0),
  max_indexes integer NOT NULL CHECK (max_indexes > 0)
);
INSERT INTO p008.capacity_limit(singleton, max_dynamic_tables, max_indexes)
VALUES (true, 100000, 300000) ON CONFLICT (singleton) DO NOTHING;

CREATE TABLE IF NOT EXISTS p008.tenant_quota (
  tenant_id text PRIMARY KEY,
  tenant_code text NOT NULL UNIQUE,
  custom_ledger_quota integer NOT NULL CHECK (custom_ledger_quota >= 0),
  custom_ledgers_used integer NOT NULL DEFAULT 0 CHECK (custom_ledgers_used >= 0)
);

CREATE TABLE IF NOT EXISTS p008.table_registry (
  table_name text PRIMARY KEY,
  scope text NOT NULL CHECK (scope IN ('tenant', 'app_shared')),
  tenant_required boolean NOT NULL DEFAULT true CHECK (tenant_required),
  owner_tenant_id text,
  owner_module text NOT NULL,
  schema_version integer NOT NULL DEFAULT 1 CHECK (schema_version > 0),
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT owner_scope CHECK ((scope = 'tenant') = (owner_tenant_id IS NOT NULL))
);

CREATE TABLE IF NOT EXISTS p008.ddl_sample (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  created_at timestamptz NOT NULL DEFAULT now(),
  table_name text NOT NULL,
  scope text NOT NULL,
  duration_ms numeric NOT NULL
);

-- Schema AST is a fixed system-field list plus optional validated business fields.
-- Identifiers are rendered only with format('%I', ...); values use USING/normal SQL.
CREATE OR REPLACE FUNCTION p008.validate_name(p_value text) RETURNS text
LANGUAGE plpgsql IMMUTABLE AS $$
BEGIN
  IF p_value IS NULL OR p_value !~ '^[a-z][a-z0-9_]{0,23}$' THEN
    RAISE EXCEPTION 'invalid ledger identifier: %', p_value;
  END IF;
  RETURN p_value;
END $$;

CREATE OR REPLACE FUNCTION p008.create_ledger_table(
  p_table_name text, p_scope text, p_owner_tenant_id text, p_owner_module text,
  p_fields jsonb DEFAULT '[]'::jsonb
) RETURNS void LANGUAGE plpgsql AS $$
DECLARE
  v_sql text;
  v_field jsonb;
  v_name text;
  v_type text;
  v_started timestamptz;
  v_tables integer;
  v_indexes integer;
  v_limit p008.capacity_limit%ROWTYPE;
BEGIN
  IF p_scope NOT IN ('tenant', 'app_shared') OR length(p_table_name) > 52
     OR p_table_name !~ '^[la]_[a-z0-9_]+$' THEN
    RAISE EXCEPTION 'invalid table scope/name';
  END IF;
  IF jsonb_typeof(p_fields) <> 'array' THEN RAISE EXCEPTION 'fields must be array'; END IF;
  -- Global serialization makes quota and catalog caps atomic across sessions.
  PERFORM pg_advisory_xact_lock(8008, 1);
  SELECT * INTO STRICT v_limit FROM p008.capacity_limit WHERE singleton;
  SELECT count(*) INTO v_tables FROM p008.table_registry;
  SELECT count(*) INTO v_indexes FROM pg_indexes WHERE schemaname = 'p008' AND tablename IN
    (SELECT table_name FROM p008.table_registry);
  IF v_tables >= v_limit.max_dynamic_tables OR v_indexes + 2 > v_limit.max_indexes THEN
    RAISE EXCEPTION 'dynamic table/index cap reached';
  END IF;
  IF EXISTS (SELECT 1 FROM p008.table_registry WHERE table_name = p_table_name) THEN
    RAISE EXCEPTION 'ledger already registered: %', p_table_name;
  END IF;
  v_sql := format('CREATE TABLE p008.%I (id uuid PRIMARY KEY, tenant_id text NOT NULL, '
    || 'created_at timestamptz NOT NULL DEFAULT now(), created_by text NOT NULL, '
    || 'updated_at timestamptz NOT NULL DEFAULT now(), updated_by text NOT NULL, '
    || 'deleted_at timestamptz, version bigint NOT NULL DEFAULT 1, '
    || 'schema_version integer NOT NULL DEFAULT 1, ext_data jsonb NOT NULL DEFAULT ''{}''::jsonb', p_table_name);
  FOR v_field IN SELECT value FROM jsonb_array_elements(p_fields) LOOP
    v_name := p008.validate_name(v_field->>'name');
    v_type := v_field->>'type';
    IF v_name IN ('id','tenant_id','created_at','created_by','updated_at','updated_by','deleted_at','version','schema_version','ext_data')
       OR v_type NOT IN ('text', 'numeric', 'timestamptz') THEN
      RAISE EXCEPTION 'invalid business field/type: % %', v_name, v_type;
    END IF;
    v_sql := v_sql || format(', %I %s', v_name, v_type);
  END LOOP;
  v_sql := v_sql || ')';
  v_started := clock_timestamp();
  EXECUTE v_sql;
  EXECUTE format('CREATE INDEX %I ON p008.%I (tenant_id, deleted_at)', p_table_name || '_tenant_idx', p_table_name);
  INSERT INTO p008.table_registry(table_name, scope, owner_tenant_id, owner_module)
  VALUES (p_table_name, p_scope, p_owner_tenant_id, p_owner_module);
  INSERT INTO p008.ddl_sample(table_name, scope, duration_ms)
  VALUES (p_table_name, p_scope, extract(epoch FROM clock_timestamp() - v_started) * 1000);
END $$;

CREATE OR REPLACE FUNCTION p008.schema_service_create_custom(
  p_tenant_id text, p_tenant_code text, p_ledger_code text, p_fields jsonb DEFAULT '[]'::jsonb
) RETURNS text LANGUAGE plpgsql AS $$
DECLARE v_quota p008.tenant_quota%ROWTYPE; v_table text;
BEGIN
  PERFORM p008.validate_name(p_tenant_code);
  PERFORM p008.validate_name(p_ledger_code);
  v_table := 'l_' || p_tenant_code || '_' || p_ledger_code;
  SELECT * INTO STRICT v_quota FROM p008.tenant_quota WHERE tenant_id = p_tenant_id FOR UPDATE;
  IF v_quota.tenant_code <> p_tenant_code THEN RAISE EXCEPTION 'tenant code mismatch'; END IF;
  IF v_quota.custom_ledgers_used >= v_quota.custom_ledger_quota THEN RAISE EXCEPTION 'custom ledger quota exceeded'; END IF;
  PERFORM p008.create_ledger_table(v_table, 'tenant', p_tenant_id, 'P3', p_fields);
  UPDATE p008.tenant_quota SET custom_ledgers_used = custom_ledgers_used + 1 WHERE tenant_id = p_tenant_id;
  RETURN v_table;
END $$;

CREATE OR REPLACE FUNCTION p008.schema_service_create_shared(
  p_app_code text, p_ledger_code text, p_fields jsonb DEFAULT '[]'::jsonb
) RETURNS text LANGUAGE plpgsql AS $$
DECLARE v_table text;
BEGIN
  v_table := 'a_' || p008.validate_name(p_app_code) || '_' || p008.validate_name(p_ledger_code);
  PERFORM p008.create_ledger_table(v_table, 'app_shared', NULL, p_app_code, p_fields);
  RETURN v_table;
END $$;

CREATE OR REPLACE FUNCTION p008.schema_drift_check(p_batch_size integer, p_offset integer DEFAULT 0)
RETURNS TABLE(table_name text, issue text) LANGUAGE sql STABLE AS $$
  WITH batch AS (
    SELECT r.table_name FROM p008.table_registry r ORDER BY r.table_name
    LIMIT greatest(p_batch_size, 0) OFFSET greatest(p_offset, 0)
  )
  SELECT b.table_name,
    CASE WHEN c.oid IS NULL THEN 'missing_table'
         WHEN a.attname IS NULL OR a.attnotnull = false THEN 'tenant_id_missing_or_nullable'
         ELSE NULL END
  FROM batch b
  LEFT JOIN pg_class c ON c.oid = to_regclass(format('p008.%I', b.table_name)) AND c.relkind = 'r'
  LEFT JOIN pg_attribute a ON a.attrelid = c.oid AND a.attname = 'tenant_id' AND a.attisdropped = false
  WHERE c.oid IS NULL OR a.attname IS NULL OR a.attnotnull = false
$$;
