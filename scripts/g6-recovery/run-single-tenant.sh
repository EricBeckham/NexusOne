#!/usr/bin/env bash
# G6 leftover: single-tenant logical restore (ops only).
# Restores ONE tenant's business rows into a dedicated empty database.
#
# Never touches haopan_platform. Never uses port 5432.
# Does not change product RecoveryPort. Does not claim G17, minute-level RPO,
# or a V1.1 product single-tenant restore capability.
# Dual-tenant whole-DB restore remains scripts/g6-recovery/run-drill.sh.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"
PG15_BIN="${G6_PG15_BIN:-/usr/lib/postgresql/15/bin}"
WORK="${G6_WORK_DIR:-$ROOT/.local/g6-recovery}"
PGDATA="${G6_PGDATA:-$WORK/pgdata}"
PGHOST="${G6_PGHOST:-127.0.0.1}"
PGPORT="${G6_PGPORT:-55432}"
SRC_DB="${G6_SRC_DB:-nexusone_g6_src}"
ONE_DB="${G6_ONE_DB:-nexusone_g6_one}"
TENANT_A="11111111-1111-4111-8111-111111111111"
TENANT_B="22222222-2222-4222-8222-222222222222"
TENANT_ID="${G6_TENANT_ID:-$TENANT_A}"
export PATH="$PG15_BIN:$PATH"

mkdir -p "$WORK"/{logs,backup,objects,evidence}
chmod 700 "$WORK" || true

refuse_forbidden_name "$SRC_DB" database
refuse_forbidden_name "$ONE_DB" database
if [ "$SRC_DB" = "$ONE_DB" ]; then
  fail "source and single-tenant restore databases must differ"
fi
refuse_dedicated_port
require_pg15
require_uuid "$TENANT_ID" "G6_TENANT_ID"

export PGHOST PGPORT
export PGUSER="${G6_PGUSER:-$USER}"

start_pg
assert_no_haopan

PG_VERSION="$(psql_q -d postgres -Atc 'SHOW server_version;')"
PG_DUMP_VER="$("$PG15_BIN/pg_dump" --version)"
HOST_UNAME="$(uname -a)"
JAVA_VER="$(java -version 2>&1 | head -n1 || true)"
DRILL_START_MS="$(now_ms)"
DRILL_START_ISO="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

log "server_version=${PG_VERSION}"
psql_q -d postgres -c "DROP DATABASE IF EXISTS ${ONE_DB};"
seed_source_db "$SRC_DB"

SRC_TENANTS="$(psql_q -d "$SRC_DB" -Atc "SELECT tenant_id::text FROM ledger.sample_row ORDER BY tenant_id;")"
echo "$SRC_TENANTS" | grep -qx "$TENANT_A" || fail "source fixture missing tenant A"
echo "$SRC_TENANTS" | grep -qx "$TENANT_B" || fail "source fixture missing tenant B"
echo "$SRC_TENANTS" | grep -qx "$TENANT_ID" || fail "requested tenant ${TENANT_ID} is not in the source fixture"

if [ "$TENANT_ID" = "$TENANT_A" ]; then
  OTHER_TENANT="$TENANT_B"
else
  OTHER_TENANT="$TENANT_A"
fi

SRC_AUDIT_SHA="$(table_sha_tenant "$SRC_DB" platform.audit_record chain_index "$TENANT_ID")"
SRC_FILE_SHA="$(table_sha_tenant "$SRC_DB" files.file_object file_id "$TENANT_ID")"
SRC_LEDGER_SHA="$(table_sha_tenant "$SRC_DB" ledger.sample_row id "$TENANT_ID")"
SRC_FLYWAY_SHA="$(table_sha "$SRC_DB" platform.flyway_schema_history installed_rank)"
SRC_AUDIT_ROWS="$(psql_q -d "$SRC_DB" -Atc "SELECT count(*) FROM platform.audit_record WHERE tenant_id = '${TENANT_ID}'::uuid;")"
SRC_FILE_ROWS="$(psql_q -d "$SRC_DB" -Atc "SELECT count(*) FROM files.file_object WHERE tenant_id = '${TENANT_ID}'::uuid;")"
SRC_LEDGER_ROWS="$(psql_q -d "$SRC_DB" -Atc "SELECT count(*) FROM ledger.sample_row WHERE tenant_id = '${TENANT_ID}'::uuid;")"
SRC_OTHER_LEDGER="$(psql_q -d "$SRC_DB" -Atc "SELECT count(*) FROM ledger.sample_row WHERE tenant_id = '${OTHER_TENANT}'::uuid;")"
[ "$SRC_OTHER_LEDGER" -gt 0 ] || fail "source no longer holds the other tenant; fixture is not dual-tenant"

SCHEMA="$WORK/backup/${SRC_DB}.schema.sql"
log "pg_dump --schema-only ${SRC_DB} -> ${SCHEMA}"
DUMP_T0="$(now_ms)"
"$PG15_BIN/pg_dump" --schema-only --no-owner --no-privileges --file="$SCHEMA" --dbname="$SRC_DB"
DUMP_T1="$(now_ms)"
SCHEMA_SHA="$(sha256sum "$SCHEMA" | awk '{print $1}')"
SCHEMA_BYTES="$(wc -c <"$SCHEMA" | tr -d ' ')"

log "CREATE DATABASE ${ONE_DB} (empty TEMPLATE template0) and apply schema only"
psql_q -d postgres -c "CREATE DATABASE ${ONE_DB} TEMPLATE template0 ENCODING 'UTF8';"
psql_q -d "$ONE_DB" -f "$SCHEMA" >/dev/null

EMPTY_LEDGER="$(psql_q -d "$ONE_DB" -Atc "SELECT count(*) FROM ledger.sample_row;")"
[ "$EMPTY_LEDGER" = "0" ] || fail "restore database was not empty after schema-only apply"

log "COPY one tenant ${TENANT_ID} business rows into ${ONE_DB}"
RESTORE_T0="$(now_ms)"
copy_tenant_rows "$SRC_DB" "$ONE_DB" platform.audit_record "$TENANT_ID"
copy_tenant_rows "$SRC_DB" "$ONE_DB" files.file_object "$TENANT_ID"
copy_tenant_rows "$SRC_DB" "$ONE_DB" ledger.sample_row "$TENANT_ID"
# Schema history is platform metadata, not a tenant business row.
psql_q -d "$SRC_DB" -c "COPY (SELECT * FROM platform.flyway_schema_history ORDER BY installed_rank) TO STDOUT" \
  | psql_q -d "$ONE_DB" -c "COPY platform.flyway_schema_history FROM STDIN"
# Rebuild chain meta from the restored tenant only. Do not copy the dual-tenant head.
psql_q -d "$ONE_DB" -c "
  INSERT INTO platform.audit_chain_meta (chain_id, head_index, head_hash)
  SELECT 'default', chain_index, event_hash
  FROM platform.audit_record
  ORDER BY chain_index DESC
  LIMIT 1;
"
RESTORE_T1="$(now_ms)"

DST_AUDIT_SHA="$(table_sha "$ONE_DB" platform.audit_record chain_index)"
DST_FILE_SHA="$(table_sha "$ONE_DB" files.file_object file_id)"
DST_LEDGER_SHA="$(table_sha "$ONE_DB" ledger.sample_row id)"
DST_FLYWAY_SHA="$(table_sha "$ONE_DB" platform.flyway_schema_history installed_rank)"
DST_TENANTS="$(psql_q -d "$ONE_DB" -Atc "SELECT tenant_id::text FROM ledger.sample_row ORDER BY tenant_id;")"
DST_AUDIT_ROWS="$(psql_q -d "$ONE_DB" -Atc "SELECT count(*) FROM platform.audit_record;")"
DST_FILE_ROWS="$(psql_q -d "$ONE_DB" -Atc "SELECT count(*) FROM files.file_object;")"
DST_LEDGER_ROWS="$(psql_q -d "$ONE_DB" -Atc "SELECT count(*) FROM ledger.sample_row;")"
DST_OTHER="$(psql_q -d "$ONE_DB" -Atc "
  SELECT count(*) FROM (
    SELECT tenant_id FROM platform.audit_record WHERE tenant_id = '${OTHER_TENANT}'::uuid
    UNION ALL
    SELECT tenant_id FROM files.file_object WHERE tenant_id = '${OTHER_TENANT}'::uuid
    UNION ALL
    SELECT tenant_id FROM ledger.sample_row WHERE tenant_id = '${OTHER_TENANT}'::uuid
  ) extra;
")"
SRC_STILL_BOTH="$(psql_q -d "$SRC_DB" -Atc "SELECT count(DISTINCT tenant_id) FROM ledger.sample_row;")"

PG_PASS=PASS
[ "$SRC_AUDIT_SHA" = "$DST_AUDIT_SHA" ] || { echo "mismatch audit_record (tenant filter)"; PG_PASS=FAIL; }
[ "$SRC_FILE_SHA" = "$DST_FILE_SHA" ] || { echo "mismatch file_object (tenant filter)"; PG_PASS=FAIL; }
[ "$SRC_LEDGER_SHA" = "$DST_LEDGER_SHA" ] || { echo "mismatch sample_row (tenant filter)"; PG_PASS=FAIL; }
[ "$SRC_FLYWAY_SHA" = "$DST_FLYWAY_SHA" ] || { echo "mismatch flyway_schema_history"; PG_PASS=FAIL; }
[ "$SRC_AUDIT_ROWS" = "$DST_AUDIT_ROWS" ] || { echo "mismatch audit_record rows"; PG_PASS=FAIL; }
[ "$SRC_FILE_ROWS" = "$DST_FILE_ROWS" ] || { echo "mismatch file_object rows"; PG_PASS=FAIL; }
[ "$SRC_LEDGER_ROWS" = "$DST_LEDGER_ROWS" ] || { echo "mismatch sample_row rows"; PG_PASS=FAIL; }
[ "$DST_AUDIT_ROWS" = "1" ] && [ "$DST_FILE_ROWS" = "1" ] && [ "$DST_LEDGER_ROWS" = "1" ] \
  || { echo "dest must hold exactly one row per tenant-scoped table"; PG_PASS=FAIL; }
echo "$DST_TENANTS" | grep -qx "$TENANT_ID" || { echo "missing requested tenant in dest"; PG_PASS=FAIL; }
echo "$DST_TENANTS" | grep -qx "$OTHER_TENANT" && { echo "other tenant leaked into dest"; PG_PASS=FAIL; }
[ "$DST_OTHER" = "0" ] || { echo "other tenant rows present in dest"; PG_PASS=FAIL; }
[ "$SRC_STILL_BOTH" = "2" ] || { echo "source lost a tenant during single-tenant restore"; PG_PASS=FAIL; }
assert_no_haopan

# Dual-tenant dest name stays unused by this leftover unless the operator overrides it.
if [ "$ONE_DB" != "nexusone_g6_dst" ]; then
  FULL_DST_EXISTS="$(psql_q -d postgres -Atc "SELECT count(*) FROM pg_database WHERE datname = 'nexusone_g6_dst';")"
else
  FULL_DST_EXISTS="overridden"
fi

DRILL_END_MS="$(now_ms)"
DRILL_END_ISO="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
PG_DUMP_MS=$((DUMP_T1 - DUMP_T0))
PG_RESTORE_MS=$((RESTORE_T1 - RESTORE_T0))
WALL_MS=$((DRILL_END_MS - DRILL_START_MS))
OVERALL="$PG_PASS"

RESULT="$WORK/evidence/single-tenant-RESULT.txt"
{
  echo "overall=${OVERALL}"
  echo "single_tenant_logical_restore=${PG_PASS}"
  echo "tenant_id=${TENANT_ID}"
  echo "other_tenant_id=${OTHER_TENANT}"
  echo "src_db=${SRC_DB}"
  echo "one_db=${ONE_DB}"
  echo "pg_host=${PGHOST}"
  echo "pg_port=${PGPORT}"
  echo "server_version=${PG_VERSION}"
  echo "pg_dump_version=${PG_DUMP_VER}"
  echo "schema_sha256=${SCHEMA_SHA}"
  echo "schema_bytes=${SCHEMA_BYTES}"
  echo "src_audit_sha256=${SRC_AUDIT_SHA}"
  echo "dst_audit_sha256=${DST_AUDIT_SHA}"
  echo "src_file_sha256=${SRC_FILE_SHA}"
  echo "dst_file_sha256=${DST_FILE_SHA}"
  echo "src_ledger_sha256=${SRC_LEDGER_SHA}"
  echo "dst_ledger_sha256=${DST_LEDGER_SHA}"
  echo "src_flyway_sha256=${SRC_FLYWAY_SHA}"
  echo "dst_flyway_sha256=${DST_FLYWAY_SHA}"
  echo "src_audit_rows=${SRC_AUDIT_ROWS}"
  echo "dst_audit_rows=${DST_AUDIT_ROWS}"
  echo "src_file_rows=${SRC_FILE_ROWS}"
  echo "dst_file_rows=${DST_FILE_ROWS}"
  echo "src_ledger_rows=${SRC_LEDGER_ROWS}"
  echo "dst_ledger_rows=${DST_LEDGER_ROWS}"
  echo "dst_other_tenant_rows=${DST_OTHER}"
  echo "src_tenant_count=${SRC_STILL_BOTH}"
  echo "pg_schema_dump_ms=${PG_DUMP_MS}"
  echo "pg_tenant_copy_ms=${PG_RESTORE_MS}"
  echo "wall_ms=${WALL_MS}"
  echo "started_utc=${DRILL_START_ISO}"
  echo "ended_utc=${DRILL_END_ISO}"
  echo "haopan_platform=not_touched"
  echo "dual_tenant_full_restore_script=scripts/g6-recovery/run-drill.sh"
  echo "nexusone_g6_dst_exists=${FULL_DST_EXISTS}"
  echo "g17_claimed=false"
  echo "minute_rpo_claimed=false"
  echo "recovery_port_changed=false"
} >"$RESULT"

EVIDENCE_MD="$WORK/evidence/single-tenant.md"
{
  echo "# G6 single-tenant logical restore — machine result"
  echo
  echo "Generated ${DRILL_END_ISO}. Overall **${OVERALL}**."
  echo
  echo "Tenant \`${TENANT_ID}\` only. Other tenant \`${OTHER_TENANT}\` must be absent from \`${ONE_DB}\`."
  echo
  echo "| Check | Source (tenant filter) | Restore (whole dest table) | Result |"
  echo "|---|---|---|---|"
  echo "| platform.audit_record | \`${SRC_AUDIT_SHA}\` (${SRC_AUDIT_ROWS} rows) | \`${DST_AUDIT_SHA}\` (${DST_AUDIT_ROWS} rows) | $([ "$SRC_AUDIT_SHA" = "$DST_AUDIT_SHA" ] && echo PASS || echo FAIL) |"
  echo "| files.file_object | \`${SRC_FILE_SHA}\` (${SRC_FILE_ROWS} rows) | \`${DST_FILE_SHA}\` (${DST_FILE_ROWS} rows) | $([ "$SRC_FILE_SHA" = "$DST_FILE_SHA" ] && echo PASS || echo FAIL) |"
  echo "| ledger.sample_row | \`${SRC_LEDGER_SHA}\` (${SRC_LEDGER_ROWS} rows) | \`${DST_LEDGER_SHA}\` (${DST_LEDGER_ROWS} rows) | $([ "$SRC_LEDGER_SHA" = "$DST_LEDGER_SHA" ] && echo PASS || echo FAIL) |"
  echo "| platform.flyway_schema_history (schema metadata, not tenant-scoped) | \`${SRC_FLYWAY_SHA}\` | \`${DST_FLYWAY_SHA}\` | $([ "$SRC_FLYWAY_SHA" = "$DST_FLYWAY_SHA" ] && echo PASS || echo FAIL) |"
  echo
  echo "Restored tenant_ids:"
  echo
  echo '```'
  echo "$DST_TENANTS"
  echo '```'
  echo
  echo "Source tenant_ids (both must remain):"
  echo
  echo '```'
  echo "$SRC_TENANTS"
  echo '```'
  echo
  echo "schema dump ${PG_DUMP_MS}ms, tenant COPY ${PG_RESTORE_MS}ms, wall ${WALL_MS}ms."
} >"$EVIDENCE_MD"

log "RESULT ${OVERALL} single_tenant=${PG_PASS} tenant=${TENANT_ID} written to $RESULT"
cat "$RESULT"
if [ "$OVERALL" != PASS ]; then
  fail "G6 single-tenant logical restore failed; see $RESULT"
fi
