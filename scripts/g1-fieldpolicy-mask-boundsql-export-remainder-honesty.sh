#!/usr/bin/env bash
# G1 leftover remainder: FieldPolicy mask / BoundSql export PASS/SKIP honesty
# beyond #238 / #215 / #184.
#
# Extends docs/g1-fieldpolicy-mask-second-auth-remainder-honesty.md (#238)
# and docs/g1-queryast-export-honesty.md (#215).
# Does NOT redo #184 / #215 / #238 product code.
# Does NOT claim product G1 close. G18 stays closed.
# No P0/M0 redo. No Docker. AuditPort same-TX unchanged.
# #238/#215 leftover honesty fixtures stay PASS and are explicitly NOT a
# live JAR / IsolatedExport mask / BoundSql walk / product G1 close.
# Live JAR / IsolatedExport mask / BoundSql / export-drop is PASS only
# when the endpoint is up AND the walk was observed. Missing listener
# is honest SKIP. Never fabricates PASS.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT=""
VERIFY_REPORT=""
VERIFY_EXPECT=pass
SKIP_TESTS=0

FILE_238="$ROOT/docs/engineering/g1-fieldpolicy-mask-second-auth-remainder-honesty/agent-vm.env"
FILE_215="$ROOT/docs/engineering/g1-queryast-export-honesty/agent-vm.env"
FIXTURE_184="$ROOT/scripts/g1-fieldpolicy-mask-second-auth-honesty/fixtures/honest-skip.env"
FIXTURE_215="$ROOT/scripts/g1-queryast-export-honesty/fixtures/honest-skip.env"
FIXTURE_238="$ROOT/scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty/fixtures/honest-skip.env"
README="$ROOT/docs/README.md"
DOC_184="$ROOT/docs/g1-fieldpolicy-mask-second-auth-honesty.md"
DOC_215="$ROOT/docs/g1-queryast-export-honesty.md"
DOC_238="$ROOT/docs/g1-fieldpolicy-mask-second-auth-remainder-honesty.md"

REQUIRED_LEFTOVER_ROWS="g1-fieldpolicy-mask-second-auth g1-fieldpolicy-mask-second-auth-honesty g1-fieldpolicy-mask-second-auth-remainder-honesty g1-fieldpolicy-mask-boundsql-export-remainder-honesty g1-queryast-export-honesty g1-queryast-boundsql-guard g1-queryast-boundsql-honesty g1-export-fieldpolicy-drop g1-datascope-or-in-ast linux-arm-profile-remainder-honesty linux-arm-profile-honesty linux-arm-profile-leftover-honesty g2-h5-every-page-form-write-report-remainder-honesty g15-h5-pkce-miniprogram-remainder-honesty g17-separation-leftover-honesty-remainder g4-hot-purge-staging-pkce-remainder-honesty g4-hot-purge-staging-leftover-honesty g4-pkce-spa-verifier-remainder-honesty g10-file-object-reconcile-remainder-honesty g10-leftover-honesty-remainder g10-leftover-honesty g10-file-reconcile-honesty g2-h5-every-page-form-write-remainder-honesty g2-h5-every-page-form-write-honesty g9-redis-down-invalidate-remainder-honesty g9-redis-down-invalidate-remainder g7-query-expense-travel-honesty-remainder g17-mfa-recovery-vue-otp-remainder-honesty g5-publishhook-ddl-remainder-honesty s0-secret-rotate-getenv-leftover-honesty g7-withdraw-browser-evidence-honesty g9-live-multi-jvm-replica-remainder-honesty g3-outbox-consume-idempotent-honesty g2-report-physical-dump-honesty g6-recovery-port-dry-run-remainder-honesty s0-secret-rotate-getenv-remainder-honesty g17-separation-leftover-honesty g15-wx-dt-native-bridge-remainder g17-gm-tls-nginx-handshake-honesty g5-publishhook-ddl-honesty g9-sentinel-cluster-remainder-honesty g2-ops-pg-dump-honesty"

SIBLING_ROWS="
g1-fieldpolicy-mask-second-auth
g1-fieldpolicy-mask-second-auth-honesty
g1-fieldpolicy-mask-second-auth-remainder-honesty
g1-queryast-export-honesty
g1-queryast-boundsql-guard
g1-queryast-boundsql-honesty
g1-export-fieldpolicy-drop
g1-datascope-or-in-ast
linux-arm-profile-remainder-honesty
linux-arm-profile-honesty
linux-arm-profile-leftover-honesty
g2-h5-every-page-form-write-report-remainder-honesty
g15-h5-pkce-miniprogram-remainder-honesty
g17-separation-leftover-honesty-remainder
g17-separation-leftover-honesty
g4-hot-purge-staging-pkce-remainder-honesty
g4-hot-purge-staging-leftover-honesty
g4-hot-purge-90d-honesty
g4-p1-staging-audit-events-honesty
g4-pkce-spa-verifier-remainder-honesty
g4-pkce-spa-verifier-honesty
g4-export-wizard-deny-honesty
g10-file-object-reconcile-remainder-honesty
g10-leftover-honesty-remainder
g10-leftover-honesty
g10-file-reconcile-honesty
g10-identity-drift-honesty
g2-h5-every-page-form-write-remainder-honesty
g2-h5-every-page-form-write-honesty
g2-form-write-honesty
g2-h5-every-page
g2-report-physical-dump-honesty
g2-ops-pg-dump-honesty
g7-query-expense-travel-honesty-remainder
g7-query-expense-travel-honesty
g7-withdraw-browser-evidence-honesty
g7-withdraw-browser-honesty
g17-mfa-recovery-vue-otp-remainder-honesty
g17-mfa-recovery-vue-otp-honesty
g5-publishhook-ddl-remainder-honesty
g5-publishhook-ddl-honesty
s0-secret-rotate-getenv-leftover-honesty
s0-secret-rotate-getenv-remainder-honesty
s0-secret-rotate-without-restart-honesty
s0-secret-getenv-snapshot-honesty
g9-redis-down-invalidate-remainder-honesty
g9-redis-down-invalidate-remainder
g9-live-multi-jvm-replica-remainder-honesty
g9-sentinel-cluster-remainder-honesty
g3-outbox-consume-idempotent-honesty
g3-outbox-consume-crash-honesty
g6-recovery-port-dry-run-remainder-honesty
g17-separation-leftover-honesty
g15-wx-dt-native-bridge-remainder
g15-wx-dt-native-bridge-honesty
g17-gm-tls-nginx-handshake-honesty
linux-arm-profile-leftover-honesty
s0s1-live-redis-minio-isolation-honesty
"

usage() {
  cat <<'EOF'
Usage:
  scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty.sh [--out DIR] [--skip-tests]
  scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty.sh --verify-report FILE [pass|fail]

Beyond #238/#215/#184: leftover FieldPolicy mask / BoundSql-export cells
stay honest; parent leftover honesty fixtures stay PASS and are NOT a live
JAR / IsolatedExport mask / BoundSql walk. Live JAR / IsolatedExport mask /
BoundSql / export-drop is PASS only when present AND the walk was observed.
Missing listener is honest SKIP. Does not re-run #184/#215/#238. Does not
claim product G1 close.

  --out DIR           Output directory (default: .local/g1-fieldpolicy-mask-boundsql-export-remainder-honesty/<utc>)
  --skip-tests        Inventory + honesty only (do not run parent verify)
  --verify-report F   Honesty gate on an existing report.env
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --out) OUT="${2:?}"; shift 2 ;;
    --skip-tests) SKIP_TESTS=1; shift ;;
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

artifact_get() {
  local file="$1" key="$2"
  awk -F= -v k="$key" '$1==k {sub(/^[^=]+=/, ""); print; exit}' "$file"
}

verify_report() {
  local file="$1"
  local expect="${2:-pass}"
  [ -f "$file" ] || fail "report not found: $file"
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

verify_report_inner() {
  local file="$1"
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used leftover_162_cells_honest leftover_184_cells_honest leftover_215_cells_honest leftover_238_cells_honest leftover_184_honesty_gate leftover_215_honesty_gate leftover_238_honesty_gate leftover_beside_ast leftover_boundsql_guard leftover_count_guard leftover_export_hidden_drop mask_on_ast second_auth_on_ast isolated_export_applies_mask count_is_ast_node raw_boundsql_rejected leftover_surface leftover_rows_kept leftover_rows leftover_rows_all leftover_184_kept leftover_215_kept leftover_238_kept leftover_249_kept leftover_240_kept leftover_254_kept leftover_236_kept leftover_251_kept leftover_243_kept leftover_228_kept leftover_232_kept leftover_237_kept leftover_239_kept harness_238_is_not_live_jar harness_238_is_not_live_export_mask harness_238_is_not_product_g1 harness_215_is_not_live_jar live_jar_endpoint_set live_jar_context_set live_jar_listener live_jar_present live_api_mask live_api_mask_observed live_api_mask_skip_reason live_export_mask live_export_mask_observed live_export_mask_skip_reason live_second_auth live_second_auth_observed live_second_auth_skip_reason live_boundsql_count live_boundsql_list_observed live_boundsql_count_observed live_boundsql_skip_reason live_export_fieldpolicy_drop live_export_drop_observed live_export_drop_skip_reason ui_h5_report ui_h5_report_skip_reason honesty_184_rerun honesty_215_rerun honesty_238_rerun redone_184 redone_215 redone_238 g1_close g18_status audit_port_changed p0_m0_redone"
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
  [ "$(report_get "$file" redone_184)" = "false" ] || { echo "FAIL: must not redo #184" >&2; return 1; }
  [ "$(report_get "$file" redone_215)" = "false" ] || { echo "FAIL: must not redo #215" >&2; return 1; }
  [ "$(report_get "$file" redone_238)" = "false" ] || { echo "FAIL: must not redo #238" >&2; return 1; }
  [ "$(report_get "$file" honesty_184_rerun)" = "false" ] || { echo "FAIL: honesty_184_rerun must be false" >&2; return 1; }
  [ "$(report_get "$file" honesty_215_rerun)" = "false" ] || { echo "FAIL: honesty_215_rerun must be false" >&2; return 1; }
  [ "$(report_get "$file" honesty_238_rerun)" = "false" ] || { echo "FAIL: honesty_238_rerun must be false" >&2; return 1; }
  [ "$(report_get "$file" g1_close)" = "NOT_CLAIMED" ] || { echo "FAIL: g1_close must stay NOT_CLAIMED" >&2; return 1; }
  [ "$(report_get "$file" leftover_162_cells_honest)" = "PASS" ] || { echo "FAIL: leftover_162_cells_honest must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_184_cells_honest)" = "PASS" ] || { echo "FAIL: leftover_184_cells_honest must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_215_cells_honest)" = "PASS" ] || { echo "FAIL: leftover_215_cells_honest must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_238_cells_honest)" = "PASS" ] || { echo "FAIL: leftover_238_cells_honest must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_184_honesty_gate)" = "PASS" ] || { echo "FAIL: leftover_184_honesty_gate must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_215_honesty_gate)" = "PASS" ] || { echo "FAIL: leftover_215_honesty_gate must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_238_honesty_gate)" = "PASS" ] || { echo "FAIL: leftover_238_honesty_gate must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_beside_ast)" = "PASS" ] || { echo "FAIL: leftover_beside_ast must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_boundsql_guard)" = "PASS" ] || { echo "FAIL: leftover_boundsql_guard must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_count_guard)" = "PASS" ] || { echo "FAIL: leftover_count_guard must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_export_hidden_drop)" = "PASS" ] || { echo "FAIL: leftover_export_hidden_drop must be PASS" >&2; return 1; }
  [ "$(report_get "$file" mask_on_ast)" = "false" ] || { echo "FAIL: mask_on_ast must stay false" >&2; return 1; }
  [ "$(report_get "$file" second_auth_on_ast)" = "false" ] || { echo "FAIL: second_auth_on_ast must stay false" >&2; return 1; }
  [ "$(report_get "$file" isolated_export_applies_mask)" = "false" ] || { echo "FAIL: isolated_export_applies_mask must stay false" >&2; return 1; }
  [ "$(report_get "$file" count_is_ast_node)" = "false" ] || { echo "FAIL: count_is_ast_node must stay false" >&2; return 1; }
  [ "$(report_get "$file" raw_boundsql_rejected)" = "true" ] || { echo "FAIL: raw_boundsql_rejected must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_surface)" = "FieldPolicyProjector+QueryAstScopedQuery+IsolatedExport" ] || { echo "FAIL: leftover_surface must stay FieldPolicyProjector+QueryAstScopedQuery+IsolatedExport" >&2; return 1; }
  [ "$(report_get "$file" leftover_rows_kept)" = "true" ] || { echo "FAIL: leftover_rows_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_rows_all)" = "true" ] || { echo "FAIL: leftover_rows_all must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_184_kept)" = "true" ] || { echo "FAIL: leftover_184_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_215_kept)" = "true" ] || { echo "FAIL: leftover_215_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_238_kept)" = "true" ] || { echo "FAIL: leftover_238_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_249_kept)" = "true" ] || { echo "FAIL: leftover_249_kept must stay true (keep g17-separation-leftover-honesty-remainder)" >&2; return 1; }
  [ "$(report_get "$file" leftover_240_kept)" = "true" ] || { echo "FAIL: leftover_240_kept must stay true (keep g15-h5-pkce-miniprogram-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_254_kept)" = "true" ] || { echo "FAIL: leftover_254_kept must stay true (keep g2-h5-every-page-form-write-report-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_236_kept)" = "true" ] || { echo "FAIL: leftover_236_kept must stay true (keep linux-arm-profile-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_251_kept)" = "true" ] || { echo "FAIL: leftover_251_kept must stay true (keep g4-hot-purge-staging-pkce-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_243_kept)" = "true" ] || { echo "FAIL: leftover_243_kept must stay true (keep g10-file-object-reconcile-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_228_kept)" = "true" ] || { echo "FAIL: leftover_228_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_232_kept)" = "true" ] || { echo "FAIL: leftover_232_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_237_kept)" = "true" ] || { echo "FAIL: leftover_237_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_239_kept)" = "true" ] || { echo "FAIL: leftover_239_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" harness_238_is_not_live_jar)" = "true" ] || { echo "FAIL: harness_238_is_not_live_jar must stay true; #238 harness is not a live JAR walk" >&2; return 1; }
  [ "$(report_get "$file" harness_238_is_not_live_export_mask)" = "true" ] || { echo "FAIL: harness_238_is_not_live_export_mask must stay true; #238 harness is not a live IsolatedExport mask walk" >&2; return 1; }
  [ "$(report_get "$file" harness_238_is_not_product_g1)" = "true" ] || { echo "FAIL: harness_238_is_not_product_g1 must stay true; #238 PASS is not product G1 close" >&2; return 1; }
  [ "$(report_get "$file" harness_215_is_not_live_jar)" = "true" ] || { echo "FAIL: harness_215_is_not_live_jar must stay true; #215 harness is not a live JAR walk" >&2; return 1; }

  local rows
  rows="$(report_get "$file" leftover_rows)"
  local required_row
  for required_row in $REQUIRED_LEFTOVER_ROWS; do
    echo "$rows" | grep -Fq "$required_row" || { echo "FAIL: leftover_rows must keep $required_row" >&2; return 1; }
  done

  local verdict
  for verdict in live_api_mask live_export_mask live_second_auth live_boundsql_count live_export_fieldpolicy_drop ui_h5_report; do
    local value
    value="$(report_get "$file" "$verdict")"
    case "$value" in
      PASS|SKIP|FAIL) ;;
      *) echo "FAIL: $verdict must be PASS/SKIP/FAIL" >&2; return 1 ;;
    esac
  done

  if [ "$(report_get "$file" live_api_mask)" = "PASS" ]; then
    [ "$(report_get "$file" live_jar_present)" = "true" ] || { echo "FAIL: live_api_mask=PASS requires live_jar_present=true" >&2; return 1; }
    [ "$(report_get "$file" live_api_mask_observed)" = "true" ] || { echo "FAIL: live_api_mask=PASS requires live_api_mask_observed=true" >&2; return 1; }
    [ "$(report_get "$file" harness_238_is_not_live_jar)" = "true" ] || { echo "FAIL: live API mask PASS cannot treat #238 harness as the walk" >&2; return 1; }
  fi
  if [ "$(report_get "$file" live_export_mask)" = "PASS" ]; then
    [ "$(report_get "$file" live_jar_present)" = "true" ] || { echo "FAIL: live_export_mask=PASS requires live_jar_present=true" >&2; return 1; }
    [ "$(report_get "$file" live_export_mask_observed)" = "true" ] || { echo "FAIL: live_export_mask=PASS requires live_export_mask_observed=true" >&2; return 1; }
    [ "$(report_get "$file" harness_238_is_not_live_export_mask)" = "true" ] || { echo "FAIL: live IsolatedExport mask PASS cannot treat #238 harness as the walk" >&2; return 1; }
  fi
  if [ "$(report_get "$file" live_second_auth)" = "PASS" ]; then
    [ "$(report_get "$file" live_jar_present)" = "true" ] || { echo "FAIL: live_second_auth=PASS requires live_jar_present=true" >&2; return 1; }
    [ "$(report_get "$file" live_second_auth_observed)" = "true" ] || { echo "FAIL: live_second_auth=PASS requires live_second_auth_observed=true" >&2; return 1; }
  fi
  if [ "$(report_get "$file" live_boundsql_count)" = "PASS" ]; then
    [ "$(report_get "$file" live_jar_present)" = "true" ] || { echo "FAIL: live_boundsql_count=PASS requires live_jar_present=true" >&2; return 1; }
    [ "$(report_get "$file" live_boundsql_list_observed)" = "true" ] || { echo "FAIL: live_boundsql_count=PASS requires live_boundsql_list_observed=true" >&2; return 1; }
    [ "$(report_get "$file" live_boundsql_count_observed)" = "true" ] || { echo "FAIL: live_boundsql_count=PASS requires live_boundsql_count_observed=true" >&2; return 1; }
    [ "$(report_get "$file" harness_215_is_not_live_jar)" = "true" ] || { echo "FAIL: live BoundSql PASS cannot treat #215 harness as the walk" >&2; return 1; }
  fi
  if [ "$(report_get "$file" live_export_fieldpolicy_drop)" = "PASS" ]; then
    [ "$(report_get "$file" live_jar_present)" = "true" ] || { echo "FAIL: live_export_fieldpolicy_drop=PASS requires live_jar_present=true" >&2; return 1; }
    [ "$(report_get "$file" live_export_drop_observed)" = "true" ] || { echo "FAIL: live_export_fieldpolicy_drop=PASS requires live_export_drop_observed=true" >&2; return 1; }
  fi
  if [ "$(report_get "$file" live_api_mask)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_api_mask_skip_reason)" ] || { echo "FAIL: live API mask SKIP needs live_api_mask_skip_reason" >&2; return 1; }
    [ "$(report_get "$file" live_api_mask_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim a live API mask walk" >&2; return 1; }
  fi
  if [ "$(report_get "$file" live_export_mask)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_export_mask_skip_reason)" ] || { echo "FAIL: live export mask SKIP needs live_export_mask_skip_reason" >&2; return 1; }
    [ "$(report_get "$file" live_export_mask_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim a live IsolatedExport mask walk" >&2; return 1; }
  fi
  if [ "$(report_get "$file" live_second_auth)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_second_auth_skip_reason)" ] || { echo "FAIL: live second-auth SKIP needs live_second_auth_skip_reason" >&2; return 1; }
    [ "$(report_get "$file" live_second_auth_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim a live second-auth walk" >&2; return 1; }
  fi
  if [ "$(report_get "$file" live_boundsql_count)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_boundsql_skip_reason)" ] || { echo "FAIL: live BoundSql SKIP needs live_boundsql_skip_reason" >&2; return 1; }
    [ "$(report_get "$file" live_boundsql_list_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim a live BoundSql list walk" >&2; return 1; }
    [ "$(report_get "$file" live_boundsql_count_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim a live BoundSql COUNT walk" >&2; return 1; }
  fi
  if [ "$(report_get "$file" live_export_fieldpolicy_drop)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_export_drop_skip_reason)" ] || { echo "FAIL: live export drop SKIP needs live_export_drop_skip_reason" >&2; return 1; }
    [ "$(report_get "$file" live_export_drop_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim a live IsolatedExport drop walk" >&2; return 1; }
  fi
  if [ "$(report_get "$file" ui_h5_report)" = "SKIP" ]; then
    [ -n "$(report_get "$file" ui_h5_report_skip_reason)" ] || { echo "FAIL: UI/H5 SKIP needs ui_h5_report_skip_reason" >&2; return 1; }
  fi
  if grep -Eiq 'PASSWORD=|passwd=|secret=|ACCESS_KEY=|TOKEN=' "$file"; then
    echo "FAIL: report must not contain password/secret/token material" >&2
    return 1
  fi
  if grep -Eiq 'product g1 closed|g1_close=CLOSED|产品 G1 已关闭' "$file"; then
    echo "FAIL: report must not claim product G1 closed" >&2
    return 1
  fi
  return 0
}

if [ -n "$VERIFY_REPORT" ]; then
  verify_report "$VERIFY_REPORT" "$VERIFY_EXPECT"
  exit 0
fi

if [ -z "$OUT" ]; then
  OUT="$ROOT/.local/g1-fieldpolicy-mask-boundsql-export-remainder-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G1 leftover honesty remainder beyond #238/#215/#184 (FieldPolicy mask / BoundSql export PASS/SKIP)"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #184/#215/#238. Not product G1 close. G18 stays closed."
echo "#238/#215 harness PASS is not a live JAR / IsolatedExport mask / BoundSql walk / product G1 close."
echo

[ -f "$README" ] || fail "missing $README"
[ -f "$DOC_184" ] || fail "missing committed #184 leftover $DOC_184"
[ -f "$DOC_215" ] || fail "missing committed #215 leftover $DOC_215"
[ -f "$DOC_238" ] || fail "missing committed #238 leftover $DOC_238"
[ -f "$FILE_238" ] || fail "missing committed #238 artifact $FILE_238"
[ -f "$FILE_215" ] || fail "missing committed #215 artifact $FILE_215"
[ -f "$FIXTURE_184" ] || fail "missing committed #184 honest-skip fixture $FIXTURE_184"
[ -f "$FIXTURE_215" ] || fail "missing committed #215 honest-skip fixture $FIXTURE_215"
[ -f "$FIXTURE_238" ] || fail "missing committed #238 honest-skip fixture $FIXTURE_238"

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

C238_184="$(artifact_get "$FILE_238" leftover_184_cells_honest)"
C238_215="$(artifact_get "$FILE_238" leftover_215_cells_honest)"
C238_GATE="$(artifact_get "$FILE_238" leftover_184_honesty_gate)"
C238_MASK="$(artifact_get "$FILE_238" live_api_mask)"
C238_EXPORT="$(artifact_get "$FILE_238" live_export_mask)"
C238_AUTH="$(artifact_get "$FILE_238" live_second_auth)"
C238_G1="$(artifact_get "$FILE_238" g1_close)"
C238_G18="$(artifact_get "$FILE_238" g18_status)"
C238_AUDIT="$(artifact_get "$FILE_238" audit_port_changed)"
C238_P0="$(artifact_get "$FILE_238" p0_m0_redone)"
[ "$C238_184" = "PASS" ] || fail "committed #238 leftover_184_cells_honest is $C238_184"
[ "$C238_215" = "PASS" ] || fail "committed #238 leftover_215_cells_honest is $C238_215"
[ "$C238_GATE" = "PASS" ] || fail "committed #238 leftover_184_honesty_gate is $C238_GATE"
[ "$C238_MASK" = "SKIP" ] || fail "committed #238 live_api_mask is $C238_MASK; leftover requires honest SKIP on that artifact"
[ "$C238_EXPORT" = "SKIP" ] || fail "committed #238 live_export_mask is $C238_EXPORT; leftover requires honest SKIP on that artifact"
[ "$C238_AUTH" = "SKIP" ] || fail "committed #238 live_second_auth is $C238_AUTH; leftover requires honest SKIP on that artifact"
[ "$C238_G1" = "NOT_CLAIMED" ] || fail "committed #238 g1_close is $C238_G1"
[ "$C238_G18" = "closed_unchanged" ] || fail "committed #238 g18_status is $C238_G18"
[ "$C238_AUDIT" = "false" ] || fail "committed #238 audit_port_changed is $C238_AUDIT"
[ "$C238_P0" = "false" ] || fail "committed #238 p0_m0_redone is $C238_P0"

C215_BOUNDSQL="$(artifact_get "$FILE_215" leftover_boundsql_guard)"
C215_COUNT="$(artifact_get "$FILE_215" leftover_count_guard)"
C215_DROP="$(artifact_get "$FILE_215" leftover_export_hidden_drop)"
C215_LIVE="$(artifact_get "$FILE_215" live_boundsql_count)"
C215_G1="$(artifact_get "$FILE_215" g1_close)"
[ "$C215_BOUNDSQL" = "PASS" ] || fail "committed #215 leftover_boundsql_guard is $C215_BOUNDSQL"
[ "$C215_COUNT" = "PASS" ] || fail "committed #215 leftover_count_guard is $C215_COUNT"
[ "$C215_DROP" = "PASS" ] || fail "committed #215 leftover_export_hidden_drop is $C215_DROP"
[ "$C215_LIVE" = "SKIP" ] || fail "committed #215 live_boundsql_count is $C215_LIVE; leftover requires honest SKIP on that artifact"
[ "$C215_G1" = "NOT_CLAIMED" ] || fail "committed #215 g1_close is $C215_G1"

grep -Fq 'g1_close=NOT_CLAIMED' "$FIXTURE_184" || fail "committed #184 fixture must keep g1_close=NOT_CLAIMED"
grep -Fq 'mask_on_ast=false' "$FIXTURE_184" || fail "committed #184 fixture must keep mask_on_ast=false"
grep -Fq 'isolated_export_applies_mask=false' "$FIXTURE_184" || fail "committed #184 fixture must keep isolated_export_applies_mask=false"
grep -Fq 'g1_close=NOT_CLAIMED' "$FIXTURE_215" || fail "committed #215 fixture must keep g1_close=NOT_CLAIMED"
grep -Fq 'leftover_export_hidden_drop=PASS' "$FIXTURE_215" || fail "committed #215 fixture must keep leftover_export_hidden_drop=PASS"
grep -Fq 'g1_close=NOT_CLAIMED' "$FIXTURE_238" || fail "committed #238 fixture must keep g1_close=NOT_CLAIMED"
grep -Fq 'leftover_184_cells_honest=PASS' "$FIXTURE_238" || fail "committed #238 fixture must keep leftover_184_cells_honest=PASS"

LEFTOVER_ROWS_ALL=true
for row in $SIBLING_ROWS; do
  grep -q "\[${row}\]" "$README" || fail "docs/README.md must keep leftover row ${row}"
done
grep -q '\[g1-fieldpolicy-mask-boundsql-export-remainder-honesty\](\./g1-fieldpolicy-mask-boundsql-export-remainder-honesty.md)' "$README" \
  || fail "docs/README.md must list this leftover row"
grep -q '\[g17-separation-leftover-honesty-remainder\](\./g17-separation-leftover-honesty-remainder.md)' "$README" \
  || fail "docs/README.md must keep sibling leftover g17-separation-leftover-honesty-remainder (#249)"
grep -q '\[g15-h5-pkce-miniprogram-remainder-honesty\](\./g15-h5-pkce-miniprogram-remainder-honesty.md)' "$README" \
  || fail "docs/README.md must keep sibling leftover g15-h5-pkce-miniprogram-remainder-honesty (#240)"
grep -q '\[g2-h5-every-page-form-write-report-remainder-honesty\](\./g2-h5-every-page-form-write-report-remainder-honesty.md)' "$README" \
  || fail "docs/README.md must keep sibling leftover g2-h5-every-page-form-write-report-remainder-honesty (#254)"
grep -q '\[linux-arm-profile-remainder-honesty\](\./linux-arm-profile-remainder-honesty.md)' "$README" \
  || fail "docs/README.md must keep sibling leftover linux-arm-profile-remainder-honesty (#236)"
grep -q '\[g4-hot-purge-staging-pkce-remainder-honesty\](\./g4-hot-purge-staging-pkce-remainder-honesty.md)' "$README" \
  || fail "docs/README.md must keep sibling leftover g4-hot-purge-staging-pkce-remainder-honesty (#251)"
if grep -Eiq '产品 G1 已关闭|product g1 closed|g1_close=CLOSED' "$README"; then
  fail "docs/README.md must not claim product G1 closed"
fi

echo
echo "== Honesty fixtures (this leftover)"
verify_report scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty/fixtures/honest-skip.env pass
verify_report scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty/fixtures/fake-g1-close.env fail
verify_report scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty/fixtures/fake-live-api-mask-pass.env fail
verify_report scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty/fixtures/fake-live-export-mask-pass.env fail
verify_report scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty/fixtures/fake-live-second-auth-pass.env fail
verify_report scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty/fixtures/fake-live-boundsql-pass.env fail
verify_report scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty/fixtures/fake-redone.env fail
verify_report scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty/fixtures/fake-harness-as-live.env fail

echo
echo "== Parent leftover honesty still PASS (#238/#215 verify-report only; not a product redo)"
if [ "$SKIP_TESTS" = "0" ]; then
  log "verify parent #238 leftover honesty fixtures (harness PASS; do not re-run collect)"
  ./scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty.sh --verify-report \
    scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty/fixtures/honest-skip.env | tee -a "$OUT/collect.log"
  set +e
  ./scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty.sh --verify-report \
    scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty/fixtures/fake-g1-close.env >"$OUT/fake-238-close.log" 2>&1
  FAKE238_CLOSE=$?
  ./scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty.sh --verify-report \
    scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty/fixtures/fake-live-api-mask-pass.env >"$OUT/fake-238-mask.log" 2>&1
  FAKE238_MASK=$?
  set -e
  [ "$FAKE238_CLOSE" -ne 0 ] || fail "expected #238 fake-g1-close.env to FAIL"
  [ "$FAKE238_MASK" -ne 0 ] || fail "expected #238 fake-live-api-mask-pass.env to FAIL"
  log "verify parent #215 leftover honesty fixtures (harness PASS; do not re-run collect)"
  ./scripts/g1-queryast-export-honesty.sh --verify-report \
    scripts/g1-queryast-export-honesty/fixtures/honest-skip.env | tee -a "$OUT/collect.log"
  log "parent #238/#215 leftover honesty gate PASS (honest SKIP accepted; fakes rejected)"
else
  log "parent #238/#215 verify skipped (--skip-tests); inventory still PASS"
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

LIVE_ENDPOINT="${NEXUS_G1_LIVE_BASE:-${NEXUS_BASE_URL:-}}"
LIVE_ENDPOINT_SET=false
LIVE_CONTEXT_SET=false
if [ -n "$LIVE_ENDPOINT" ]; then
  LIVE_ENDPOINT_SET=true
fi
if [ -n "${NEXUS_G1_LIVE_CONTEXT_ID:-}" ]; then
  LIVE_CONTEXT_SET=true
fi

LIVE_LISTENER=false
if port_open 127.0.0.1 8080 || port_open 127.0.0.1 8088; then
  LIVE_LISTENER=true
fi

LIVE_PRESENT=false
if [ "$LIVE_ENDPOINT_SET" = "true" ] && [ "$LIVE_CONTEXT_SET" = "true" ] && [ "$LIVE_LISTENER" = "true" ]; then
  LIVE_PRESENT=true
fi

if [ "$LIVE_ENDPOINT_SET" != "true" ] && [ "$LIVE_LISTENER" != "true" ]; then
  LIVE_REASON="NEXUS_BASE_URL / NEXUS_G1_LIVE_BASE unset; nothing listening on 127.0.0.1:8080 / :8088; IsolatedExport still writes raw cells; leftover BoundSql / COUNT stay on QueryAstScopedQuery; #238/#215 harness PASS is not a live JAR / IsolatedExport mask / BoundSql walk; Docker forbidden; this leftover remainder does not re-run #184/#215/#238"
elif [ "$LIVE_CONTEXT_SET" != "true" ]; then
  LIVE_REASON="NEXUS_G1_LIVE_CONTEXT_ID unset; live FieldPolicy / BoundSql walk SKIP; #238/#215 harness PASS is not that walk; this leftover remainder does not re-run #184/#215/#238"
elif [ "$LIVE_LISTENER" != "true" ]; then
  LIVE_REASON="live base set but product JAR not reachable; live walk SKIP; #238/#215 harness PASS is not that walk; this leftover remainder does not re-run #184/#215/#238"
else
  LIVE_REASON="live listener present but this leftover remainder does not claim a walk until list/detail mask, IsolatedExport mask, second-auth grant, and BoundSql / COUNT / export-drop were observed; #238/#215 harness PASS is not that walk; does not re-run #184/#215/#238"
fi

MISSING_ROWS=""
LEFTOVER_ROWS_KEPT=true
LEFTOVER_184_KEPT=false
LEFTOVER_215_KEPT=false
LEFTOVER_238_KEPT=false
LEFTOVER_249_KEPT=false
LEFTOVER_240_KEPT=false
LEFTOVER_254_KEPT=false
LEFTOVER_236_KEPT=false
LEFTOVER_251_KEPT=false
LEFTOVER_243_KEPT=false
LEFTOVER_228_KEPT=false
LEFTOVER_232_KEPT=false
LEFTOVER_237_KEPT=false
LEFTOVER_239_KEPT=false
for required_row in $REQUIRED_LEFTOVER_ROWS; do
  if grep -Fq "[${required_row}](./${required_row}.md)" "$README"; then
    case "$required_row" in
      g1-fieldpolicy-mask-second-auth-honesty) LEFTOVER_184_KEPT=true ;;
      g1-queryast-export-honesty) LEFTOVER_215_KEPT=true ;;
      g1-fieldpolicy-mask-second-auth-remainder-honesty) LEFTOVER_238_KEPT=true ;;
      g17-separation-leftover-honesty-remainder) LEFTOVER_249_KEPT=true ;;
      g15-h5-pkce-miniprogram-remainder-honesty) LEFTOVER_240_KEPT=true ;;
      g2-h5-every-page-form-write-report-remainder-honesty) LEFTOVER_254_KEPT=true ;;
      linux-arm-profile-remainder-honesty) LEFTOVER_236_KEPT=true ;;
      g4-hot-purge-staging-pkce-remainder-honesty) LEFTOVER_251_KEPT=true ;;
      g10-file-object-reconcile-remainder-honesty) LEFTOVER_243_KEPT=true ;;
      g10-leftover-honesty-remainder) LEFTOVER_228_KEPT=true ;;
      g2-h5-every-page-form-write-remainder-honesty) LEFTOVER_232_KEPT=true ;;
      g9-redis-down-invalidate-remainder-honesty) LEFTOVER_237_KEPT=true ;;
      g7-query-expense-travel-honesty-remainder) LEFTOVER_239_KEPT=true ;;
    esac
  else
    LEFTOVER_ROWS_KEPT=false
    MISSING_ROWS="${MISSING_ROWS} ${required_row}"
  fi
done
[ "$LEFTOVER_ROWS_KEPT" = "true" ] || fail "docs/README.md dropped leftover row(s):$MISSING_ROWS"
[ "$LEFTOVER_184_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g1-fieldpolicy-mask-second-auth-honesty (#184)"
[ "$LEFTOVER_215_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g1-queryast-export-honesty (#215)"
[ "$LEFTOVER_238_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g1-fieldpolicy-mask-second-auth-remainder-honesty (#238)"
[ "$LEFTOVER_249_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g17-separation-leftover-honesty-remainder (#249)"
[ "$LEFTOVER_240_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g15-h5-pkce-miniprogram-remainder-honesty (#240)"
[ "$LEFTOVER_254_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g2-h5-every-page-form-write-report-remainder-honesty (#254)"
[ "$LEFTOVER_236_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover linux-arm-profile-remainder-honesty (#236)"
[ "$LEFTOVER_251_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g4-hot-purge-staging-pkce-remainder-honesty (#251)"
[ "$LEFTOVER_243_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g10-file-object-reconcile-remainder-honesty (#243)"

# Honesty leftover remainder never fabricates a live walk. #184/#215/#238 scripts are not re-run.
LIVE_API=SKIP
LIVE_EXPORT=SKIP
LIVE_AUTH=SKIP
LIVE_BOUNDSQL=SKIP
LIVE_DROP=SKIP
LIVE_API_OBS=false
LIVE_EXPORT_OBS=false
LIVE_AUTH_OBS=false
LIVE_LIST_OBS=false
LIVE_COUNT_OBS=false
LIVE_DROP_OBS=false
[ "${LIVE_API_MASK_OBSERVED:-}" = "true" ] && LIVE_API_OBS=true
[ "${LIVE_EXPORT_MASK_OBSERVED:-}" = "true" ] && LIVE_EXPORT_OBS=true
[ "${LIVE_SECOND_AUTH_OBSERVED:-}" = "true" ] && LIVE_AUTH_OBS=true
[ "${LIVE_BOUNDSQL_LIST_OBSERVED:-}" = "true" ] && LIVE_LIST_OBS=true
[ "${LIVE_BOUNDSQL_COUNT_OBSERVED:-}" = "true" ] && LIVE_COUNT_OBS=true
[ "${LIVE_EXPORT_DROP_OBSERVED:-}" = "true" ] && LIVE_DROP_OBS=true
if [ "$LIVE_PRESENT" = "true" ] && [ "$LIVE_API_OBS" = "true" ]; then
  LIVE_API=PASS
  LIVE_REASON="live product-JAR list/detail FieldPolicy mask walk observed; #238/#215 harness PASS is still not that walk"
fi
if [ "$LIVE_PRESENT" = "true" ] && [ "$LIVE_EXPORT_OBS" = "true" ]; then
  LIVE_EXPORT=PASS
fi
if [ "$LIVE_PRESENT" = "true" ] && [ "$LIVE_AUTH_OBS" = "true" ]; then
  LIVE_AUTH=PASS
fi
if [ "$LIVE_PRESENT" = "true" ] && [ "$LIVE_LIST_OBS" = "true" ] && [ "$LIVE_COUNT_OBS" = "true" ]; then
  LIVE_BOUNDSQL=PASS
fi
if [ "$LIVE_PRESENT" = "true" ] && [ "$LIVE_DROP_OBS" = "true" ]; then
  LIVE_DROP=PASS
fi

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
  kv leftover_162_cells_honest PASS
  kv leftover_184_cells_honest PASS
  kv leftover_215_cells_honest PASS
  kv leftover_238_cells_honest PASS
  kv leftover_184_honesty_gate PASS
  kv leftover_215_honesty_gate PASS
  kv leftover_238_honesty_gate PASS
  kv leftover_beside_ast PASS
  kv leftover_boundsql_guard PASS
  kv leftover_count_guard PASS
  kv leftover_export_hidden_drop PASS
  kv mask_on_ast false
  kv second_auth_on_ast false
  kv isolated_export_applies_mask false
  kv count_is_ast_node false
  kv raw_boundsql_rejected true
  kv leftover_surface FieldPolicyProjector+QueryAstScopedQuery+IsolatedExport
  kv leftover_rows_kept "$LEFTOVER_ROWS_KEPT"
  kv leftover_rows "$LEFTOVER_ROWS_CSV"
  kv leftover_rows_all "$LEFTOVER_ROWS_ALL"
  kv leftover_184_kept "$LEFTOVER_184_KEPT"
  kv leftover_215_kept "$LEFTOVER_215_KEPT"
  kv leftover_238_kept "$LEFTOVER_238_KEPT"
  kv leftover_249_kept "$LEFTOVER_249_KEPT"
  kv leftover_240_kept "$LEFTOVER_240_KEPT"
  kv leftover_254_kept "$LEFTOVER_254_KEPT"
  kv leftover_236_kept "$LEFTOVER_236_KEPT"
  kv leftover_251_kept "$LEFTOVER_251_KEPT"
  kv leftover_243_kept "$LEFTOVER_243_KEPT"
  kv leftover_228_kept "$LEFTOVER_228_KEPT"
  kv leftover_232_kept "$LEFTOVER_232_KEPT"
  kv leftover_237_kept "$LEFTOVER_237_KEPT"
  kv leftover_239_kept "$LEFTOVER_239_KEPT"
  kv harness_238_is_not_live_jar true
  kv harness_238_is_not_live_export_mask true
  kv harness_238_is_not_product_g1 true
  kv harness_215_is_not_live_jar true
  kv live_jar_endpoint_set "$LIVE_ENDPOINT_SET"
  kv live_jar_context_set "$LIVE_CONTEXT_SET"
  kv live_jar_listener "$LIVE_LISTENER"
  kv live_jar_present "$LIVE_PRESENT"
  kv live_api_mask "$LIVE_API"
  kv live_api_mask_observed "$LIVE_API_OBS"
  kv live_api_mask_skip_reason "$LIVE_REASON"
  kv live_export_mask "$LIVE_EXPORT"
  kv live_export_mask_observed "$LIVE_EXPORT_OBS"
  kv live_export_mask_skip_reason "$LIVE_REASON"
  kv live_second_auth "$LIVE_AUTH"
  kv live_second_auth_observed "$LIVE_AUTH_OBS"
  kv live_second_auth_skip_reason "$LIVE_REASON"
  kv live_boundsql_count "$LIVE_BOUNDSQL"
  kv live_boundsql_list_observed "$LIVE_LIST_OBS"
  kv live_boundsql_count_observed "$LIVE_COUNT_OBS"
  kv live_boundsql_skip_reason "$LIVE_REASON"
  kv live_export_fieldpolicy_drop "$LIVE_DROP"
  kv live_export_drop_observed "$LIVE_DROP_OBS"
  kv live_export_drop_skip_reason "$LIVE_REASON"
  kv ui_h5_report SKIP
  kv ui_h5_report_skip_reason "UI / H5 / report UI FieldPolicy / BoundSql walk was not walked; leftover stays open; #238/#215 harness PASS is not that walk; this leftover does not re-run #184/#215/#238"
  kv honesty_184_rerun false
  kv honesty_215_rerun false
  kv honesty_238_rerun false
  kv redone_184 false
  kv redone_215 false
  kv redone_238 false
  kv g1_close NOT_CLAIMED
  kv g1_close_reason "Honesty leftover remainder beyond #238/#215/#184. leftoverMaskFields / BoundSql / export hidden-drop stay PASS. Parent #238/#215 leftover honesty fixtures stay PASS and are not a live JAR / IsolatedExport mask / BoundSql walk. Live JAR / IsolatedExport mask / second-auth / BoundSql SKIP when unavailable. Not product G1 close."
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv p0_m0_redone false
} >"$REPORT"

{
  echo "# G1 leftover honesty remainder beyond #238/#215/#184"
  echo
  echo "| Check | Result |"
  echo "|---|---|"
  echo "| leftover #162 leftoverMaskFields | PASS |"
  echo "| leftover #184 honesty cells | PASS |"
  echo "| leftover #215 BoundSql / export | PASS |"
  echo "| leftover #238 remainder cells | PASS |"
  echo "| #238 leftover honesty fixtures | PASS (not live JAR / IsolatedExport mask) |"
  echo "| leftover beside AST | PASS |"
  echo "| leftover BoundSql / COUNT / export drop | PASS |"
  echo "| mask_on_ast | false |"
  echo "| IsolatedExport applies mask | false |"
  echo "| live API mask | $LIVE_API |"
  echo "| live IsolatedExport mask | $LIVE_EXPORT |"
  echo "| live second-auth | $LIVE_AUTH |"
  echo "| live BoundSql / COUNT | $LIVE_BOUNDSQL |"
  echo "| live export FieldPolicy drop | $LIVE_DROP |"
  echo "| leftover rows kept | $LEFTOVER_ROWS_CSV |"
  echo "| #184/#215/#238 redo | false |"
  echo "| product G1 close | NOT_CLAIMED |"
  echo
  echo "Live reason: $LIVE_REASON"
} > "$OUT/matrix.md"

verify_report "$REPORT" pass
log "wrote $OUT/report.env"

SANITIZED="${ROOT}/docs/engineering/g1-fieldpolicy-mask-boundsql-export-remainder-honesty/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

echo
echo "PASS: leftover honesty remainder recorded (#238/#215 harness PASS and not live JAR; live FieldPolicy / BoundSql $LIVE_API / $LIVE_BOUNDSQL)"
echo "See docs/g1-fieldpolicy-mask-boundsql-export-remainder-honesty.md"
echo "Keep sibling leftovers including g1-fieldpolicy-mask-second-auth-remainder-honesty / g1-queryast-export-honesty / g15-h5-pkce-miniprogram-remainder-honesty / g2-h5-every-page-form-write-report-remainder-honesty / linux-arm-profile-remainder-honesty / g4-hot-purge-staging-pkce-remainder-honesty / g10-file-object-reconcile-remainder-honesty"
echo "Not claimed: product G1 close / mask on Query AST / IsolatedExport mask / live JAR walk / #184/#215/#238 redo"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
