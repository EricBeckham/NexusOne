# Shared G6 recovery-drill helpers (ops only). Sourced by run-drill.sh and
# run-single-tenant.sh. Never touches haopan_platform. Never uses port 5432.
# Does not change product runtime. Does not claim G17 or minute-level RPO.

now_ms() { python3 -c 'import time; print(int(time.time() * 1000))'; }
log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }
fail() { echo "FAIL: $*" >&2; exit 1; }

refuse_forbidden_name() {
  local name="$1"
  local kind="$2"
  if echo "$name" | grep -Eiq '(^|[^a-z0-9])haopan(_platform)?([^a-z0-9]|$)'; then
    fail "refusing $kind name that mentions haopan: $name"
  fi
  case "$name" in
    postgres|template0|template1)
      fail "refusing system $kind name: $name"
      ;;
  esac
}

refuse_dedicated_port() {
  if [ "${PGPORT}" = "5432" ]; then
    fail "refusing default product port 5432; G6 drill uses a dedicated cluster (default 55432)"
  fi
}

require_pg15() {
  if [ ! -x "$PG15_BIN/pg_dump" ] || [ ! -x "$PG15_BIN/pg_restore" ] || [ ! -x "$PG15_BIN/initdb" ]; then
    fail "PostgreSQL 15 binaries missing under $PG15_BIN"
  fi
}

start_pg() {
  if "$PG15_BIN/pg_isready" -h "$PGHOST" -p "$PGPORT" >/dev/null 2>&1; then
    log "PostgreSQL already accepting on ${PGHOST}:${PGPORT}"
    return
  fi
  if [ ! -f "$PGDATA/PG_VERSION" ]; then
    log "initdb dedicated cluster at $PGDATA"
    "$PG15_BIN/initdb" -D "$PGDATA" --username="$PGUSER" --auth-local=trust --auth-host=trust --no-instructions
    cat >>"$PGDATA/postgresql.conf" <<EOF
listen_addresses = '127.0.0.1'
port = ${PGPORT}
unix_socket_directories = '${PGDATA}'
logging_collector = off
EOF
  fi
  log "starting dedicated PostgreSQL 15 on ${PGHOST}:${PGPORT}"
  "$PG15_BIN/pg_ctl" -D "$PGDATA" -l "$WORK/logs/postgres.log" -w start -o "-p ${PGPORT} -k ${PGDATA} -h 127.0.0.1"
}

psql_q() {
  "$PG15_BIN/psql" -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -v ON_ERROR_STOP=1 "$@"
}

assert_no_haopan() {
  local found
  found="$(psql_q -d postgres -Atc "SELECT datname FROM pg_database WHERE datname ILIKE '%haopan%';")"
  if [ -n "$found" ]; then
    fail "dedicated cluster unexpectedly contains haopan database(s): $found"
  fi
}

# COPY SHA-256 of every row (used by the dual-tenant whole-DB drill).
table_sha() {
  local db="$1" qual="$2" order="$3"
  psql_q -d "$db" -Atc "COPY (SELECT * FROM ${qual} ORDER BY ${order}) TO STDOUT WITH CSV" \
    | sha256sum | awk '{print $1}'
}

# COPY SHA-256 of one tenant's business rows. Platform-only tables must not use this.
# tenant is require_uuid'd by the caller; interpolated only as a quoted UUID literal.
table_sha_tenant() {
  local db="$1" qual="$2" order="$3" tenant="$4"
  require_uuid "$tenant" tenant_id
  psql_q -d "$db" -Atc \
    "COPY (SELECT * FROM ${qual} WHERE tenant_id = '${tenant}'::uuid ORDER BY ${order}) TO STDOUT WITH CSV" \
    | sha256sum | awk '{print $1}'
}

# Stream one tenant's rows from src into dest (already has schema, no other tenants).
copy_tenant_rows() {
  local src="$1" dest="$2" qual="$3" tenant="$4"
  require_uuid "$tenant" tenant_id
  "$PG15_BIN/psql" -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -q -v ON_ERROR_STOP=1 \
    -d "$src" -c \
    "COPY (SELECT * FROM ${qual} WHERE tenant_id = '${tenant}'::uuid) TO STDOUT" \
    | "$PG15_BIN/psql" -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -q -v ON_ERROR_STOP=1 \
      -d "$dest" -c "COPY ${qual} FROM STDIN"
}

require_uuid() {
  local value="$1"
  local kind="$2"
  if ! echo "$value" | grep -Eq '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'; then
    fail "refusing ${kind}: not a UUID: $value"
  fi
}

apply_fixture_object_hashes() {
  local db="$1"
  printf 'g6-tenant-a-leave-payload-v1\n' >"$WORK/objects/file-a.bin"
  printf 'g6-tenant-b-expense-payload-v1\n' >"$WORK/objects/file-b.bin"
  SHA_A="$(sha256sum "$WORK/objects/file-a.bin" | awk '{print $1}')"
  SHA_B="$(sha256sum "$WORK/objects/file-b.bin" | awk '{print $1}')"
  LEN_A="$(wc -c <"$WORK/objects/file-a.bin" | tr -d ' ')"
  LEN_B="$(wc -c <"$WORK/objects/file-b.bin" | tr -d ' ')"
  psql_q -d "$db" -c "UPDATE files.file_object SET sha256='${SHA_A}', byte_length=${LEN_A} WHERE file_id='bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1';"
  psql_q -d "$db" -c "UPDATE files.file_object SET sha256='${SHA_B}', byte_length=${LEN_B} WHERE file_id='bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2';"
}

seed_source_db() {
  local db="$1"
  log "creating dedicated source database ${db}"
  psql_q -d postgres -c "DROP DATABASE IF EXISTS ${db};"
  psql_q -d postgres -c "CREATE DATABASE ${db} TEMPLATE template0 ENCODING 'UTF8';"
  psql_q -d "$db" -f "$SCRIPT_DIR/seed-source.sql" >/dev/null
  apply_fixture_object_hashes "$db"
}
