#!/usr/bin/env bash
# G6 environment-level recovery drill (ops only).
# PostgreSQL full-database restore on a dedicated cluster/database + local MinIO object restore.
#
# Never touches haopan_platform.
# Never performs single-tenant logical restore (no tenant-filtered dump/restore).
# Does not change product runtime. Does not claim G17 or minute-level RPO.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PG15_BIN="${G6_PG15_BIN:-/usr/lib/postgresql/15/bin}"
WORK="${G6_WORK_DIR:-$ROOT/.local/g6-recovery}"
PGDATA="${G6_PGDATA:-$WORK/pgdata}"
PGHOST="${G6_PGHOST:-127.0.0.1}"
PGPORT="${G6_PGPORT:-55432}"
SRC_DB="${G6_SRC_DB:-nexusone_g6_src}"
DST_DB="${G6_DST_DB:-nexusone_g6_dst}"
MINIO_BIN="${G6_MINIO_BIN:-$HOME/.local/g6-recovery/bin/minio}"
MC_BIN="${G6_MC_BIN:-$HOME/.local/g6-recovery/bin/mc}"
MINIO_DATA="${G6_MINIO_DATA:-$WORK/minio-data}"
MINIO_ADDR="${G6_MINIO_ADDR:-127.0.0.1:9100}"
MINIO_CONSOLE="${G6_MINIO_CONSOLE:-127.0.0.1:9101}"
SRC_BUCKET="${G6_SRC_BUCKET:-nexusone-g6-src}"
DST_BUCKET="${G6_DST_BUCKET:-nexusone-g6-dst}"
RESTORE_PREFIX="${G6_RESTORE_PREFIX:-restore-20260922}"
MC_ALIAS="${G6_MC_ALIAS:-g6local}"
export PATH="$PG15_BIN:$PATH"

mkdir -p "$WORK"/{logs,backup,object-backup,objects,evidence}
chmod 700 "$WORK" || true

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

refuse_forbidden_name "$SRC_DB" database
refuse_forbidden_name "$DST_DB" database
refuse_forbidden_name "$SRC_BUCKET" bucket
refuse_forbidden_name "$DST_BUCKET" bucket
if [ "$SRC_DB" = "$DST_DB" ]; then
  fail "source and restore databases must differ"
fi
if [ "$SRC_BUCKET" = "$DST_BUCKET" ]; then
  fail "source and restore buckets must differ"
fi
if [ "${PGPORT}" = "5432" ]; then
  fail "refusing default product port 5432; G6 drill uses a dedicated cluster (default 55432)"
fi

if [ ! -x "$PG15_BIN/pg_dump" ] || [ ! -x "$PG15_BIN/pg_restore" ] || [ ! -x "$PG15_BIN/initdb" ]; then
  fail "PostgreSQL 15 binaries missing under $PG15_BIN"
fi
if [ ! -x "$MINIO_BIN" ]; then
  fail "MinIO binary missing at $MINIO_BIN"
fi
if [ ! -x "$MC_BIN" ]; then
  fail "mc binary missing at $MC_BIN"
fi

# Credentials stay on the local workdir (gitignored). Never print the secret.
if [ -z "${MINIO_ROOT_USER:-}" ]; then
  MINIO_ROOT_USER="g6drill"
fi
if [ -z "${MINIO_ROOT_PASSWORD:-}" ]; then
  if [ -f "$WORK/minio.secret" ]; then
    MINIO_ROOT_PASSWORD="$(cat "$WORK/minio.secret")"
  else
    MINIO_ROOT_PASSWORD="$(python3 -c 'import secrets; print(secrets.token_urlsafe(24))')"
    umask 077
    printf '%s' "$MINIO_ROOT_PASSWORD" >"$WORK/minio.secret"
  fi
fi
export MINIO_ROOT_USER MINIO_ROOT_PASSWORD
export PGHOST PGPORT
export PGUSER="${G6_PGUSER:-$USER}"

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

start_minio() {
  if curl -fsS "http://${MINIO_ADDR}/minio/health/live" >/dev/null 2>&1; then
    log "MinIO already live on ${MINIO_ADDR}"
    return
  fi
  mkdir -p "$MINIO_DATA"
  log "starting dedicated MinIO on ${MINIO_ADDR}"
  nohup env MINIO_ROOT_USER="$MINIO_ROOT_USER" MINIO_ROOT_PASSWORD="$MINIO_ROOT_PASSWORD" \
    "$MINIO_BIN" server --address "$MINIO_ADDR" --console-address "$MINIO_CONSOLE" "$MINIO_DATA" \
    >"$WORK/logs/minio.log" 2>&1 &
  echo $! >"$WORK/minio.pid"
  for _ in $(seq 1 40); do
    if curl -fsS "http://${MINIO_ADDR}/minio/health/live" >/dev/null 2>&1; then
      return
    fi
    sleep 0.25
  done
  fail "MinIO did not become healthy on ${MINIO_ADDR}"
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

table_sha() {
  local db="$1" qual="$2" order="$3"
  psql_q -d "$db" -Atc "COPY (SELECT * FROM ${qual} ORDER BY ${order}) TO STDOUT WITH CSV" \
    | sha256sum | awk '{print $1}'
}

start_pg
start_minio
assert_no_haopan

PG_VERSION="$(psql_q -d postgres -Atc 'SHOW server_version;')"
PG_DUMP_VER="$("$PG15_BIN/pg_dump" --version)"
MINIO_VER="$("$MINIO_BIN" --version | head -n1)"
MC_VER="$("$MC_BIN" --version | head -n1)"
HOST_UNAME="$(uname -a)"
JAVA_VER="$(java -version 2>&1 | head -n1 || true)"
DRILL_START_EPOCH="$(date -u +%s)"
DRILL_START_ISO="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

log "server_version=${PG_VERSION}"
log "creating dedicated source database ${SRC_DB}"
psql_q -d postgres -c "DROP DATABASE IF EXISTS ${SRC_DB};"
psql_q -d postgres -c "DROP DATABASE IF EXISTS ${DST_DB};"
psql_q -d postgres -c "CREATE DATABASE ${SRC_DB} TEMPLATE template0 ENCODING 'UTF8';"
psql_q -d "$SRC_DB" -f "$SCRIPT_DIR/seed-source.sql" >/dev/null

# Deterministic object bytes (two tenants + platform marker).
printf 'g6-tenant-a-leave-payload-v1\n' >"$WORK/objects/file-a.bin"
printf 'g6-tenant-b-expense-payload-v1\n' >"$WORK/objects/file-b.bin"
printf 'nexusone-g6-object-restore-marker\n' >"$WORK/objects/marker.txt"
SHA_A="$(sha256sum "$WORK/objects/file-a.bin" | awk '{print $1}')"
SHA_B="$(sha256sum "$WORK/objects/file-b.bin" | awk '{print $1}')"
SHA_M="$(sha256sum "$WORK/objects/marker.txt" | awk '{print $1}')"
LEN_A="$(wc -c <"$WORK/objects/file-a.bin" | tr -d ' ')"
LEN_B="$(wc -c <"$WORK/objects/file-b.bin" | tr -d ' ')"

psql_q -d "$SRC_DB" -c "UPDATE files.file_object SET sha256='${SHA_A}', byte_length=${LEN_A} WHERE file_id='bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1';"
psql_q -d "$SRC_DB" -c "UPDATE files.file_object SET sha256='${SHA_B}', byte_length=${LEN_B} WHERE file_id='bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2';"

SRC_AUDIT_SHA="$(table_sha "$SRC_DB" platform.audit_record chain_index)"
SRC_META_SHA="$(table_sha "$SRC_DB" platform.audit_chain_meta chain_id)"
SRC_FILE_SHA="$(table_sha "$SRC_DB" files.file_object file_id)"
SRC_LEDGER_SHA="$(table_sha "$SRC_DB" ledger.sample_row id)"
SRC_FLYWAY_SHA="$(table_sha "$SRC_DB" platform.flyway_schema_history installed_rank)"
SRC_COUNTS="$(psql_q -d "$SRC_DB" -Atc "SELECT 'audit_record='||count(*) FROM platform.audit_record UNION ALL SELECT 'file_object='||count(*) FROM files.file_object UNION ALL SELECT 'sample_row='||count(*) FROM ledger.sample_row UNION ALL SELECT 'tenants='||count(DISTINCT tenant_id) FROM ledger.sample_row;")"

DUMP="$WORK/backup/${SRC_DB}.dump"
log "pg_dump --format=custom ${SRC_DB} -> ${DUMP}"
DUMP_T0="$(date -u +%s)"
"$PG15_BIN/pg_dump" --format=custom --file="$DUMP" --dbname="$SRC_DB"
DUMP_T1="$(date -u +%s)"
DUMP_SHA="$(sha256sum "$DUMP" | awk '{print $1}')"
DUMP_BYTES="$(wc -c <"$DUMP" | tr -d ' ')"

log "CREATE DATABASE ${DST_DB} and pg_restore --exit-on-error"
psql_q -d postgres -c "CREATE DATABASE ${DST_DB} TEMPLATE template0 ENCODING 'UTF8';"
RESTORE_T0="$(date -u +%s)"
"$PG15_BIN/pg_restore" --dbname="$DST_DB" --exit-on-error "$DUMP"
RESTORE_T1="$(date -u +%s)"

DST_AUDIT_SHA="$(table_sha "$DST_DB" platform.audit_record chain_index)"
DST_META_SHA="$(table_sha "$DST_DB" platform.audit_chain_meta chain_id)"
DST_FILE_SHA="$(table_sha "$DST_DB" files.file_object file_id)"
DST_LEDGER_SHA="$(table_sha "$DST_DB" ledger.sample_row id)"
DST_FLYWAY_SHA="$(table_sha "$DST_DB" platform.flyway_schema_history installed_rank)"
DST_COUNTS="$(psql_q -d "$DST_DB" -Atc "SELECT 'audit_record='||count(*) FROM platform.audit_record UNION ALL SELECT 'file_object='||count(*) FROM files.file_object UNION ALL SELECT 'sample_row='||count(*) FROM ledger.sample_row UNION ALL SELECT 'tenants='||count(DISTINCT tenant_id) FROM ledger.sample_row;")"
DST_TENANTS="$(psql_q -d "$DST_DB" -Atc "SELECT tenant_id::text FROM ledger.sample_row ORDER BY tenant_id;")"

PG_PASS=PASS
[ "$SRC_AUDIT_SHA" = "$DST_AUDIT_SHA" ] || { echo "mismatch audit_record"; PG_PASS=FAIL; }
[ "$SRC_META_SHA" = "$DST_META_SHA" ] || { echo "mismatch audit_chain_meta"; PG_PASS=FAIL; }
[ "$SRC_FILE_SHA" = "$DST_FILE_SHA" ] || { echo "mismatch file_object"; PG_PASS=FAIL; }
[ "$SRC_LEDGER_SHA" = "$DST_LEDGER_SHA" ] || { echo "mismatch sample_row"; PG_PASS=FAIL; }
[ "$SRC_FLYWAY_SHA" = "$DST_FLYWAY_SHA" ] || { echo "mismatch flyway_schema_history"; PG_PASS=FAIL; }
[ "$SRC_COUNTS" = "$DST_COUNTS" ] || { echo "mismatch counts"; PG_PASS=FAIL; }
echo "$DST_TENANTS" | grep -q '11111111-1111-4111-8111-111111111111' || { echo "missing tenant A"; PG_PASS=FAIL; }
echo "$DST_TENANTS" | grep -q '22222222-2222-4222-8222-222222222222' || { echo "missing tenant B"; PG_PASS=FAIL; }
assert_no_haopan

log "MinIO alias + source objects"
"$MC_BIN" alias set "$MC_ALIAS" "http://${MINIO_ADDR}" "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null
"$MC_BIN" mb --ignore-existing "${MC_ALIAS}/${SRC_BUCKET}" >/dev/null
"$MC_BIN" mb --ignore-existing "${MC_ALIAS}/${DST_BUCKET}" >/dev/null
# Isolated restore target: wipe previous drill objects in dest only.
"$MC_BIN" rm --recursive --force --dangerous "${MC_ALIAS}/${DST_BUCKET}" >/dev/null 2>&1 || true
"$MC_BIN" mb --ignore-existing "${MC_ALIAS}/${DST_BUCKET}" >/dev/null

KEY_A="tenants/11111111-1111-4111-8111-111111111111/leave/2026/09/file-a.bin"
KEY_B="tenants/22222222-2222-4222-8222-222222222222/expense/2026/09/file-b.bin"
KEY_M="platform/runtime/g6-marker.txt"
"$MC_BIN" cp "$WORK/objects/file-a.bin" "${MC_ALIAS}/${SRC_BUCKET}/${KEY_A}" >/dev/null
"$MC_BIN" cp "$WORK/objects/file-b.bin" "${MC_ALIAS}/${SRC_BUCKET}/${KEY_B}" >/dev/null
"$MC_BIN" cp "$WORK/objects/marker.txt" "${MC_ALIAS}/${SRC_BUCKET}/${KEY_M}" >/dev/null

rm -rf "$WORK/object-backup"
mkdir -p "$WORK/object-backup"
OBJ_DUMP_T0="$(date -u +%s)"
"$MC_BIN" mirror --overwrite "${MC_ALIAS}/${SRC_BUCKET}" "$WORK/object-backup/${SRC_BUCKET}" >/dev/null
OBJ_DUMP_T1="$(date -u +%s)"

OBJ_RESTORE_T0="$(date -u +%s)"
"$MC_BIN" mirror --overwrite "$WORK/object-backup/${SRC_BUCKET}" "${MC_ALIAS}/${DST_BUCKET}/${RESTORE_PREFIX}" >/dev/null
OBJ_RESTORE_T1="$(date -u +%s)"

restore_sha() {
  "$MC_BIN" cat "${MC_ALIAS}/${DST_BUCKET}/${RESTORE_PREFIX}/$1" | sha256sum | awk '{print $1}'
}
DST_SHA_A="$(restore_sha "$KEY_A")"
DST_SHA_B="$(restore_sha "$KEY_B")"
DST_SHA_M="$(restore_sha "$KEY_M")"
SRC_LIST="$("$MC_BIN" ls --recursive "${MC_ALIAS}/${SRC_BUCKET}" | awk '{print $NF}' | sort)"
DST_LIST="$("$MC_BIN" ls --recursive "${MC_ALIAS}/${DST_BUCKET}/${RESTORE_PREFIX}" | awk '{print $NF}' | sort)"
SRC_COUNT="$(printf '%s\n' "$SRC_LIST" | grep -c . || true)"
DST_COUNT="$(printf '%s\n' "$DST_LIST" | grep -c . || true)"

OBJ_PASS=PASS
[ "$DST_SHA_A" = "$SHA_A" ] || { echo "object A sha mismatch"; OBJ_PASS=FAIL; }
[ "$DST_SHA_B" = "$SHA_B" ] || { echo "object B sha mismatch"; OBJ_PASS=FAIL; }
[ "$DST_SHA_M" = "$SHA_M" ] || { echo "marker sha mismatch"; OBJ_PASS=FAIL; }
[ "$SRC_COUNT" = "3" ] && [ "$DST_COUNT" = "3" ] || { echo "object count mismatch src=$SRC_COUNT dst=$DST_COUNT"; OBJ_PASS=FAIL; }
[ "$SRC_LIST" = "$DST_LIST" ] || { echo "object key list mismatch"; OBJ_PASS=FAIL; }

DRILL_END_EPOCH="$(date -u +%s)"
DRILL_END_ISO="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
PG_DUMP_S=$((DUMP_T1 - DUMP_T0))
PG_RESTORE_S=$((RESTORE_T1 - RESTORE_T0))
OBJ_DUMP_S=$((OBJ_DUMP_T1 - OBJ_DUMP_T0))
OBJ_RESTORE_S=$((OBJ_RESTORE_T1 - OBJ_RESTORE_T0))
WALL_S=$((DRILL_END_EPOCH - DRILL_START_EPOCH))

OVERALL=PASS
[ "$PG_PASS" = PASS ] && [ "$OBJ_PASS" = PASS ] || OVERALL=FAIL

RESULT="$WORK/evidence/RESULT.txt"
{
  echo "overall=${OVERALL}"
  echo "postgres_full_restore=${PG_PASS}"
  echo "minio_object_restore=${OBJ_PASS}"
  echo "src_db=${SRC_DB}"
  echo "dst_db=${DST_DB}"
  echo "pg_host=${PGHOST}"
  echo "pg_port=${PGPORT}"
  echo "server_version=${PG_VERSION}"
  echo "pg_dump_version=${PG_DUMP_VER}"
  echo "dump_sha256=${DUMP_SHA}"
  echo "dump_bytes=${DUMP_BYTES}"
  echo "src_audit_sha256=${SRC_AUDIT_SHA}"
  echo "dst_audit_sha256=${DST_AUDIT_SHA}"
  echo "src_meta_sha256=${SRC_META_SHA}"
  echo "dst_meta_sha256=${DST_META_SHA}"
  echo "src_file_sha256=${SRC_FILE_SHA}"
  echo "dst_file_sha256=${DST_FILE_SHA}"
  echo "src_ledger_sha256=${SRC_LEDGER_SHA}"
  echo "dst_ledger_sha256=${DST_LEDGER_SHA}"
  echo "src_flyway_sha256=${SRC_FLYWAY_SHA}"
  echo "dst_flyway_sha256=${DST_FLYWAY_SHA}"
  echo "minio_version=${MINIO_VER}"
  echo "mc_version=${MC_VER}"
  echo "minio_addr=${MINIO_ADDR}"
  echo "src_bucket=${SRC_BUCKET}"
  echo "dst_bucket=${DST_BUCKET}"
  echo "restore_prefix=${RESTORE_PREFIX}"
  echo "object_a_sha256=${SHA_A}"
  echo "object_b_sha256=${SHA_B}"
  echo "object_marker_sha256=${SHA_M}"
  echo "restored_object_a_sha256=${DST_SHA_A}"
  echo "restored_object_b_sha256=${DST_SHA_B}"
  echo "restored_object_marker_sha256=${DST_SHA_M}"
  echo "pg_dump_seconds=${PG_DUMP_S}"
  echo "pg_restore_seconds=${PG_RESTORE_S}"
  echo "object_backup_seconds=${OBJ_DUMP_S}"
  echo "object_restore_seconds=${OBJ_RESTORE_S}"
  echo "wall_seconds=${WALL_S}"
  echo "started_utc=${DRILL_START_ISO}"
  echo "ended_utc=${DRILL_END_ISO}"
  echo "haopan_platform=not_touched"
  echo "single_tenant_logical_restore=not_performed"
  echo "g17_claimed=false"
  echo "minute_rpo_claimed=false"
} >"$RESULT"

EVIDENCE_MD="$WORK/evidence/drill.md"
{
  echo "# G6 recovery drill — machine result"
  echo
  echo "Generated ${DRILL_END_ISO}. Overall **${OVERALL}**."
  echo
  echo "## Versions"
  echo
  echo "| Item | Value |"
  echo "|---|---|"
  echo "| Host | \`${HOST_UNAME}\` |"
  echo "| Java | \`${JAVA_VER}\` |"
  echo "| PostgreSQL server | \`${PG_VERSION}\` |"
  echo "| pg_dump | \`${PG_DUMP_VER}\` |"
  echo "| MinIO | \`${MINIO_VER}\` |"
  echo "| mc | \`${MC_VER}\` |"
  echo "| Dedicated PG | \`${PGHOST}:${PGPORT}\` databases \`${SRC_DB}\` → \`${DST_DB}\` |"
  echo "| Dedicated MinIO | \`${MINIO_ADDR}\` buckets \`${SRC_BUCKET}\` → \`${DST_BUCKET}/${RESTORE_PREFIX}\` |"
  echo
  echo "## PostgreSQL full-database restore"
  echo
  echo "| Check | Source | Restore | Result |"
  echo "|---|---|---|---|"
  echo "| dump SHA-256 | \`${DUMP_SHA}\` (${DUMP_BYTES} bytes) | same file | recorded |"
  echo "| platform.audit_record | \`${SRC_AUDIT_SHA}\` | \`${DST_AUDIT_SHA}\` | $([ "$SRC_AUDIT_SHA" = "$DST_AUDIT_SHA" ] && echo PASS || echo FAIL) |"
  echo "| platform.audit_chain_meta | \`${SRC_META_SHA}\` | \`${DST_META_SHA}\` | $([ "$SRC_META_SHA" = "$DST_META_SHA" ] && echo PASS || echo FAIL) |"
  echo "| files.file_object | \`${SRC_FILE_SHA}\` | \`${DST_FILE_SHA}\` | $([ "$SRC_FILE_SHA" = "$DST_FILE_SHA" ] && echo PASS || echo FAIL) |"
  echo "| ledger.sample_row | \`${SRC_LEDGER_SHA}\` | \`${DST_LEDGER_SHA}\` | $([ "$SRC_LEDGER_SHA" = "$DST_LEDGER_SHA" ] && echo PASS || echo FAIL) |"
  echo "| platform.flyway_schema_history | \`${SRC_FLYWAY_SHA}\` | \`${DST_FLYWAY_SHA}\` | $([ "$SRC_FLYWAY_SHA" = "$DST_FLYWAY_SHA" ] && echo PASS || echo FAIL) |"
  echo
  echo "Counts (source):"
  echo
  echo '```'
  echo "$SRC_COUNTS"
  echo '```'
  echo
  echo "Counts (restore):"
  echo
  echo '```'
  echo "$DST_COUNTS"
  echo '```'
  echo
  echo "Restored tenant_ids (both must be present; this is not single-tenant restore):"
  echo
  echo '```'
  echo "$DST_TENANTS"
  echo '```'
  echo
  echo "PostgreSQL verdict: **${PG_PASS}**. dump ${PG_DUMP_S}s, restore ${PG_RESTORE_S}s."
  echo
  echo "## MinIO object restore"
  echo
  echo "| Object | Source SHA-256 | Restored SHA-256 | Result |"
  echo "|---|---|---|---|"
  echo "| \`${KEY_A}\` | \`${SHA_A}\` | \`${DST_SHA_A}\` | $([ "$DST_SHA_A" = "$SHA_A" ] && echo PASS || echo FAIL) |"
  echo "| \`${KEY_B}\` | \`${SHA_B}\` | \`${DST_SHA_B}\` | $([ "$DST_SHA_B" = "$SHA_B" ] && echo PASS || echo FAIL) |"
  echo "| \`${KEY_M}\` | \`${SHA_M}\` | \`${DST_SHA_M}\` | $([ "$DST_SHA_M" = "$SHA_M" ] && echo PASS || echo FAIL) |"
  echo
  echo "Object restore verdict: **${OBJ_PASS}**. backup ${OBJ_DUMP_S}s, restore ${OBJ_RESTORE_S}s. wall ${WALL_S}s."
} >"$EVIDENCE_MD"

log "RESULT ${OVERALL} postgres=${PG_PASS} minio=${OBJ_PASS} written to $RESULT"
cat "$RESULT"
if [ "$OVERALL" != PASS ]; then
  fail "G6 drill failed; see $RESULT"
fi
