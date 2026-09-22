#!/usr/bin/env bash
# G4 leftover: 90-day hot-purge / P1 staging audit-events remainder honesty
# beyond #183 / #190.
#
# Extends docs/g4-hot-purge-90d-honesty.md and
# docs/g4-p1-staging-audit-events-honesty.md. After G2+G4 on main
# (including #222 SPA PKCE remainder leftover honesty).
# #183 window policy + #190 staging-like stay always-green PASS.
# Live :5432 / JAR / context / Quartz / MinIO walks are PASS only when
# those are present AND a live walk was observed. Missing live path is
# honest SKIP. Does NOT redo #123/#156/#183/#190/#222. Does NOT DROP
# platform.audit_record. Does NOT switch GET off staging. Does NOT
# change AuditPort same-TX. Does NOT claim product G4 close.
# G18 stays closed. No P0/M0 redo. No Docker.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HERE="$(cd "$(dirname "$0")" && pwd)"
if [[ -x "${HOME}/tools/apache-maven-3.8.7/bin/mvn" ]]; then
  export PATH="${HOME}/tools/apache-maven-3.8.7/bin:${PATH}"
elif [[ -x "${HOME}/tools/apache-maven-3.9.11/bin/mvn" ]]; then
  export PATH="${HOME}/tools/apache-maven-3.9.11/bin:${PATH}"
fi
cd "$ROOT"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT=""
JSON_OUT="${NEXUS_G4_HOT_PURGE_STAGING_JSON:-$ROOT/docs/g4-hot-purge-staging-leftover-honesty/walk.json}"
VERIFY_REPORT=""
VERIFY_EXPECT=pass

ARTIFACT_183="$ROOT/scripts/g4-hot-purge-90d-honesty/fixtures/honest-skip.env"
ARTIFACT_190="$ROOT/docs/engineering/g4-p1-staging-audit-events-honesty/agent-vm.env"
README="$ROOT/docs/README.md"

SIBLING_ROWS="g4-hot-purge-90d,g4-hot-purge-90d-honesty,g4-p1-staging-audit-events,g4-p1-staging-audit-events-honesty,g4-hot-purge-staging-leftover-honesty,g4-pkce-spa-verifier-remainder-honesty,s0-secret-rotate-getenv-remainder-honesty,g6-recovery-port-dry-run-remainder-honesty,g2-report-physical-dump-honesty,g10-leftover-honesty-remainder,g3-outbox-consume-idempotent-honesty,g9-live-multi-jvm-replica-remainder-honesty,g4-export-wizard-deny-honesty,g4-export-wizard-deny,g4-export-wizard,g4-guard-deny-audit,g4-pkce-spa-verifier-honesty,g17-separation-leftover-honesty,g15-wx-dt-native-bridge-remainder,g1-queryast-export-honesty,g17-gm-tls-nginx-handshake-honesty,g5-publishhook-ddl-honesty,g9-sentinel-cluster-remainder-honesty,g2-ops-pg-dump-honesty,g17-mfa-recovery-vue-otp-honesty,g2-h5-every-page-form-write-honesty,g9-redis-down-invalidate-remainder,g10-leftover-honesty,g5-mid-publish-failure-honesty,g3-outbox-consume-crash-honesty,g1-queryast-boundsql-honesty,g15-wx-dt-native-bridge-honesty,s0-secret-rotate-without-restart-honesty,g7-query-expense-travel-honesty,linux-arm-profile-leftover-honesty,g6-recovery-port-dry-run-honesty,s0s1-live-redis-minio-isolation-honesty,g17-separation-honesty,g1-fieldpolicy-mask-second-auth-honesty,g10-identity-drift-honesty,g15-native-bridge-honesty,s0-secret-getenv-snapshot-honesty,g5-browser-failed-publish,g7-withdraw-browser-honesty,g9-redis-down-invalidate-honesty,g9-sentinel-cluster-honesty"

REQUIRED_README_ROWS="g15-h5-mobile g15-f010-h5-runtime g15-miniprogram-webview g15-app-webview-host g15-h5-pkce-browser g15-wx-dt-prod-host g15-native-bridge-stub g15-native-bridge-honesty g15-wx-dt-prod-gap-honesty g15-wx-dt-native-bridge-honesty g15-wx-dt-native-bridge-remainder g7-business-types g7-withdraw-browser-honesty g7-query-expense-travel-honesty g17-separation-of-duties g17-separation-honesty g17-separation-leftover-honesty g17-gm-tls-dual-cert g17-admin-totp-enrollment g17-totp-enrollment-ux g17-admin-mfa-recovery-email g17-mfa-recovery-email-honesty g17-vue-otp-writeback g17-mfa-recovery-vue-otp-honesty g17-gm-tls-nginx-dual-stack g17-gm-tls-nginx-handshake g17-gm-tls-nginx-handshake-honesty g2-form-submission-write g2-submit-start-form-write g2-form-write-honesty g2-h5-permission g2-h5-every-page g2-h5-every-page-form-write-honesty g2-report-surface-guard g2-physical-dump-surface g1-queryast-product-optin g1-datascope-or-in-ast g1-queryast-boundsql-guard g1-queryast-boundsql-honesty g1-queryast-export-honesty g2-surfaceguard-bypass-ci g2-tenant-only-catalog g2-todo-http-surface g2-ops-pg-dump g2-ops-pg-dump-honesty g4-hot-purge-90d g4-hot-purge-90d-honesty g4-hot-purge-staging-leftover-honesty g4-pkce-browser-click g4-guard-deny-audit g4-export-wizard g4-export-wizard-deny g4-export-wizard-deny-honesty g4-p1-staging-audit-events g4-p1-staging-audit-events-honesty g4-pkce-spa-verifier g4-pkce-spa-verifier-honesty g4-pkce-spa-verifier-remainder-honesty g6-recovery-port-dry-run g6-recovery-port-honesty g6-recovery-port-dry-run-honesty g10-identity-drift-dry-run g10-file-reconcile-honesty g10-identity-drift-honesty g10-leftover-honesty g10-leftover-honesty-remainder linux-arm-profile-honesty linux-arm-profile-leftover-honesty g5-mid-publish-failure g5-browser-failed-publish g5-mid-publish-failure-honesty g5-publishhook-ddl-honesty g9-multinode-partition g9-sentinel-cluster-honesty g9-sentinel-cluster-remainder-honesty g9-live-multi-jvm-replica g9-live-multi-jvm-replica-remainder-honesty g9-policysnapshot-invalidate g9-failover-remainder g9-redis-down-invalidate-honesty g9-redis-down-invalidate-remainder g1-export-fieldpolicy-drop g1-fieldpolicy-mask-second-auth g1-fieldpolicy-mask-second-auth-honesty g3-outbox-consume-idempotent g3-claim-crash-window g3-outbox-consume-crash-honesty g3-outbox-consume-idempotent-honesty s0-secret-rotate-without-restart s0-secret-getenv-snapshot-honesty s0-secret-rotate-without-restart-honesty s0-secret-rotate-getenv-remainder-honesty g6-recovery-port-dry-run-remainder-honesty g2-report-physical-dump-honesty s0s1-live-redis-minio-isolation-honesty"

usage() {
  cat <<'EOF'
Usage:
  scripts/g4-hot-purge-staging-leftover-honesty.sh [--out DIR]
  scripts/g4-hot-purge-staging-leftover-honesty.sh --verify-report FILE [pass|fail]

#183 window policy + #190 staging-like stay always-green PASS.
Live :5432 / JAR / context / Quartz / MinIO walks are PASS only when
present AND observed. Missing live path is honest SKIP.
Does not claim product G4 close.

  --out DIR           Output directory (default: .local/g4-hot-purge-staging-leftover-honesty/<utc>)
  --verify-report F   Honesty gate on an existing report.env
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --out) OUT="${2:?}"; shift 2 ;;
    --verify-report)
      VERIFY_REPORT="${2:?}"
      shift 2
      if [ "${1:-}" = "pass" ] || [ "${1:-}" = "fail" ]; then
        VERIFY_EXPECT="$1"
        shift
      fi
      ;;
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

verify_report_inner() {
  local file="$1"
  [ -f "$file" ] || { echo "FAIL: report not found: $file" >&2; return 1; }
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used leftover_183_cells_honest leftover_190_cells_honest window_policy hot_retention_days archive_retention_days hot_table_dropped staging_like_path get_still_on_staging switched_off_staging http_export_wizard same_tx_append_unchanged leftover_rows_kept leftover_rows leftover_rows_all leftover_183_kept leftover_190_kept leftover_123_kept leftover_156_kept leftover_222_kept leftover_223_kept leftover_225_kept leftover_221_kept leftover_228_kept leftover_226_kept leftover_229_kept leftover_216_kept leftover_220_kept leftover_203_kept live_db_present live_jar_endpoint_set live_jar_listener live_jar_present live_context_set live_minio_present live_purge_observed live_purge_window live_purge_window_skip_reason live_quartz_observed live_quartz live_quartz_skip_reason live_minio_cold_observed live_minio_cold live_minio_cold_skip_reason live_staging_5432 live_staging_skip_reason live_query_observed live_export_observed live_jar_context_walk live_jar_context_skip_reason live_jar_context_observed g4_close g18_status audit_port_changed p0_m0_redone redo_123 redo_156 redo_183 redo_190 redo_222 redo_223 redo_225 redo_221 redo_228 redo_226 redo_229"
  local key
  for key in $required; do
    [ -n "$(report_get "$file" "$key")" ] || { echo "FAIL: missing key $key in $file" >&2; return 1; }
  done

  [ "$(report_get "$file" java_feature)" = "21" ] || { echo "FAIL: java_feature must be 21" >&2; return 1; }
  [ "$(report_get "$file" boot_parent_version)" = "4.1.1" ] || { echo "FAIL: boot_parent_version must be 4.1.1" >&2; return 1; }
  [ "$(report_get "$file" boot_parent_match)" = "PASS" ] || { echo "FAIL: boot_parent_match must be PASS" >&2; return 1; }
  [ "$(report_get "$file" docker_used)" = "false" ] || { echo "FAIL: docker_used must be false" >&2; return 1; }
  [ "$(report_get "$file" g18_status)" = "closed_unchanged" ] || { echo "FAIL: g18_status must stay closed_unchanged" >&2; return 1; }
  [ "$(report_get "$file" audit_port_changed)" = "false" ] || { echo "FAIL: audit_port_changed must be false" >&2; return 1; }
  [ "$(report_get "$file" p0_m0_redone)" = "false" ] || { echo "FAIL: p0_m0_redone must stay false" >&2; return 1; }
  [ "$(report_get "$file" redo_123)" = "false" ] || { echo "FAIL: redo_123 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redo_156)" = "false" ] || { echo "FAIL: redo_156 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redo_183)" = "false" ] || { echo "FAIL: redo_183 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redo_190)" = "false" ] || { echo "FAIL: redo_190 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redo_222)" = "false" ] || { echo "FAIL: redo_222 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redo_223)" = "false" ] || { echo "FAIL: redo_223 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redo_225)" = "false" ] || { echo "FAIL: redo_225 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redo_221)" = "false" ] || { echo "FAIL: redo_221 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redo_228)" = "false" ] || { echo "FAIL: redo_228 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redo_226)" = "false" ] || { echo "FAIL: redo_226 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redo_229)" = "false" ] || { echo "FAIL: redo_229 must stay false" >&2; return 1; }
  [ "$(report_get "$file" g4_close)" = "NOT_CLAIMED" ] || { echo "FAIL: g4_close must stay NOT_CLAIMED" >&2; return 1; }
  [ "$(report_get "$file" leftover_183_cells_honest)" = "PASS" ] || { echo "FAIL: leftover_183_cells_honest must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_190_cells_honest)" = "PASS" ] || { echo "FAIL: leftover_190_cells_honest must be PASS" >&2; return 1; }
  [ "$(report_get "$file" window_policy)" = "PASS" ] || { echo "FAIL: window_policy must be PASS" >&2; return 1; }
  [ "$(report_get "$file" hot_retention_days)" = "90" ] || { echo "FAIL: hot_retention_days must be 90" >&2; return 1; }
  [ "$(report_get "$file" archive_retention_days)" = "365" ] || { echo "FAIL: archive_retention_days must be 365" >&2; return 1; }
  [ "$(report_get "$file" hot_table_dropped)" = "false" ] || { echo "FAIL: hot_table_dropped must be false" >&2; return 1; }
  [ "$(report_get "$file" staging_like_path)" = "PASS" ] || { echo "FAIL: staging_like_path must be PASS" >&2; return 1; }
  [ "$(report_get "$file" get_still_on_staging)" = "true" ] || { echo "FAIL: get_still_on_staging must stay true" >&2; return 1; }
  [ "$(report_get "$file" switched_off_staging)" = "false" ] || { echo "FAIL: switched_off_staging must stay false" >&2; return 1; }
  [ "$(report_get "$file" http_export_wizard)" = "false" ] || { echo "FAIL: http_export_wizard must stay false" >&2; return 1; }
  [ "$(report_get "$file" same_tx_append_unchanged)" = "PASS" ] || { echo "FAIL: same_tx_append_unchanged must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_rows_kept)" = "true" ] || { echo "FAIL: leftover_rows_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_rows_all)" = "true" ] || { echo "FAIL: leftover_rows_all must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_183_kept)" = "true" ] || { echo "FAIL: leftover_183_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_190_kept)" = "true" ] || { echo "FAIL: leftover_190_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_123_kept)" = "true" ] || { echo "FAIL: leftover_123_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_156_kept)" = "true" ] || { echo "FAIL: leftover_156_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_222_kept)" = "true" ] || { echo "FAIL: leftover_222_kept must stay true (keep g4-pkce-spa-verifier-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_223_kept)" = "true" ] || { echo "FAIL: leftover_223_kept must stay true (keep s0-secret-rotate-getenv-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_225_kept)" = "true" ] || { echo "FAIL: leftover_225_kept must stay true (keep g6-recovery-port-dry-run-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_221_kept)" = "true" ] || { echo "FAIL: leftover_221_kept must stay true (keep g2-report-physical-dump-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_228_kept)" = "true" ] || { echo "FAIL: leftover_228_kept must stay true (keep g10-leftover-honesty-remainder)" >&2; return 1; }
  [ "$(report_get "$file" leftover_226_kept)" = "true" ] || { echo "FAIL: leftover_226_kept must stay true (keep g3-outbox-consume-idempotent-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_229_kept)" = "true" ] || { echo "FAIL: leftover_229_kept must stay true (keep g9-live-multi-jvm-replica-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_216_kept)" = "true" ] || { echo "FAIL: leftover_216_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_220_kept)" = "true" ] || { echo "FAIL: leftover_220_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_203_kept)" = "true" ] || { echo "FAIL: leftover_203_kept must stay true" >&2; return 1; }

  local rows
  rows="$(report_get "$file" leftover_rows)"
  local required_row
  for required_row in $(echo "$SIBLING_ROWS" | tr ',' ' '); do
    echo "$rows" | grep -Fq "$required_row" || { echo "FAIL: leftover_rows must keep $required_row" >&2; return 1; }
  done

  local verdict
  for verdict in live_purge_window live_quartz live_minio_cold live_staging_5432 live_jar_context_walk; do
    local value
    value="$(report_get "$file" "$verdict")"
    case "$value" in
      PASS|SKIP|FAIL) ;;
      *) echo "FAIL: $verdict must be PASS/SKIP/FAIL" >&2; return 1 ;;
    esac
  done

  if [ "$(report_get "$file" live_purge_window)" = "PASS" ]; then
    [ "$(report_get "$file" live_db_present)" = "true" ] || { echo "FAIL: live_purge_window=PASS requires live_db_present=true" >&2; return 1; }
    [ "$(report_get "$file" live_jar_present)" = "true" ] || { echo "FAIL: live_purge_window=PASS requires live_jar_present=true" >&2; return 1; }
    [ "$(report_get "$file" live_purge_observed)" = "true" ] || { echo "FAIL: live_purge_window=PASS requires live_purge_observed=true" >&2; return 1; }
  fi
  if [ "$(report_get "$file" live_purge_window)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_purge_window_skip_reason)" ] || { echo "FAIL: live purge SKIP needs live_purge_window_skip_reason" >&2; return 1; }
    [ "$(report_get "$file" live_purge_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim live_purge_observed=true" >&2; return 1; }
  fi
  if [ "$(report_get "$file" live_quartz)" = "PASS" ]; then
    [ "$(report_get "$file" live_quartz_observed)" = "true" ] || { echo "FAIL: live_quartz=PASS requires live_quartz_observed=true" >&2; return 1; }
  fi
  if [ "$(report_get "$file" live_quartz)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_quartz_skip_reason)" ] || { echo "FAIL: live Quartz SKIP needs live_quartz_skip_reason" >&2; return 1; }
    [ "$(report_get "$file" live_quartz_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim live_quartz_observed=true" >&2; return 1; }
  fi
  if [ "$(report_get "$file" live_minio_cold)" = "PASS" ]; then
    [ "$(report_get "$file" live_minio_present)" = "true" ] || { echo "FAIL: live_minio_cold=PASS requires live_minio_present=true" >&2; return 1; }
    [ "$(report_get "$file" live_minio_cold_observed)" = "true" ] || { echo "FAIL: live_minio_cold=PASS requires live_minio_cold_observed=true" >&2; return 1; }
  fi
  if [ "$(report_get "$file" live_minio_cold)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_minio_cold_skip_reason)" ] || { echo "FAIL: live MinIO SKIP needs live_minio_cold_skip_reason" >&2; return 1; }
    [ "$(report_get "$file" live_minio_cold_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim live_minio_cold_observed=true" >&2; return 1; }
  fi
  if [ "$(report_get "$file" live_staging_5432)" = "PASS" ]; then
    [ "$(report_get "$file" live_db_present)" = "true" ] || { echo "FAIL: live_staging_5432=PASS requires live_db_present=true" >&2; return 1; }
    [ "$(report_get "$file" live_query_observed)" = "true" ] || { echo "FAIL: live_staging_5432=PASS requires live_query_observed=true" >&2; return 1; }
    [ "$(report_get "$file" live_export_observed)" = "true" ] || { echo "FAIL: live_staging_5432=PASS requires live_export_observed=true" >&2; return 1; }
  fi
  if [ "$(report_get "$file" live_staging_5432)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_staging_skip_reason)" ] || { echo "FAIL: live staging SKIP needs live_staging_skip_reason" >&2; return 1; }
    [ "$(report_get "$file" live_query_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim live_query_observed=true" >&2; return 1; }
    [ "$(report_get "$file" live_export_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim live_export_observed=true" >&2; return 1; }
  fi
  if [ "$(report_get "$file" live_jar_context_walk)" = "PASS" ]; then
    [ "$(report_get "$file" live_jar_present)" = "true" ] || { echo "FAIL: live_jar_context_walk=PASS requires live_jar_present=true" >&2; return 1; }
    [ "$(report_get "$file" live_context_set)" = "true" ] || { echo "FAIL: live_jar_context_walk=PASS requires live_context_set=true" >&2; return 1; }
    [ "$(report_get "$file" live_jar_context_observed)" = "true" ] || { echo "FAIL: live_jar_context_walk=PASS requires live_jar_context_observed=true" >&2; return 1; }
  fi
  if [ "$(report_get "$file" live_jar_context_walk)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_jar_context_skip_reason)" ] || { echo "FAIL: live JAR/context SKIP needs live_jar_context_skip_reason" >&2; return 1; }
    [ "$(report_get "$file" live_jar_context_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim live_jar_context_observed=true" >&2; return 1; }
  fi
  if grep -Eiq 'PASSWORD=|passwd=|secret=|ACCESS_KEY=|TOKEN=' "$file"; then
    echo "FAIL: report must not contain password/secret/token material" >&2
    return 1
  fi
  return 0
}

verify_report() {
  local file="$1"
  local expect="${2:-pass}"
  set +e
  verify_report_inner "$file"
  local rc=$?
  set -e
  if [ "$expect" = "pass" ]; then
    [ "$rc" -eq 0 ] || fail "expected honest leftover, got FAIL ($file)"
    echo "PASS honesty: $file"
  elif [ "$expect" = "fail" ]; then
    [ "$rc" -ne 0 ] || fail "expected fake report to fail: $file"
    echo "PASS reject fake: $file"
  else
    fail "unknown verify expect: $expect"
  fi
}

if [ -n "$VERIFY_REPORT" ]; then
  verify_report "$VERIFY_REPORT" "$VERIFY_EXPECT"
  exit 0
fi

if [ -z "$OUT" ]; then
  OUT="$ROOT/.local/g4-hot-purge-staging-leftover-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G4 leftover: 90-day hot-purge / P1 staging remainder honesty beyond #183/#190"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #123/#156/#183/#190/#222. Not product G4 close. G18 stays closed."
echo

[ -f "$ARTIFACT_183" ] || fail "missing committed #183 artifact $ARTIFACT_183"
[ -f "$ARTIFACT_190" ] || fail "missing committed #190 artifact $ARTIFACT_190"
[ -f "$README" ] || fail "missing $README"

JAVA_FEATURE=missing
JAVA_VERSION_LINE=not-installed
if command -v java >/dev/null 2>&1; then
  JAVA_VERSION_LINE="$(java -version 2>&1 | head -n 1 | tr -d '\r')"
  JAVA_FEATURE="$(java -XshowSettings:properties -version 2>&1 | awk '/^[[:space:]]*java\.specification\.version[[:space:]]*=/{print $3; exit}')"
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

C183_WINDOW="$(report_get "$ARTIFACT_183" window_policy)"
C183_PURGE="$(report_get "$ARTIFACT_183" live_purge_window)"
C183_G4="$(report_get "$ARTIFACT_183" g4_close)"
C183_DROP="$(report_get "$ARTIFACT_183" hot_table_dropped)"
[ "$C183_WINDOW" = "PASS" ] || fail "committed #183 window_policy is $C183_WINDOW; must stay PASS"
[ "$C183_PURGE" = "SKIP" ] || fail "committed #183 live_purge_window is $C183_PURGE; leftover requires honest SKIP"
[ "$C183_G4" = "NOT_CLAIMED" ] || fail "committed #183 g4_close is $C183_G4"
[ "$C183_DROP" = "false" ] || fail "committed #183 hot_table_dropped is $C183_DROP"

C190_STAGING="$(report_get "$ARTIFACT_190" staging_like_path)"
C190_LIVE="$(report_get "$ARTIFACT_190" live_staging_5432)"
C190_G4="$(report_get "$ARTIFACT_190" g4_close)"
C190_SWITCH="$(report_get "$ARTIFACT_190" switched_off_staging)"
[ "$C190_STAGING" = "PASS" ] || fail "committed #190 staging_like_path is $C190_STAGING; must stay PASS"
[ "$C190_LIVE" = "SKIP" ] || fail "committed #190 live_staging_5432 is $C190_LIVE; leftover requires honest SKIP"
[ "$C190_G4" = "NOT_CLAIMED" ] || fail "committed #190 g4_close is $C190_G4"
[ "$C190_SWITCH" = "false" ] || fail "committed #190 switched_off_staging is $C190_SWITCH"

MISSING_ROWS=""
LEFTOVER_ROWS_KEPT=true
LEFTOVER_183_KEPT=false
LEFTOVER_190_KEPT=false
LEFTOVER_123_KEPT=false
LEFTOVER_156_KEPT=false
LEFTOVER_222_KEPT=false
LEFTOVER_223_KEPT=false
LEFTOVER_225_KEPT=false
LEFTOVER_221_KEPT=false
LEFTOVER_228_KEPT=false
LEFTOVER_226_KEPT=false
LEFTOVER_229_KEPT=false
LEFTOVER_216_KEPT=false
LEFTOVER_220_KEPT=false
LEFTOVER_203_KEPT=false
for required_row in $REQUIRED_README_ROWS; do
  if grep -Fq "[${required_row}](./${required_row}.md)" "$README"; then
    case "$required_row" in
      g4-hot-purge-90d-honesty) LEFTOVER_183_KEPT=true ;;
      g4-p1-staging-audit-events-honesty) LEFTOVER_190_KEPT=true ;;
      g4-hot-purge-90d) LEFTOVER_123_KEPT=true ;;
      g4-p1-staging-audit-events) LEFTOVER_156_KEPT=true ;;
      g4-pkce-spa-verifier-remainder-honesty) LEFTOVER_222_KEPT=true ;;
      s0-secret-rotate-getenv-remainder-honesty) LEFTOVER_223_KEPT=true ;;
      g6-recovery-port-dry-run-remainder-honesty) LEFTOVER_225_KEPT=true ;;
      g2-report-physical-dump-honesty) LEFTOVER_221_KEPT=true ;;
      g10-leftover-honesty-remainder) LEFTOVER_228_KEPT=true ;;
      g3-outbox-consume-idempotent-honesty) LEFTOVER_226_KEPT=true ;;
      g9-live-multi-jvm-replica-remainder-honesty) LEFTOVER_229_KEPT=true ;;
      g17-separation-leftover-honesty) LEFTOVER_216_KEPT=true ;;
      g15-wx-dt-native-bridge-remainder) LEFTOVER_220_KEPT=true ;;
      g4-export-wizard-deny-honesty) LEFTOVER_203_KEPT=true ;;
    esac
  else
    LEFTOVER_ROWS_KEPT=false
    MISSING_ROWS="${MISSING_ROWS} ${required_row}"
  fi
done
[ "$LEFTOVER_ROWS_KEPT" = "true" ] || fail "docs/README.md dropped leftover row(s):$MISSING_ROWS"
[ "$LEFTOVER_183_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g4-hot-purge-90d-honesty (#183)"
[ "$LEFTOVER_190_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g4-p1-staging-audit-events-honesty (#190)"
[ "$LEFTOVER_123_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g4-hot-purge-90d (#123)"
[ "$LEFTOVER_156_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g4-p1-staging-audit-events (#156)"
[ "$LEFTOVER_222_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g4-pkce-spa-verifier-remainder-honesty (#222)"
[ "$LEFTOVER_223_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover s0-secret-rotate-getenv-remainder-honesty (#223)"
[ "$LEFTOVER_225_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g6-recovery-port-dry-run-remainder-honesty (#225)"
[ "$LEFTOVER_221_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g2-report-physical-dump-honesty (#221)"
[ "$LEFTOVER_228_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g10-leftover-honesty-remainder (#228)"
[ "$LEFTOVER_226_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g3-outbox-consume-idempotent-honesty (#226)"
[ "$LEFTOVER_229_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g9-live-multi-jvm-replica-remainder-honesty (#229)"
[ "$LEFTOVER_216_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g17-separation-leftover-honesty (#216)"
[ "$LEFTOVER_220_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g15-wx-dt-native-bridge-remainder (#220)"
[ "$LEFTOVER_203_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g4-export-wizard-deny-honesty (#203)"

eval "$(python3 - "$ROOT" <<'PY'
import pathlib, sys
root = pathlib.Path(sys.argv[1])
policy = (root / "nexus-audit-core/src/main/java/one/nexus/audit/AuditRetentionPolicy.java").read_text(encoding="utf-8")
v504 = (root / "nexus-audit-core/src/main/resources/db/migration/V504__audit_hot_purge.sql").read_text(encoding="utf-8")
controller = (root / "nexus-p1-tenant/src/main/java/one/nexus/p1/api/TenantController.java").read_text(encoding="utf-8")
guard = (root / "nexus-permission-core/src/main/java/one/nexus/permission/surface/SurfaceGuard.java").read_text(encoding="utf-8")
window = (
    "HOT_RETENTION = Duration.ofDays(90)" in policy
    and "ARCHIVE_RETENTION = Duration.ofDays(365)" in policy
)
drop = "DROP TABLE" in v504.upper() or "DROP TABLE" in v504
grant = "GRANT DELETE ON TABLE platform.audit_record" in v504
staging = (
    '@GetMapping("/tenants/{id}/audit-events")' in controller
    and "audit.listByTenant" in controller
    and "audit.listByType" in controller
    and "Temporary read of P1-local audit staging rows" in controller
    and "findByTraceId" not in controller
)
same_tx = "appendNewTransaction" in guard
print("WINDOW_OK=%s" % ("true" if window else "false"))
print("HOT_TABLE_DROPPED=%s" % ("true" if drop else "false"))
print("V504_GRANT=%s" % ("true" if grant else "false"))
print("GET_ON_STAGING=%s" % ("true" if staging else "false"))
print("SAME_TX_OK=%s" % ("true" if same_tx else "false"))
PY
)"
[ "$WINDOW_OK" = "true" ] || fail "AuditRetentionPolicy 90d/365d window missing"
[ "$HOT_TABLE_DROPPED" = "false" ] || fail "V504 must not DROP platform.audit_record"
[ "$V504_GRANT" = "true" ] || fail "V504 must keep GRANT DELETE only"
[ "$GET_ON_STAGING" = "true" ] || fail "P1 GET /tenants/{id}/audit-events must still read staging"
[ "$SAME_TX_OK" = "true" ] || fail "AuditPort same-TX appendNewTransaction must stay untouched"

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

LIVE_ENDPOINT="${NEXUS_G4_LIVE_BASE:-${NEXUS_BASE_URL:-}}"
LIVE_ENDPOINT_SET=false
LIVE_CONTEXT_SET=false
if [ -n "$LIVE_ENDPOINT" ]; then
  LIVE_ENDPOINT_SET=true
fi
if [ -n "${NEXUS_G4_LIVE_CONTEXT_ID:-}" ]; then
  LIVE_CONTEXT_SET=true
fi

LIVE_DB=false
LIVE_LISTENER=false
LIVE_MINIO=false
if port_open 127.0.0.1 5432; then LIVE_DB=true; fi
if port_open 127.0.0.1 8080 || port_open 127.0.0.1 8088; then LIVE_LISTENER=true; fi
if port_open 127.0.0.1 9000; then LIVE_MINIO=true; fi

LIVE_JAR_PRESENT=false
if [ "$LIVE_ENDPOINT_SET" = "true" ] && [ "$LIVE_CONTEXT_SET" = "true" ] && [ "$LIVE_LISTENER" = "true" ]; then
  LIVE_JAR_PRESENT=true
fi

if [ "$LIVE_DB" != "true" ] && [ "$LIVE_LISTENER" != "true" ]; then
  PURGE_REASON="no live PostgreSQL on 127.0.0.1:5432 and no product JAR on 127.0.0.1:8080 / :8088; Zonky #123 / #183 collect is not a live purge PASS; Docker forbidden; this leftover does not start the JAR"
  STAGING_REASON="live DB credential unset / nothing listening on 127.0.0.1:5432; #156/#190 collect is not a live staging walk; Docker forbidden"
  JAR_REASON="live JAR unset (NEXUS_BASE_URL / NEXUS_G4_LIVE_BASE); issued context unset (NEXUS_G4_LIVE_CONTEXT_ID); nothing on 127.0.0.1:8080 / :8088; this leftover does not start the JAR"
elif [ "$LIVE_DB" != "true" ]; then
  PURGE_REASON="product JAR may be present but no live PostgreSQL on 127.0.0.1:5432; live 90-day prefix DELETE not observed; Docker forbidden"
  STAGING_REASON="credential/listener missing on 127.0.0.1:5432; live staging query+export not observed"
  JAR_REASON="issued context unset or live walk not observed; this leftover does not fabricate a JAR/context PASS"
elif [ "$LIVE_LISTENER" != "true" ]; then
  PURGE_REASON="PostgreSQL listener present on 127.0.0.1:5432 but no product JAR on 127.0.0.1:8080 / :8088; live purge Job not observed; Docker forbidden"
  STAGING_REASON="listener on 127.0.0.1:5432 but this leftover does not claim a walk until query/export were observed; JAR/context unavailable"
  JAR_REASON="live JAR unset / issued context unset; nothing on 127.0.0.1:8080 / :8088; this leftover does not start the JAR"
else
  PURGE_REASON="live :5432 and JAR listeners present but a live 90-day prefix DELETE was not observed; Zonky #123 is not a live purge PASS; Docker forbidden"
  STAGING_REASON="listener on 127.0.0.1:5432 but this leftover does not claim a walk until query/export were observed"
  JAR_REASON="live listener present but issued context / observed JAR walk missing; this leftover does not fabricate PASS"
fi

QUARTZ_REASON="nexus.audit.hot-purge.schedule-enabled defaults false; live Quartz 03:00 UTC walk not observed; IsolatedQuartzJob is tenant-only"
if [ "$LIVE_MINIO" = "true" ]; then
  MINIO_REASON="MinIO listener present on 127.0.0.1:9000 but a live cold-copy drill was not observed"
else
  MINIO_REASON="no live MinIO on 127.0.0.1:9000; fake / counting storage is not a live cold drill"
fi

if [ "$LIVE_ENDPOINT_SET" != "true" ] && [ "$LIVE_CONTEXT_SET" != "true" ] && [ "$LIVE_LISTENER" != "true" ]; then
  JAR_REASON="live JAR unset (NEXUS_BASE_URL / NEXUS_G4_LIVE_BASE); issued context unset (NEXUS_G4_LIVE_CONTEXT_ID); nothing on 127.0.0.1:8080 / :8088; this leftover does not start the JAR"
fi

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
  kv leftover_183_cells_honest PASS
  kv leftover_190_cells_honest PASS
  kv window_policy PASS
  kv hot_retention_days 90
  kv archive_retention_days 365
  kv hot_table_dropped false
  kv staging_like_path PASS
  kv get_still_on_staging true
  kv switched_off_staging false
  kv http_export_wizard false
  kv same_tx_append_unchanged PASS
  kv leftover_rows_kept "$LEFTOVER_ROWS_KEPT"
  kv leftover_rows "$SIBLING_ROWS"
  kv leftover_rows_all true
  kv leftover_183_kept "$LEFTOVER_183_KEPT"
  kv leftover_190_kept "$LEFTOVER_190_KEPT"
  kv leftover_123_kept "$LEFTOVER_123_KEPT"
  kv leftover_156_kept "$LEFTOVER_156_KEPT"
  kv leftover_222_kept "$LEFTOVER_222_KEPT"
  kv leftover_223_kept "$LEFTOVER_223_KEPT"
  kv leftover_225_kept "$LEFTOVER_225_KEPT"
  kv leftover_221_kept "$LEFTOVER_221_KEPT"
  kv leftover_228_kept "$LEFTOVER_228_KEPT"
  kv leftover_226_kept "$LEFTOVER_226_KEPT"
  kv leftover_229_kept "$LEFTOVER_229_KEPT"
  kv leftover_216_kept "$LEFTOVER_216_KEPT"
  kv leftover_220_kept "$LEFTOVER_220_KEPT"
  kv leftover_203_kept "$LEFTOVER_203_KEPT"
  kv live_db_present "$LIVE_DB"
  kv live_jar_endpoint_set "$LIVE_ENDPOINT_SET"
  kv live_jar_listener "$LIVE_LISTENER"
  kv live_jar_present "$LIVE_JAR_PRESENT"
  kv live_context_set "$LIVE_CONTEXT_SET"
  kv live_minio_present "$LIVE_MINIO"
  kv live_purge_observed false
  kv live_purge_window SKIP
  kv live_purge_window_skip_reason "$PURGE_REASON"
  kv live_quartz_observed false
  kv live_quartz SKIP
  kv live_quartz_skip_reason "$QUARTZ_REASON"
  kv live_minio_cold_observed false
  kv live_minio_cold SKIP
  kv live_minio_cold_skip_reason "$MINIO_REASON"
  kv live_staging_5432 SKIP
  kv live_staging_skip_reason "$STAGING_REASON"
  kv live_query_observed false
  kv live_export_observed false
  kv live_jar_context_walk SKIP
  kv live_jar_context_skip_reason "$JAR_REASON"
  kv live_jar_context_observed false
  kv g4_close NOT_CLAIMED
  kv g4_close_reason "Honesty leftover beyond #183/#190. Window policy / staging-like PASS. Live JAR/context/:5432/Quartz/MinIO SKIP when unavailable. Not product G4 close."
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv p0_m0_redone false
  kv redo_123 false
  kv redo_156 false
  kv redo_183 false
  kv redo_190 false
  kv redo_222 false
  kv redo_223 false
  kv redo_225 false
  kv redo_221 false
  kv redo_228 false
  kv redo_226 false
  kv redo_229 false
} >"$REPORT"

mkdir -p "$(dirname "$JSON_OUT")"
python3 - "$JSON_OUT" "$REPORT" <<'PY'
import json, sys
out, report = sys.argv[1:3]
vals = {}
for line in open(report, encoding="utf-8"):
    if "=" in line and not line.startswith("#"):
        k, v = line.rstrip("\n").split("=", 1)
        vals[k] = v
walk = {
    "leftover": "g4-hot-purge-staging-leftover-honesty",
    "siblings": [
        "g4-hot-purge-90d",
        "g4-hot-purge-90d-honesty",
        "g4-p1-staging-audit-events",
        "g4-p1-staging-audit-events-honesty",
        "g4-pkce-spa-verifier-remainder-honesty",
        "g2-report-physical-dump-honesty",
        "g10-leftover-honesty-remainder",
        "g3-outbox-consume-idempotent-honesty",
        "g9-live-multi-jvm-replica-remainder-honesty",
    ],
    "window_policy": vals.get("window_policy"),
    "staging_like_path": vals.get("staging_like_path"),
    "live_purge_window": vals.get("live_purge_window"),
    "live_staging_5432": vals.get("live_staging_5432"),
    "live_jar_context_walk": vals.get("live_jar_context_walk"),
    "g4Close": "NOT_CLAIMED",
    "g18": "closed_unchanged",
    "timestampUtc": vals.get("timestamp_utc"),
    "evidence": "scripts/g4-hot-purge-staging-leftover-honesty.sh",
}
open(out, "w", encoding="utf-8").write(json.dumps(walk, indent=2) + "\n")
print("wrote", out)
PY

verify_report "$REPORT"

echo "== Honesty fixtures"
verify_report "$HERE/g4-hot-purge-staging-leftover-honesty/fixtures/honest-skip.env"
verify_report "$HERE/g4-hot-purge-staging-leftover-honesty/fixtures/fake-live-purge-pass.env" fail
verify_report "$HERE/g4-hot-purge-staging-leftover-honesty/fixtures/fake-live-staging-pass.env" fail
verify_report "$HERE/g4-hot-purge-staging-leftover-honesty/fixtures/fake-g4-close.env" fail
verify_report "$HERE/g4-hot-purge-staging-leftover-honesty/fixtures/fake-switched-off-staging.env" fail
verify_report "$HERE/g4-hot-purge-staging-leftover-honesty/fixtures/fake-redo.env" fail

SANITIZED="${ROOT}/docs/engineering/g4-hot-purge-staging-leftover-honesty/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

log "DONE report=$REPORT live_purge=SKIP live_staging=SKIP live_jar_context=SKIP g4_close=NOT_CLAIMED"
echo
echo "PASS: leftover honesty recorded (#183/#190 cells honest; live JAR/context/:5432 SKIP)"
echo "See docs/g4-hot-purge-staging-leftover-honesty.md"
echo "Not claimed: product G4 close / live purge / live staging / live JAR context"
echo "Not redone: #123/#156/#183/#190/#222/#223/#225/#221/#228/#226/#229"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
echo "walk=$JSON_OUT"
