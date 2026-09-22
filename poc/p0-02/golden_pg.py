"""Compile a P0-02 decision scope to parameterized SQL and execute it on PostgreSQL."""
import os
import subprocess
import sys
import tempfile

from permission_engine import PolicySnapshot, Predicate, Rule, Subject, evaluate

ALLOWED = {"id", "owner_id", "department_id", "status", "tenant_id"}
PSQL = os.environ.get("PSQL_EXE", r"D:\Software\PostgreSQL\15\bin\psql.exe")


def compile_predicate(predicate, params):
    if predicate.op == "true":
        return "TRUE"
    if predicate.op == "false":
        return "FALSE"
    if predicate.op in ("and", "or"):
        joiner = " AND " if predicate.op == "and" else " OR "
        return "(" + joiner.join(compile_predicate(item, params) for item in predicate.args) + ")"
    if predicate.op != "atom":
        raise ValueError(predicate.op)
    column, operator, value = predicate.args
    if column not in ALLOWED or operator not in ("=", "IN"):
        raise ValueError("rejected identifier or operator")
    values = value if operator == "IN" else (value,)
    parts = []
    for item in values:
        params.append(item)
        parts.append("%s = $%d" % (column, len(params)))
    return "(" + " OR ".join(parts) + ")" if len(parts) > 1 else parts[0]


def main():
    self_scope = Predicate.atom("owner_id", "=", "u1")
    dept = Predicate.atom("department_id", "=", "d1")
    share = Predicate.atom("id", "IN", ("r1",))
    context = Predicate.atom("status", "=", "active")
    subject = Subject("t1", "u1", frozenset({"employee"}), frozenset({"manager"}), frozenset({"d1"}))
    snapshot = PolicySnapshot("t1", 1, (
        Rule("role", "employee", "ledger", "read", self_scope),
        Rule("position", "manager", "ledger", "read", dept),
    ))
    decision = evaluate(subject, "t1", "ledger", "read", snapshot, record_shares=(share,), context=context)
    if not decision.allowed:
        raise SystemExit("permission decision was denied")
    params = ["t1"]
    scope_sql = compile_predicate(decision.scope, params)
    where_sql = "tenant_id = $1 AND " + scope_sql
    using = ", ".join("'%s'" % item.replace("'", "''") for item in params)
    sql = """
BEGIN;
CREATE TEMP TABLE ledger_row (
  id text PRIMARY KEY,
  tenant_id text NOT NULL,
  owner_id text NOT NULL,
  department_id text NOT NULL,
  status text NOT NULL,
  title text NOT NULL
);
INSERT INTO ledger_row VALUES
  ('r1','t1','u1','d1','active','Alpha'),
  ('r2','t1','u2','d1','active','Beta'),
  ('r3','t1','u9','d2','inactive','Gamma'),
  ('r4','t1','u9','d2','active','Delta'),
  ('r9','t2','u1','d1','active','Other');
DO $$
DECLARE got text[];
BEGIN
  EXECUTE 'SELECT array_agg(title ORDER BY id) FROM ledger_row WHERE %s' USING %s INTO got;
  IF got IS DISTINCT FROM ARRAY['Alpha','Beta']::text[] THEN
    RAISE EXCEPTION 'golden scope mismatch: %%', got;
  END IF;
  EXECUTE 'UPDATE ledger_row SET title = ''tampered'' WHERE tenant_id = $1 AND id = $2' USING 't1', 'r9';
  IF (SELECT title FROM ledger_row WHERE id = 'r9') <> 'Other' THEN
    RAISE EXCEPTION 'cross-tenant update was not rejected by the tenant predicate';
  END IF;
END $$;
ROLLBACK;
""" % (where_sql, using)
    with tempfile.NamedTemporaryFile("w", suffix=".sql", delete=False, encoding="utf-8") as handle:
        handle.write(sql)
        path = handle.name
    env = os.environ.copy()
    env.setdefault("PGHOST", "127.0.0.1")
    env.setdefault("PGPORT", "5432")
    env.setdefault("PGUSER", "postgres")
    env.setdefault("PGDATABASE", "nexus_p0_01")
    completed = subprocess.run([PSQL, "-X", "-v", "ON_ERROR_STOP=1", "-f", path], env=env, text=True, capture_output=True)
    os.remove(path)
    if completed.returncode != 0:
        sys.stderr.write(completed.stderr)
        sys.stderr.write(completed.stdout)
        raise SystemExit(completed.returncode)
    print("PASS: PostgreSQL DataScope golden test rows Alpha, Beta")
    print("SQL:", where_sql)


if __name__ == "__main__":
    main()
