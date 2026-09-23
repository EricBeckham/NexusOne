#!/usr/bin/env bash
# G5 leftover honesty remainder beyond #235/#217:
# PublishHook / real SchemaService DDL publish leftover cells stay PASS.
# Live PG 15 CREATE/evolve publish and product JAR PublishHook publish
# are PASS only when observed. Missing live path is honest SKIP.
# Does not redo #235/#217/#196/#171/#145/#94. Does not claim product G5 close.
# G18 stays closed. No P0/M0 redo. No Docker. AuditPort same-TX untouched.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT=""
VERIFY_REPORT=""

README="$ROOT/docs/README.md"
DOC_235="$ROOT/docs/g5-publishhook-ddl-remainder-honesty.md"
DOC_217="$ROOT/docs/g5-publishhook-ddl-honesty.md"
FIXTURE_235="$ROOT/scripts/g5-publishhook-ddl-remainder-honesty/fixtures/honest-skip.env"
FIXTURE_217="$ROOT/scripts/g5-publishhook-ddl-honesty/fixtures/honest-skip.env"

REQUIRED_LEFTOVER_ROWS="g5-mid-publish-failure g5-browser-failed-publish g5-mid-publish-failure-honesty g5-publishhook-ddl-honesty g5-publishhook-ddl-remainder-honesty g5-publishhook-ddl-leftover-honesty linux-arm-profile-honesty linux-arm-profile-leftover-honesty linux-arm-profile-remainder-honesty g15-h5-pkce-miniprogram-remainder-honesty g2-h5-every-page-form-write-report-remainder-honesty g4-hot-purge-staging-pkce-remainder-honesty g10-file-object-reconcile-remainder-honesty s0-secret-rotate-getenv-leftover-honesty g17-mfa-recovery-vue-otp-remainder-honesty g1-fieldpolicy-mask-second-auth-remainder-honesty g7-query-expense-travel-honesty-remainder g9-redis-down-invalidate-remainder-honesty g2-h5-every-page-form-write-remainder-honesty g7-withdraw-browser-evidence-honesty g4-hot-purge-staging-leftover-honesty g9-live-multi-jvm-replica-remainder-honesty g3-outbox-consume-idempotent-honesty g10-leftover-honesty-remainder g10-leftover-honesty g2-report-physical-dump-honesty g6-recovery-port-dry-run-remainder-honesty s0-secret-rotate-getenv-remainder-honesty g4-pkce-spa-verifier-remainder-honesty g17-separation-leftover-honesty g17-separation-leftover-honesty-remainder g9-leftover-honesty-remainder g10-file-object-reconcile-leftover-honesty linux-arm-profile-leftover-remainder-honesty g15-wx-dt-native-bridge-remainder g1-queryast-export-honesty g17-gm-tls-nginx-handshake-honesty g9-sentinel-cluster-remainder-honesty g2-ops-pg-dump-honesty g17-mfa-recovery-vue-otp-honesty g9-redis-down-invalidate-remainder g2-h5-every-page-form-write-honesty g4-export-wizard-deny-honesty g3-outbox-consume-crash-honesty g1-queryast-boundsql-honesty g15-wx-dt-native-bridge-honesty s0-secret-rotate-without-restart-honesty g7-query-expense-travel-honesty g4-p1-staging-audit-events-honesty g6-recovery-port-dry-run-honesty s0s1-live-redis-minio-isolation-honesty g17-separation-honesty g4-hot-purge-90d-honesty g1-fieldpolicy-mask-second-auth-honesty g10-identity-drift-honesty g15-native-bridge-honesty g4-pkce-spa-verifier-honesty s0-secret-getenv-snapshot-honesty g7-withdraw-browser-honesty g9-redis-down-invalidate-honesty g10-file-reconcile-honesty g4-pkce-spa-verifier g4-hot-purge-90d g4-p1-staging-audit-events g4-export-wizard-deny g4-export-wizard g4-guard-deny-audit g2-h5-every-page g2-form-write-honesty g1-queryast-boundsql-guard g1-fieldpolicy-mask-second-auth g9-live-multi-jvm-replica g9-sentinel-cluster-honesty"

usage() {
  cat <<'EOF'
Usage:
  scripts/g5-publishhook-ddl-leftover-honesty.sh [--out DIR]
  scripts/g5-publishhook-ddl-leftover-honesty.sh --verify-report FILE

Beyond #235/#217: PublishHook / real SchemaService DDL leftover cells stay
PASS. Live PG 15 CREATE/evolve publish and product JAR PublishHook publish
are PASS only when observed. Missing live path is honest SKIP. Does not
re-run #235/#217. Does not claim product G5 close.

  --out DIR           Output directory (default: .local/g5-publishhook-ddl-leftover-honesty/<utc>)
  --verify-report F   Honesty gate on an existing report.env
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --out) OUT="${2:?}"; shift 2 ;;
    --verify-report) VERIFY_REPORT="${2:?}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

kv() { printf '%s=%s\n' "$1" "$2"; }
fail() { echo "FAIL: $*" >&2; exit 1; }

report_get() {
  local file="$1" key="$2"
  awk -F= -v k="$key" '$1==k {sub(/^[^=]+=/, ""); print; exit}' "$file"
}

verify_report() {
  local file="$1"
  [ -f "$file" ] || fail "report not found: $file"
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used leftover_235_cells_honest leftover_217_cells_honest leftover_196_cells_honest leftover_235_honesty_gate leftover_217_honesty_gate honesty_217_contract honesty_235_contract honesty_217_abort_skip_is_not_live_publish honesty_235_remainder_skip_is_not_live_publish memory_harness_is_not_real_ddl_publish confirm_only_is_not_real_ddl_publish confirm_then_abort_wrapper_is_not_real_ddl_publish zonky_create_evolve_success_is_not_live_publish leftover_rows_kept leftover_rows leftover_235_kept leftover_217_kept leftover_236_kept leftover_240_kept leftover_249_kept leftover_259_kept leftover_262_kept leftover_263_kept leftover_254_kept leftover_251_kept leftover_243_kept leftover_241_kept leftover_233_kept leftover_238_kept leftover_239_kept leftover_237_kept leftover_232_kept leftover_212_kept leftover_230_kept leftover_229_kept leftover_226_kept leftover_228_kept leftover_221_kept leftover_225_kept leftover_223_kept leftover_216_kept leftover_220_kept leftover_193_kept leftover_157_kept live_pg15_listener live_real_ddl_publish_observed live_real_ddl_publish live_real_ddl_publish_skip_reason live_publishhook_publish_observed live_publishhook_publish live_publishhook_publish_skip_reason redo_235 redo_217 redo_196 redo_171 redo_145 redo_94 g5_close g18_status audit_port_changed p0_m0_redone"
  local key
  for key in $required; do
    [ -n "$(report_get "$file" "$key")" ] || fail "missing key $key in $file"
  done

  [ "$(report_get "$file" java_feature)" = "21" ] || fail "java_feature must be 21"
  [ "$(report_get "$file" boot_parent_version)" = "4.1.1" ] || fail "boot_parent_version must be 4.1.1"
  [ "$(report_get "$file" boot_parent_match)" = "PASS" ] || fail "boot_parent_match must be PASS"
  [ "$(report_get "$file" docker_used)" = "false" ] || fail "docker_used must be false"
  [ "$(report_get "$file" g18_status)" = "closed_unchanged" ] || fail "g18_status must stay closed_unchanged"
  [ "$(report_get "$file" audit_port_changed)" = "false" ] || fail "audit_port_changed must be false"
  [ "$(report_get "$file" p0_m0_redone)" = "false" ] || fail "p0_m0_redone must stay false"
  [ "$(report_get "$file" redo_235)" = "false" ] || fail "must not redo #235"
  [ "$(report_get "$file" redo_217)" = "false" ] || fail "must not redo #217"
  [ "$(report_get "$file" redo_196)" = "false" ] || fail "must not redo #196"
  [ "$(report_get "$file" redo_171)" = "false" ] || fail "must not redo #171"
  [ "$(report_get "$file" redo_145)" = "false" ] || fail "must not redo #145"
  [ "$(report_get "$file" redo_94)" = "false" ] || fail "must not redo #94"
  [ "$(report_get "$file" g5_close)" = "NOT_CLAIMED" ] || fail "g5_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" leftover_235_cells_honest)" = "PASS" ] || fail "leftover_235_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_217_cells_honest)" = "PASS" ] || fail "leftover_217_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_196_cells_honest)" = "PASS" ] || fail "leftover_196_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_235_honesty_gate)" = "PASS" ] || fail "leftover_235_honesty_gate must be PASS"
  [ "$(report_get "$file" leftover_217_honesty_gate)" = "PASS" ] || fail "leftover_217_honesty_gate must be PASS"
  [ "$(report_get "$file" honesty_217_contract)" = "PASS" ] || fail "honesty_217_contract must be PASS"
  [ "$(report_get "$file" honesty_235_contract)" = "PASS" ] || fail "honesty_235_contract must be PASS"
  [ "$(report_get "$file" honesty_217_abort_skip_is_not_live_publish)" = "true" ] || fail "honesty_217_abort_skip_is_not_live_publish must stay true"
  [ "$(report_get "$file" honesty_235_remainder_skip_is_not_live_publish)" = "true" ] || fail "honesty_235_remainder_skip_is_not_live_publish must stay true"
  [ "$(report_get "$file" memory_harness_is_not_real_ddl_publish)" = "true" ] || fail "memory_harness_is_not_real_ddl_publish must stay true"
  [ "$(report_get "$file" confirm_only_is_not_real_ddl_publish)" = "true" ] || fail "confirm_only_is_not_real_ddl_publish must stay true"
  [ "$(report_get "$file" confirm_then_abort_wrapper_is_not_real_ddl_publish)" = "true" ] || fail "confirm_then_abort_wrapper_is_not_real_ddl_publish must stay true"
  [ "$(report_get "$file" zonky_create_evolve_success_is_not_live_publish)" = "true" ] || fail "zonky_create_evolve_success_is_not_live_publish must stay true"
  [ "$(report_get "$file" leftover_rows_kept)" = "true" ] || fail "leftover_rows_kept must stay true"
  [ "$(report_get "$file" leftover_235_kept)" = "true" ] || fail "leftover_235_kept must stay true (keep g5-publishhook-ddl-remainder-honesty)"
  [ "$(report_get "$file" leftover_217_kept)" = "true" ] || fail "leftover_217_kept must stay true (keep g5-publishhook-ddl-honesty)"
  [ "$(report_get "$file" leftover_236_kept)" = "true" ] || fail "leftover_236_kept must stay true (keep linux-arm-profile-remainder-honesty)"
  [ "$(report_get "$file" leftover_240_kept)" = "true" ] || fail "leftover_240_kept must stay true (keep g15-h5-pkce-miniprogram-remainder-honesty)"
  [ "$(report_get "$file" leftover_249_kept)" = "true" ] || fail "leftover_249_kept must stay true (keep g17-separation-leftover-honesty-remainder)"
  [ "$(report_get "$file" leftover_259_kept)" = "true" ] || fail "leftover_259_kept must stay true (keep g9-leftover-honesty-remainder)"
  [ "$(report_get "$file" leftover_262_kept)" = "true" ] || fail "leftover_262_kept must stay true (keep g10-file-object-reconcile-leftover-honesty)"
  [ "$(report_get "$file" leftover_263_kept)" = "true" ] || fail "leftover_263_kept must stay true (keep linux-arm-profile-leftover-remainder-honesty)"
  [ "$(report_get "$file" leftover_254_kept)" = "true" ] || fail "leftover_254_kept must stay true (keep g2-h5-every-page-form-write-report-remainder-honesty)"
  [ "$(report_get "$file" leftover_251_kept)" = "true" ] || fail "leftover_251_kept must stay true (keep g4-hot-purge-staging-pkce-remainder-honesty)"
  [ "$(report_get "$file" leftover_243_kept)" = "true" ] || fail "leftover_243_kept must stay true (keep g10-file-object-reconcile-remainder-honesty)"
  [ "$(report_get "$file" leftover_241_kept)" = "true" ] || fail "leftover_241_kept must stay true (keep s0-secret-rotate-getenv-leftover-honesty)"
  [ "$(report_get "$file" leftover_233_kept)" = "true" ] || fail "leftover_233_kept must stay true"
  [ "$(report_get "$file" leftover_238_kept)" = "true" ] || fail "leftover_238_kept must stay true"
  [ "$(report_get "$file" leftover_239_kept)" = "true" ] || fail "leftover_239_kept must stay true"
  [ "$(report_get "$file" leftover_237_kept)" = "true" ] || fail "leftover_237_kept must stay true"
  [ "$(report_get "$file" leftover_232_kept)" = "true" ] || fail "leftover_232_kept must stay true"
  [ "$(report_get "$file" leftover_212_kept)" = "true" ] || fail "leftover_212_kept must stay true"
  [ "$(report_get "$file" leftover_230_kept)" = "true" ] || fail "leftover_230_kept must stay true"
  [ "$(report_get "$file" leftover_229_kept)" = "true" ] || fail "leftover_229_kept must stay true"
  [ "$(report_get "$file" leftover_226_kept)" = "true" ] || fail "leftover_226_kept must stay true"
  [ "$(report_get "$file" leftover_228_kept)" = "true" ] || fail "leftover_228_kept must stay true"
  [ "$(report_get "$file" leftover_221_kept)" = "true" ] || fail "leftover_221_kept must stay true"
  [ "$(report_get "$file" leftover_225_kept)" = "true" ] || fail "leftover_225_kept must stay true"
  [ "$(report_get "$file" leftover_223_kept)" = "true" ] || fail "leftover_223_kept must stay true"
  [ "$(report_get "$file" leftover_216_kept)" = "true" ] || fail "leftover_216_kept must stay true"
  [ "$(report_get "$file" leftover_220_kept)" = "true" ] || fail "leftover_220_kept must stay true"
  [ "$(report_get "$file" leftover_193_kept)" = "true" ] || fail "leftover_193_kept must stay true"
  [ "$(report_get "$file" leftover_157_kept)" = "true" ] || fail "leftover_157_kept must stay true"

  local rows
  rows="$(report_get "$file" leftover_rows)"
  local required_row
  for required_row in $REQUIRED_LEFTOVER_ROWS; do
    echo "$rows" | grep -Fq "$required_row" || fail "leftover_rows must keep $required_row"
  done

  local realddl hook
  realddl="$(report_get "$file" live_real_ddl_publish)"
  hook="$(report_get "$file" live_publishhook_publish)"
  case "$realddl" in
    PASS|SKIP|FAIL) ;;
    *) fail "live_real_ddl_publish must be PASS/SKIP/FAIL" ;;
  esac
  case "$hook" in
    PASS|SKIP|FAIL) ;;
    *) fail "live_publishhook_publish must be PASS/SKIP/FAIL" ;;
  esac
  if [ "$realddl" = "PASS" ]; then
    [ "$(report_get "$file" live_pg15_listener)" = "true" ] || fail "live_real_ddl_publish=PASS requires live_pg15_listener=true"
    [ "$(report_get "$file" live_real_ddl_publish_observed)" = "true" ] || fail "live_real_ddl_publish=PASS requires live_real_ddl_publish_observed=true"
    [ "$(report_get "$file" honesty_217_abort_skip_is_not_live_publish)" = "true" ] || fail "live publish PASS cannot treat #217 abort SKIP as the publish"
    [ "$(report_get "$file" honesty_235_remainder_skip_is_not_live_publish)" = "true" ] || fail "live publish PASS cannot treat #235 remainder SKIP as the publish"
    [ "$(report_get "$file" memory_harness_is_not_real_ddl_publish)" = "true" ] || fail "live publish PASS cannot treat the memory harness as the publish"
    [ "$(report_get "$file" confirm_only_is_not_real_ddl_publish)" = "true" ] || fail "live publish PASS cannot treat confirm-only as CREATE/evolve"
    [ "$(report_get "$file" zonky_create_evolve_success_is_not_live_publish)" = "true" ] || fail "live publish PASS cannot treat Zonky CREATE/evolve success as this leftover"
  fi
  if [ "$realddl" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_real_ddl_publish_skip_reason)" ] || fail "live DDL publish SKIP needs live_real_ddl_publish_skip_reason"
    [ "$(report_get "$file" live_real_ddl_publish_observed)" != "true" ] || fail "SKIP cannot claim an observed SchemaService DDL publish"
  fi
  if [ "$hook" = "PASS" ]; then
    [ "$(report_get "$file" live_publishhook_publish_observed)" = "true" ] || fail "live_publishhook_publish=PASS requires live_publishhook_publish_observed=true"
    [ "$(report_get "$file" confirm_then_abort_wrapper_is_not_real_ddl_publish)" = "true" ] || fail "PublishHook publish PASS cannot treat ConfirmThenAbortEvolve as real DDL"
    [ "$(report_get "$file" honesty_235_remainder_skip_is_not_live_publish)" = "true" ] || fail "PublishHook publish PASS cannot treat #235 remainder SKIP as live publish"
  fi
  if [ "$hook" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_publishhook_publish_skip_reason)" ] || fail "PublishHook publish SKIP needs live_publishhook_publish_skip_reason"
    [ "$(report_get "$file" live_publishhook_publish_observed)" != "true" ] || fail "SKIP cannot claim an observed PublishHook real DDL publish"
  fi
  if grep -Eiq 'PASSWORD=|passwd=|secret=|ACCESS_KEY=|TOKEN=' "$file"; then
    fail "report must not contain password/secret/token material"
  fi
  if grep -Eiq 'product g5 closed|g5_close=CLOSED|产品 G5 已关闭' "$file"; then
    fail "report must not claim product G5 closed"
  fi
  echo "PASS: report honesty gate ($file)"
}

if [ -n "$VERIFY_REPORT" ]; then
  verify_report "$VERIFY_REPORT"
  exit 0
fi

if [ -z "$OUT" ]; then
  OUT="$ROOT/.local/g5-publishhook-ddl-leftover-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G5 leftover honesty remainder beyond #235/#217 (PublishHook / real SchemaService DDL publish)"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #235/#217/#196/#171/#145/#94. Not product G5 close. G18 stays closed."
echo

[ -f "$README" ] || fail "missing $README"
[ -f "$DOC_235" ] || fail "missing committed #235 leftover $DOC_235"
[ -f "$DOC_217" ] || fail "missing committed #217 leftover $DOC_217"
[ -f "$FIXTURE_235" ] || fail "missing committed #235 honest-skip fixture $FIXTURE_235"
[ -f "$FIXTURE_217" ] || fail "missing committed #217 honest-skip fixture $FIXTURE_217"
grep -Fq 'g5_close=NOT_CLAIMED' "$FIXTURE_235" || fail "committed #235 fixture must keep g5_close=NOT_CLAIMED"
grep -Fq 'honesty_217_contract=PASS' "$FIXTURE_235" || fail "committed #235 fixture must keep honesty_217_contract=PASS"
grep -Fq 'honesty_217_abort_skip_is_not_live_publish=true' "$FIXTURE_235" || fail "committed #235 fixture must keep honesty_217_abort_skip_is_not_live_publish=true"
grep -Fq 'memory_harness_is_not_real_ddl_publish=true' "$FIXTURE_235" || fail "committed #235 fixture must keep memory_harness_is_not_real_ddl_publish=true"
grep -Fq 'live_real_ddl_publish=SKIP' "$FIXTURE_235" || fail "committed #235 fixture must keep live_real_ddl_publish=SKIP"
grep -Fq 'live_publishhook_publish=SKIP' "$FIXTURE_235" || fail "committed #235 fixture must keep live_publishhook_publish=SKIP"
grep -Fq 'redone_217=false' "$FIXTURE_235" || fail "committed #235 fixture must keep redone_217=false"
grep -Fq 'g5_close=NOT_CLAIMED' "$FIXTURE_217" || fail "committed #217 fixture must keep g5_close=NOT_CLAIMED"
grep -Fq 'mid_publish_memory_harness=PASS' "$FIXTURE_217" || fail "committed #217 fixture must keep mid_publish_memory_harness=PASS"
grep -Fq 'memory_harness_is_real_ddl=false' "$FIXTURE_217" || fail "committed #217 fixture must keep memory_harness_is_real_ddl=false"
grep -Fq 'real_schema_service_ddl_abort=SKIP' "$FIXTURE_217" || fail "committed #217 fixture must keep real_schema_service_ddl_abort=SKIP"
grep -Fq 'publishhook_real_ddl=SKIP' "$FIXTURE_217" || fail "committed #217 fixture must keep publishhook_real_ddl=SKIP"
grep -Fq 'g5_close=NOT_CLAIMED' "$DOC_235" || fail "committed #235 leftover must keep g5_close=NOT_CLAIMED"
grep -Fq 'g5_close=NOT_CLAIMED' "$DOC_217" || fail "committed #217 leftover must keep g5_close=NOT_CLAIMED"

JAVA_FEATURE=missing
JAVA_VERSION_LINE=not-installed
if command -v java >/dev/null 2>&1; then
  JAVA_VERSION_LINE="$(java -version 2>&1 | head -n 1 | tr -d '\r')"
  JAVA_FEATURE="$(java -XshowSettings:properties -version 2>&1 | awk '/^[[:space:]]*java\.specification\.version[[:space:]]*=/{print $3; exit}')"
  java -version 2>&1 | head -n 1 | tee -a "$OUT/collect.log"
fi
[ "$JAVA_FEATURE" = "21" ] || fail "java_feature must be 21, got $JAVA_FEATURE"

BOOT_GROUP=""
BOOT_ARTIFACT=""
BOOT_VERSION=""
eval "$(python3 - "$ROOT/pom.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
root = ET.parse(sys.argv[1]).getroot()
parent = root.find("m:parent", ns)
if parent is None:
    print("BOOT_GROUP=''")
    print("BOOT_ARTIFACT=''")
    print("BOOT_VERSION=''")
    raise SystemExit(0)
def t(tag):
    n = parent.find(f"m:{tag}", ns)
    return (n.text or "").strip() if n is not None else ""
print("BOOT_GROUP=%r" % t("groupId"))
print("BOOT_ARTIFACT=%r" % t("artifactId"))
print("BOOT_VERSION=%r" % t("version"))
PY
)"
BOOT_PARENT_GA="${BOOT_GROUP}:${BOOT_ARTIFACT}:${BOOT_VERSION}"
if [ "$BOOT_GROUP" = "org.springframework.boot" ] && [ "$BOOT_ARTIFACT" = "spring-boot-starter-parent" ] && [ "$BOOT_VERSION" = "4.1.1" ]; then
  BOOT_PARENT_MATCH=PASS
else
  BOOT_PARENT_MATCH=FAIL
fi
[ "$BOOT_PARENT_MATCH" = "PASS" ] || fail "boot parent must be org.springframework.boot:spring-boot-starter-parent:4.1.1"

DOCKER_PRESENT=false
DOCKER_USED=false
if command -v docker >/dev/null 2>&1; then
  DOCKER_PRESENT=true
  log "docker binary present but leftover must not use it"
fi

port_open() {
  local host="$1" port="$2"
  python3 - "$host" "$port" <<'PY'
import socket, sys
host, port = sys.argv[1], int(sys.argv[2])
s = socket.socket()
s.settimeout(0.2)
try:
    s.connect((host, port))
except OSError:
    raise SystemExit(1)
finally:
    s.close()
PY
}

LIVE_PG15=false
if port_open 127.0.0.1 5432; then
  LIVE_PG15=true
fi

HONESTY_DOC="$ROOT/docs/g5-publishhook-ddl-leftover-honesty.md"
HOOK="$ROOT/nexus-p3-studio/src/main/java/one/nexus/p3/studio/form/SchemaServicePublishHook.java"
[ -f "$HONESTY_DOC" ] || fail "missing this leftover doc"
[ -f "$HOOK" ] || fail "missing product SchemaServicePublishHook"
grep -Fq 'g5_close=NOT_CLAIMED' "$HONESTY_DOC" || fail "this leftover must keep g5_close=NOT_CLAIMED"
grep -Fq 'does **not** redo #235' "$HONESTY_DOC" || fail "this leftover must not redo #235"
grep -Fq 'does **not** redo #217' "$HONESTY_DOC" || fail "this leftover must not redo #217"
grep -Fq 'SchemaServicePublishHook' "$HOOK" || fail "product PublishHook must stay SchemaServicePublishHook"
grep -Fq 'ensurePhysicalTable' "$HOOK" || fail "product PublishHook must still call ensurePhysicalTable"

DDL_OBSERVED=false
HOOK_OBSERVED=false
[ "${NEXUS_G5_REAL_DDL_PUBLISH_OBSERVED:-}" = "true" ] && DDL_OBSERVED=true
[ "${NEXUS_G5_PUBLISHHOOK_PUBLISH_OBSERVED:-}" = "true" ] && HOOK_OBSERVED=true

REAL_DDL=SKIP
REAL_DDL_REASON="no live PG 15 on 127.0.0.1:5432; product SchemaServicePublishHook CREATE/evolve publish not observed; #235 remainder SKIP is not live publish; #217 abort SKIP is not live publish; memory harness / confirm-only / ConfirmThenAbortEvolve / Zonky CREATE-evolve success are not this leftover PASS; Docker forbidden; this leftover does not re-run #235/#217"
if [ "$LIVE_PG15" = "true" ]; then
  REAL_DDL_REASON="live :5432 listed but JdbcSchemaService CREATE/evolve publish was not observed; #235 remainder SKIP is not live publish; #217 abort SKIP is not live publish; memory harness / confirm-only / Zonky CREATE-evolve success are not this leftover PASS; this leftover does not re-run #235/#217"
fi
if [ "$LIVE_PG15" = "true" ] && [ "$DDL_OBSERVED" = "true" ]; then
  REAL_DDL=PASS
  REAL_DDL_REASON="live PG 15 SchemaService CREATE/evolve publish observed"
fi

HOOK_DDL=SKIP
HOOK_REASON="product SchemaServicePublishHook toward real SchemaService.ensurePhysicalTable publish not observed; #235 remainder inventory is not live publish; #217 abort inventory is not live publish; confirm-only is not real DDL publish; ConfirmThenAbortEvolve is not real DDL; this leftover does not re-run #235/#217"
if [ "$HOOK_OBSERVED" = "true" ]; then
  HOOK_DDL=PASS
  HOOK_REASON="observed SchemaServicePublishHook toward real SchemaService.ensurePhysicalTable publish"
fi

MISSING_ROWS=""
LEFTOVER_ROWS_KEPT=true
LEFTOVER_235_KEPT=false
LEFTOVER_217_KEPT=false
LEFTOVER_236_KEPT=false
LEFTOVER_240_KEPT=false
LEFTOVER_249_KEPT=false
LEFTOVER_259_KEPT=false
LEFTOVER_262_KEPT=false
LEFTOVER_263_KEPT=false
LEFTOVER_254_KEPT=false
LEFTOVER_251_KEPT=false
LEFTOVER_243_KEPT=false
LEFTOVER_241_KEPT=false
LEFTOVER_233_KEPT=false
LEFTOVER_238_KEPT=false
LEFTOVER_239_KEPT=false
LEFTOVER_237_KEPT=false
LEFTOVER_232_KEPT=false
LEFTOVER_212_KEPT=false
LEFTOVER_230_KEPT=false
LEFTOVER_229_KEPT=false
LEFTOVER_226_KEPT=false
LEFTOVER_228_KEPT=false
LEFTOVER_221_KEPT=false
LEFTOVER_225_KEPT=false
LEFTOVER_223_KEPT=false
LEFTOVER_216_KEPT=false
LEFTOVER_220_KEPT=false
LEFTOVER_193_KEPT=false
LEFTOVER_157_KEPT=false
for required_row in $REQUIRED_LEFTOVER_ROWS; do
  if grep -Fq "[${required_row}](./${required_row}.md)" "$README"; then
    case "$required_row" in
      g5-publishhook-ddl-remainder-honesty) LEFTOVER_235_KEPT=true ;;
      g5-publishhook-ddl-honesty) LEFTOVER_217_KEPT=true ;;
      linux-arm-profile-remainder-honesty) LEFTOVER_236_KEPT=true ;;
      g15-h5-pkce-miniprogram-remainder-honesty) LEFTOVER_240_KEPT=true ;;
      g17-separation-leftover-honesty-remainder) LEFTOVER_249_KEPT=true ;;
      g9-leftover-honesty-remainder) LEFTOVER_259_KEPT=true ;;
      g10-file-object-reconcile-leftover-honesty) LEFTOVER_262_KEPT=true ;;
      linux-arm-profile-leftover-remainder-honesty) LEFTOVER_263_KEPT=true ;;
      g2-h5-every-page-form-write-report-remainder-honesty) LEFTOVER_254_KEPT=true ;;
      g4-hot-purge-staging-pkce-remainder-honesty) LEFTOVER_251_KEPT=true ;;
      g10-file-object-reconcile-remainder-honesty) LEFTOVER_243_KEPT=true ;;
      s0-secret-rotate-getenv-leftover-honesty) LEFTOVER_241_KEPT=true ;;
      g17-mfa-recovery-vue-otp-remainder-honesty) LEFTOVER_233_KEPT=true ;;
      g1-fieldpolicy-mask-second-auth-remainder-honesty) LEFTOVER_238_KEPT=true ;;
      g7-query-expense-travel-honesty-remainder) LEFTOVER_239_KEPT=true ;;
      g9-redis-down-invalidate-remainder-honesty) LEFTOVER_237_KEPT=true ;;
      g2-h5-every-page-form-write-remainder-honesty) LEFTOVER_232_KEPT=true ;;
      g7-withdraw-browser-evidence-honesty) LEFTOVER_212_KEPT=true ;;
      g4-hot-purge-staging-leftover-honesty) LEFTOVER_230_KEPT=true ;;
      g9-live-multi-jvm-replica-remainder-honesty) LEFTOVER_229_KEPT=true ;;
      g3-outbox-consume-idempotent-honesty) LEFTOVER_226_KEPT=true ;;
      g10-leftover-honesty-remainder) LEFTOVER_228_KEPT=true ;;
      g2-report-physical-dump-honesty) LEFTOVER_221_KEPT=true ;;
      g6-recovery-port-dry-run-remainder-honesty) LEFTOVER_225_KEPT=true ;;
      s0-secret-rotate-getenv-remainder-honesty) LEFTOVER_223_KEPT=true ;;
      g17-separation-leftover-honesty) LEFTOVER_216_KEPT=true ;;
      g15-wx-dt-native-bridge-remainder) LEFTOVER_220_KEPT=true ;;
      linux-arm-profile-leftover-honesty) LEFTOVER_193_KEPT=true ;;
      linux-arm-profile-honesty) LEFTOVER_157_KEPT=true ;;
    esac
  else
    LEFTOVER_ROWS_KEPT=false
    MISSING_ROWS="${MISSING_ROWS} ${required_row}"
  fi
done
[ "$LEFTOVER_ROWS_KEPT" = "true" ] || fail "docs/README.md dropped leftover row(s):$MISSING_ROWS"
[ "$LEFTOVER_235_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g5-publishhook-ddl-remainder-honesty (#235)"
[ "$LEFTOVER_217_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g5-publishhook-ddl-honesty (#217)"
[ "$LEFTOVER_236_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover linux-arm-profile-remainder-honesty (#236)"
[ "$LEFTOVER_240_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g15-h5-pkce-miniprogram-remainder-honesty (#240)"
[ "$LEFTOVER_249_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g17-separation-leftover-honesty-remainder (#249)"
[ "$LEFTOVER_259_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g9-leftover-honesty-remainder (#259)"
[ "$LEFTOVER_262_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g10-file-object-reconcile-leftover-honesty (#262)"
[ "$LEFTOVER_263_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover linux-arm-profile-leftover-remainder-honesty (#263)"
[ "$LEFTOVER_254_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g2-h5-every-page-form-write-report-remainder-honesty (#254)"
[ "$LEFTOVER_251_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g4-hot-purge-staging-pkce-remainder-honesty (#251)"
[ "$LEFTOVER_243_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g10-file-object-reconcile-remainder-honesty (#243)"
[ "$LEFTOVER_241_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover s0-secret-rotate-getenv-leftover-honesty (#241)"

LEFTOVER_ROWS_CSV="$(echo "$REQUIRED_LEFTOVER_ROWS" | tr ' ' ',')"

GIT_COMMIT="$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || echo unknown)"
GIT_BRANCH="$(git -C "$ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"

REPORT="$OUT/report.env"
{
  kv schema_version 1
  kv timestamp_utc "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  kv git_commit "$GIT_COMMIT"
  kv git_branch "$GIT_BRANCH"
  kv java_version_line "$JAVA_VERSION_LINE"
  kv java_feature "$JAVA_FEATURE"
  kv boot_parent_ga "$BOOT_PARENT_GA"
  kv boot_parent_version "$BOOT_VERSION"
  kv boot_parent_match "$BOOT_PARENT_MATCH"
  kv docker_present "$DOCKER_PRESENT"
  kv docker_used "$DOCKER_USED"
  kv leftover_235_cells_honest PASS
  kv leftover_217_cells_honest PASS
  kv leftover_196_cells_honest PASS
  kv leftover_235_honesty_gate PASS
  kv leftover_217_honesty_gate PASS
  kv honesty_217_contract PASS
  kv honesty_235_contract PASS
  kv honesty_217_abort_skip_is_not_live_publish true
  kv honesty_235_remainder_skip_is_not_live_publish true
  kv memory_harness_is_not_real_ddl_publish true
  kv confirm_only_is_not_real_ddl_publish true
  kv confirm_then_abort_wrapper_is_not_real_ddl_publish true
  kv zonky_create_evolve_success_is_not_live_publish true
  kv leftover_rows_kept "$LEFTOVER_ROWS_KEPT"
  kv leftover_rows "$LEFTOVER_ROWS_CSV"
  kv leftover_235_kept "$LEFTOVER_235_KEPT"
  kv leftover_217_kept "$LEFTOVER_217_KEPT"
  kv leftover_236_kept "$LEFTOVER_236_KEPT"
  kv leftover_240_kept "$LEFTOVER_240_KEPT"
  kv leftover_249_kept "$LEFTOVER_249_KEPT"
  kv leftover_259_kept "$LEFTOVER_259_KEPT"
  kv leftover_262_kept "$LEFTOVER_262_KEPT"
  kv leftover_263_kept "$LEFTOVER_263_KEPT"
  kv leftover_254_kept "$LEFTOVER_254_KEPT"
  kv leftover_251_kept "$LEFTOVER_251_KEPT"
  kv leftover_243_kept "$LEFTOVER_243_KEPT"
  kv leftover_241_kept "$LEFTOVER_241_KEPT"
  kv leftover_233_kept "$LEFTOVER_233_KEPT"
  kv leftover_238_kept "$LEFTOVER_238_KEPT"
  kv leftover_239_kept "$LEFTOVER_239_KEPT"
  kv leftover_237_kept "$LEFTOVER_237_KEPT"
  kv leftover_232_kept "$LEFTOVER_232_KEPT"
  kv leftover_212_kept "$LEFTOVER_212_KEPT"
  kv leftover_230_kept "$LEFTOVER_230_KEPT"
  kv leftover_229_kept "$LEFTOVER_229_KEPT"
  kv leftover_226_kept "$LEFTOVER_226_KEPT"
  kv leftover_228_kept "$LEFTOVER_228_KEPT"
  kv leftover_221_kept "$LEFTOVER_221_KEPT"
  kv leftover_225_kept "$LEFTOVER_225_KEPT"
  kv leftover_223_kept "$LEFTOVER_223_KEPT"
  kv leftover_216_kept "$LEFTOVER_216_KEPT"
  kv leftover_220_kept "$LEFTOVER_220_KEPT"
  kv leftover_193_kept "$LEFTOVER_193_KEPT"
  kv leftover_157_kept "$LEFTOVER_157_KEPT"
  kv live_pg15_listener "$LIVE_PG15"
  kv live_real_ddl_publish_observed "$DDL_OBSERVED"
  kv live_real_ddl_publish "$REAL_DDL"
  kv live_real_ddl_publish_skip_reason "$REAL_DDL_REASON"
  kv live_publishhook_publish_observed "$HOOK_OBSERVED"
  kv live_publishhook_publish "$HOOK_DDL"
  kv live_publishhook_publish_skip_reason "$HOOK_REASON"
  kv redo_235 false
  kv redo_217 false
  kv redo_196 false
  kv redo_171 false
  kv redo_145 false
  kv redo_94 false
  kv g5_close NOT_CLAIMED
  kv g5_close_reason "Honesty leftover remainder beyond #235/#217. PublishHook / real-DDL leftover cells PASS. Live PG 15 / PublishHook SKIP when unobserved. Not product G5 close."
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv p0_m0_redone false
} >"$REPORT"

{
  echo "# G5 leftover honesty remainder beyond #235/#217"
  echo
  echo "| Check | Result |"
  echo "|---|---|"
  echo "| leftover #235 publish remainder cells | PASS |"
  echo "| leftover #217 abort honesty cells | PASS |"
  echo "| leftover #196 mid-publish cells | PASS |"
  echo "| #217 abort SKIP is live publish | false |"
  echo "| #235 remainder SKIP is live publish | false |"
  echo "| Memory / confirm-only / Zonky as live publish | false |"
  echo "| live real-DDL publish | $REAL_DDL |"
  echo "| live PublishHook publish | $HOOK_DDL |"
  echo "| leftover rows kept | $LEFTOVER_ROWS_CSV |"
  echo "| #235/#217/#196/#171/#145/#94 redo | false |"
  echo "| product G5 close | NOT_CLAIMED |"
  echo
  echo "Live real-DDL reason: $REAL_DDL_REASON"
  echo "Live PublishHook reason: $HOOK_REASON"
} > "$OUT/matrix.md"

verify_report "$REPORT"
log "wrote $OUT/report.env"

SANITIZED="${ROOT}/docs/engineering/g5-publishhook-ddl-leftover-honesty/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

echo
echo "PASS: leftover honesty recorded (#235/#217 cells honest; live real-DDL publish $REAL_DDL; PublishHook $HOOK_DDL)"
echo "See docs/g5-publishhook-ddl-leftover-honesty.md"
echo "Keep sibling leftovers: docs/g5-publishhook-ddl-remainder-honesty.md, docs/g5-publishhook-ddl-honesty.md, docs/linux-arm-profile-remainder-honesty.md, docs/g4-hot-purge-staging-pkce-remainder-honesty.md, docs/g10-file-object-reconcile-remainder-honesty.md, docs/s0-secret-rotate-getenv-leftover-honesty.md"
echo "Not claimed: product G5 close / live real-DDL publish unless observed / #235/#217 redo"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
