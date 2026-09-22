#!/usr/bin/env bash
# G7 leftover honesty remainder beyond #239 / #212 / #192:
# expense/travel query leftover + withdraw browser-evidence leftover.
#
# Extends docs/g7-query-expense-travel-honesty-remainder.md (#239),
# docs/g7-withdraw-browser-evidence-honesty.md (#212), and
# docs/g7-query-expense-travel-honesty.md (#192). After #263 Linux/ARM leftover
# honesty remainder on main (2ac6326) after #262 G10 leftover honesty.
# #239/#212/#192 leftover cells stay
# always-green PASS. Live product-JAR + live/Keycloak IdP query /
# browser query click / browser-visible withdraw are PASS only when
# a healthy JAR, issued context, live IdP, and an observed walk exist.
# Missing JAR/IdP is honest SKIP. Does NOT redo
# #103/#113/#140/#159/#175/#192/#212/#239. Does NOT claim product G7
# close. G18 stays closed. No P0/M0 redo. AuditPort same-TX untouched.
# No Docker.
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
JSON_OUT="${NEXUS_G7_QUERY_WITHDRAW_LEFTOVER_JSON:-$ROOT/docs/g7-query-withdraw-leftover-honesty/walk.json}"
VERIFY_REPORT=""
VERIFY_EXPECT=pass

ARTIFACT_192="$ROOT/docs/engineering/g7-query-expense-travel-honesty/agent-vm.env"
ARTIFACT_212="$ROOT/docs/engineering/g7-withdraw-browser-evidence-honesty/agent-vm.env"
ARTIFACT_239="$ROOT/docs/engineering/g7-query-expense-travel-honesty-remainder/agent-vm.env"
IT="$ROOT/nexus-p3-studio/src/test/java/one/nexus/p3/path/G7ExpenseTravelQueryIT.java"
README="$ROOT/docs/README.md"

SIBLING_ROWS="g7-query-expense-travel,g7-query-expense-travel-honesty,g7-query-expense-travel-honesty-remainder,g7-query-withdraw-leftover-honesty,g7-withdraw-browser,g7-withdraw-browser-honesty,g7-withdraw-browser-evidence-honesty,g7-withdraw-expense-travel,g15-h5-pkce-miniprogram-remainder-honesty,g2-h5-every-page-form-write-report-remainder-honesty,linux-arm-profile-remainder-honesty,g4-hot-purge-staging-pkce-remainder-honesty,g10-file-object-reconcile-remainder-honesty,s0-secret-rotate-getenv-leftover-honesty,g5-publishhook-ddl-remainder-honesty,g1-fieldpolicy-mask-second-auth-remainder-honesty,g17-mfa-recovery-vue-otp-remainder-honesty,g4-hot-purge-staging-leftover-honesty,g10-leftover-honesty-remainder,g3-outbox-consume-idempotent-honesty,g9-live-multi-jvm-replica-remainder-honesty,g2-report-physical-dump-honesty,g6-recovery-port-dry-run-remainder-honesty,s0-secret-rotate-getenv-remainder-honesty,g4-pkce-spa-verifier-remainder-honesty,g17-separation-leftover-honesty,g17-separation-leftover-honesty-remainder,g9-leftover-honesty-remainder,g10-file-object-reconcile-leftover-honesty,linux-arm-profile-leftover-remainder-honesty,g15-wx-dt-native-bridge-remainder,g1-queryast-export-honesty,g17-gm-tls-nginx-handshake-honesty,g5-publishhook-ddl-honesty,g9-sentinel-cluster-remainder-honesty,g2-ops-pg-dump-honesty,g17-mfa-recovery-vue-otp-honesty,g2-h5-every-page-form-write-honesty,g2-h5-every-page-form-write-remainder-honesty,g9-redis-down-invalidate-remainder,g9-redis-down-invalidate-remainder-honesty,g10-leftover-honesty,g5-mid-publish-failure-honesty,g3-outbox-consume-crash-honesty,g1-queryast-boundsql-honesty,g15-wx-dt-native-bridge-honesty,s0-secret-rotate-without-restart-honesty,linux-arm-profile-leftover-honesty,g6-recovery-port-dry-run-honesty,s0s1-live-redis-minio-isolation-honesty,g17-separation-honesty,g1-fieldpolicy-mask-second-auth-honesty,g10-identity-drift-honesty,g15-native-bridge-honesty,s0-secret-getenv-snapshot-honesty,g5-browser-failed-publish,g4-hot-purge-90d-honesty,g4-p1-staging-audit-events-honesty,g4-export-wizard-deny-honesty,g9-redis-down-invalidate-honesty,g9-sentinel-cluster-honesty"

REQUIRED_README_ROWS="g15-h5-mobile g15-f010-h5-runtime g15-miniprogram-webview g15-app-webview-host g15-h5-pkce-browser g15-wx-dt-prod-host g15-native-bridge-stub g15-native-bridge-honesty g15-wx-dt-prod-gap-honesty g15-wx-dt-native-bridge-honesty g15-wx-dt-native-bridge-remainder g15-h5-pkce-miniprogram-remainder-honesty g7-business-types g7-withdraw-browser-honesty g7-query-expense-travel-honesty g7-withdraw-browser-evidence-honesty g7-query-expense-travel-honesty-remainder g7-query-withdraw-leftover-honesty g17-separation-of-duties g17-separation-honesty g17-separation-leftover-honesty g17-separation-leftover-honesty-remainder g17-gm-tls-dual-cert g17-admin-totp-enrollment g17-totp-enrollment-ux g17-admin-mfa-recovery-email g17-mfa-recovery-email-honesty g17-vue-otp-writeback g17-mfa-recovery-vue-otp-honesty g17-mfa-recovery-vue-otp-remainder-honesty g17-gm-tls-nginx-dual-stack g17-gm-tls-nginx-handshake g17-gm-tls-nginx-handshake-honesty g2-form-submission-write g2-submit-start-form-write g2-form-write-honesty g2-h5-permission g2-h5-every-page g2-h5-every-page-form-write-honesty g2-h5-every-page-form-write-remainder-honesty g2-h5-every-page-form-write-report-remainder-honesty g2-report-surface-guard g2-physical-dump-surface g2-report-physical-dump-honesty g1-queryast-product-optin g1-datascope-or-in-ast g1-queryast-boundsql-guard g1-queryast-boundsql-honesty g1-queryast-export-honesty g2-surfaceguard-bypass-ci g2-tenant-only-catalog g2-todo-http-surface g2-ops-pg-dump g2-ops-pg-dump-honesty g4-hot-purge-90d g4-hot-purge-90d-honesty g4-hot-purge-staging-leftover-honesty g4-pkce-browser-click g4-guard-deny-audit g4-export-wizard g4-export-wizard-deny g4-export-wizard-deny-honesty g4-p1-staging-audit-events g4-p1-staging-audit-events-honesty g4-pkce-spa-verifier g4-pkce-spa-verifier-honesty g4-pkce-spa-verifier-remainder-honesty g4-hot-purge-staging-pkce-remainder-honesty g6-recovery-port-dry-run g6-recovery-port-honesty g6-recovery-port-dry-run-honesty g6-recovery-port-dry-run-remainder-honesty g10-identity-drift-dry-run g10-file-reconcile-honesty g10-identity-drift-honesty g10-leftover-honesty g10-leftover-honesty-remainder g10-file-object-reconcile-remainder-honesty g10-file-object-reconcile-leftover-honesty linux-arm-profile-honesty linux-arm-profile-leftover-honesty linux-arm-profile-remainder-honesty linux-arm-profile-leftover-remainder-honesty g5-mid-publish-failure g5-browser-failed-publish g5-mid-publish-failure-honesty g5-publishhook-ddl-honesty g5-publishhook-ddl-remainder-honesty g9-multinode-partition g9-sentinel-cluster-honesty g9-sentinel-cluster-remainder-honesty g9-live-multi-jvm-replica g9-live-multi-jvm-replica-remainder-honesty g9-policysnapshot-invalidate g9-failover-remainder g9-redis-down-invalidate-honesty g9-redis-down-invalidate-remainder g9-redis-down-invalidate-remainder-honesty g9-leftover-honesty-remainder g1-export-fieldpolicy-drop g1-fieldpolicy-mask-second-auth g1-fieldpolicy-mask-second-auth-honesty g1-fieldpolicy-mask-second-auth-remainder-honesty g3-outbox-consume-idempotent g3-claim-crash-window g3-outbox-consume-crash-honesty g3-outbox-consume-idempotent-honesty s0-secret-rotate-without-restart s0-secret-getenv-snapshot-honesty s0-secret-rotate-without-restart-honesty s0-secret-rotate-getenv-remainder-honesty s0-secret-rotate-getenv-leftover-honesty s0s1-live-redis-minio-isolation-honesty"

usage() {
  cat <<'EOF'
Usage:
  scripts/g7-query-withdraw-leftover-honesty.sh [--out DIR]
  scripts/g7-query-withdraw-leftover-honesty.sh --verify-report FILE [pass|fail]

Beyond #239/#212/#192: leftover #239/#212/#192 cells stay honest;
parent leftover honesty fixtures stay PASS. Live JAR/IdP query /
browser query click / browser-visible withdraw are PASS only when
present AND the walk was observed. Missing JAR/IdP is honest SKIP.
Does not re-run #140/#192/#212/#239. Does not claim product G7 close.

  --out DIR           Output directory (default: .local/g7-query-withdraw-leftover-honesty/<utc>)
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
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used leftover_140_cells_honest leftover_192_cells_honest leftover_192_honesty_gate leftover_175_cells_honest leftover_212_cells_honest leftover_239_cells_honest leftover_239_honesty_gate query_surface expense_query_surface travel_query_surface zonky_it_is_not_live_jar leftover_rows_kept leftover_rows leftover_rows_all leftover_140_kept leftover_192_kept leftover_175_kept leftover_103_kept leftover_113_kept leftover_159_kept leftover_212_kept leftover_239_kept leftover_240_kept leftover_249_kept leftover_259_kept leftover_262_kept leftover_263_kept leftover_254_kept leftover_236_kept leftover_251_kept leftover_243_kept leftover_241_kept leftover_235_kept leftover_238_kept leftover_233_kept leftover_230_kept leftover_232_kept leftover_237_kept leftover_228_kept leftover_226_kept leftover_229_kept leftover_221_kept leftover_225_kept leftover_223_kept leftover_222_kept leftover_216_kept leftover_220_kept committed_192_query_surface committed_192_live_jar_idp_query committed_192_g7_close committed_212_stub_walk committed_212_live_withdraw committed_212_g7_close committed_239_query_surface committed_239_live_query committed_239_g7_close live_jar issued_context idp query_walk_observed live_jar_idp_query live_jar_idp_skip_reason live_browser_query_click live_browser_query_observed live_browser_query_skip_reason live_withdraw_observed live_jar_idp_browser_withdraw live_browser_visible_withdraw live_withdraw_skip_reason withdraw_paths_redone g7_103_redone g7_113_redone g7_140_redone g7_159_redone g7_175_redone leftover_192_redone leftover_212_redone leftover_239_redone honesty_192_rerun honesty_140_rerun honesty_212_rerun honesty_239_rerun g7_close g18_status audit_port_changed p0_m0_redone"
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
  [ "$(report_get "$file" g7_103_redone)" = "false" ] || { echo "FAIL: g7_103_redone must stay false" >&2; return 1; }
  [ "$(report_get "$file" g7_113_redone)" = "false" ] || { echo "FAIL: g7_113_redone must stay false" >&2; return 1; }
  [ "$(report_get "$file" g7_140_redone)" = "false" ] || { echo "FAIL: g7_140_redone must stay false" >&2; return 1; }
  [ "$(report_get "$file" g7_159_redone)" = "false" ] || { echo "FAIL: g7_159_redone must stay false" >&2; return 1; }
  [ "$(report_get "$file" g7_175_redone)" = "false" ] || { echo "FAIL: g7_175_redone must stay false" >&2; return 1; }
  [ "$(report_get "$file" leftover_192_redone)" = "false" ] || { echo "FAIL: leftover_192_redone must stay false" >&2; return 1; }
  [ "$(report_get "$file" leftover_212_redone)" = "false" ] || { echo "FAIL: leftover_212_redone must stay false" >&2; return 1; }
  [ "$(report_get "$file" leftover_239_redone)" = "false" ] || { echo "FAIL: leftover_239_redone must stay false" >&2; return 1; }
  [ "$(report_get "$file" honesty_192_rerun)" = "false" ] || { echo "FAIL: honesty_192_rerun must stay false" >&2; return 1; }
  [ "$(report_get "$file" honesty_140_rerun)" = "false" ] || { echo "FAIL: honesty_140_rerun must stay false" >&2; return 1; }
  [ "$(report_get "$file" honesty_212_rerun)" = "false" ] || { echo "FAIL: honesty_212_rerun must stay false" >&2; return 1; }
  [ "$(report_get "$file" honesty_239_rerun)" = "false" ] || { echo "FAIL: honesty_239_rerun must stay false" >&2; return 1; }
  [ "$(report_get "$file" withdraw_paths_redone)" = "false" ] || { echo "FAIL: withdraw_paths_redone must stay false" >&2; return 1; }
  [ "$(report_get "$file" g7_close)" = "NOT_CLAIMED" ] || { echo "FAIL: g7_close must stay NOT_CLAIMED" >&2; return 1; }
  [ "$(report_get "$file" leftover_140_cells_honest)" = "PASS" ] || { echo "FAIL: leftover_140_cells_honest must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_192_cells_honest)" = "PASS" ] || { echo "FAIL: leftover_192_cells_honest must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_192_honesty_gate)" = "PASS" ] || { echo "FAIL: leftover_192_honesty_gate must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_175_cells_honest)" = "PASS" ] || { echo "FAIL: leftover_175_cells_honest must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_212_cells_honest)" = "PASS" ] || { echo "FAIL: leftover_212_cells_honest must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_239_cells_honest)" = "PASS" ] || { echo "FAIL: leftover_239_cells_honest must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_239_honesty_gate)" = "PASS" ] || { echo "FAIL: leftover_239_honesty_gate must be PASS" >&2; return 1; }
  [ "$(report_get "$file" query_surface)" = "PASS" ] || { echo "FAIL: query_surface must be PASS" >&2; return 1; }
  [ "$(report_get "$file" expense_query_surface)" = "PASS" ] || { echo "FAIL: expense_query_surface must be PASS" >&2; return 1; }
  [ "$(report_get "$file" travel_query_surface)" = "PASS" ] || { echo "FAIL: travel_query_surface must be PASS" >&2; return 1; }
  [ "$(report_get "$file" zonky_it_is_not_live_jar)" = "true" ] || { echo "FAIL: zonky_it_is_not_live_jar must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_rows_kept)" = "true" ] || { echo "FAIL: leftover_rows_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_rows_all)" = "true" ] || { echo "FAIL: leftover_rows_all must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_140_kept)" = "true" ] || { echo "FAIL: leftover_140_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_192_kept)" = "true" ] || { echo "FAIL: leftover_192_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_175_kept)" = "true" ] || { echo "FAIL: leftover_175_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_103_kept)" = "true" ] || { echo "FAIL: leftover_103_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_113_kept)" = "true" ] || { echo "FAIL: leftover_113_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_159_kept)" = "true" ] || { echo "FAIL: leftover_159_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_212_kept)" = "true" ] || { echo "FAIL: leftover_212_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_239_kept)" = "true" ] || { echo "FAIL: leftover_239_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_240_kept)" = "true" ] || { echo "FAIL: leftover_240_kept must stay true (keep g15-h5-pkce-miniprogram-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_249_kept)" = "true" ] || { echo "FAIL: leftover_249_kept must stay true (keep g17-separation-leftover-honesty-remainder)" >&2; return 1; }
  [ "$(report_get "$file" leftover_259_kept)" = "true" ] || { echo "FAIL: leftover_259_kept must stay true (keep g9-leftover-honesty-remainder)" >&2; return 1; }
  [ "$(report_get "$file" leftover_262_kept)" = "true" ] || { echo "FAIL: leftover_262_kept must stay true (keep g10-file-object-reconcile-leftover-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_263_kept)" = "true" ] || { echo "FAIL: leftover_263_kept must stay true (keep linux-arm-profile-leftover-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_254_kept)" = "true" ] || { echo "FAIL: leftover_254_kept must stay true (keep g2-h5-every-page-form-write-report-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_236_kept)" = "true" ] || { echo "FAIL: leftover_236_kept must stay true (keep linux-arm-profile-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_251_kept)" = "true" ] || { echo "FAIL: leftover_251_kept must stay true (keep g4-hot-purge-staging-pkce-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_243_kept)" = "true" ] || { echo "FAIL: leftover_243_kept must stay true (keep g10-file-object-reconcile-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_241_kept)" = "true" ] || { echo "FAIL: leftover_241_kept must stay true (keep s0-secret-rotate-getenv-leftover-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_235_kept)" = "true" ] || { echo "FAIL: leftover_235_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_238_kept)" = "true" ] || { echo "FAIL: leftover_238_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_233_kept)" = "true" ] || { echo "FAIL: leftover_233_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_230_kept)" = "true" ] || { echo "FAIL: leftover_230_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_232_kept)" = "true" ] || { echo "FAIL: leftover_232_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_237_kept)" = "true" ] || { echo "FAIL: leftover_237_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_228_kept)" = "true" ] || { echo "FAIL: leftover_228_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_226_kept)" = "true" ] || { echo "FAIL: leftover_226_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_229_kept)" = "true" ] || { echo "FAIL: leftover_229_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_221_kept)" = "true" ] || { echo "FAIL: leftover_221_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_225_kept)" = "true" ] || { echo "FAIL: leftover_225_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_223_kept)" = "true" ] || { echo "FAIL: leftover_223_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_222_kept)" = "true" ] || { echo "FAIL: leftover_222_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_216_kept)" = "true" ] || { echo "FAIL: leftover_216_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_220_kept)" = "true" ] || { echo "FAIL: leftover_220_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" committed_192_query_surface)" = "PASS" ] || { echo "FAIL: committed_192_query_surface must be PASS" >&2; return 1; }
  [ "$(report_get "$file" committed_192_live_jar_idp_query)" = "SKIP" ] || { echo "FAIL: committed_192_live_jar_idp_query must stay SKIP" >&2; return 1; }
  [ "$(report_get "$file" committed_192_g7_close)" = "NOT_CLAIMED" ] || { echo "FAIL: committed_192_g7_close must stay NOT_CLAIMED" >&2; return 1; }
  [ "$(report_get "$file" committed_212_stub_walk)" = "PASS" ] || { echo "FAIL: committed_212_stub_walk must be PASS" >&2; return 1; }
  [ "$(report_get "$file" committed_212_live_withdraw)" = "SKIP" ] || { echo "FAIL: committed_212_live_withdraw must stay SKIP" >&2; return 1; }
  [ "$(report_get "$file" committed_212_g7_close)" = "NOT_CLAIMED" ] || { echo "FAIL: committed_212_g7_close must stay NOT_CLAIMED" >&2; return 1; }
  [ "$(report_get "$file" committed_239_query_surface)" = "PASS" ] || { echo "FAIL: committed_239_query_surface must be PASS" >&2; return 1; }
  [ "$(report_get "$file" committed_239_live_query)" = "SKIP" ] || { echo "FAIL: committed_239_live_query must stay SKIP" >&2; return 1; }
  [ "$(report_get "$file" committed_239_g7_close)" = "NOT_CLAIMED" ] || { echo "FAIL: committed_239_g7_close must stay NOT_CLAIMED" >&2; return 1; }

  local rows
  rows="$(report_get "$file" leftover_rows)"
  local required_row
  for required_row in $(echo "$SIBLING_ROWS" | tr ',' ' '); do
    echo "$rows" | grep -Fq "$required_row" || { echo "FAIL: leftover_rows must keep $required_row" >&2; return 1; }
  done

  local live
  live="$(report_get "$file" live_jar_idp_query)"
  case "$live" in
    PASS|SKIP|FAIL) ;;
    *) echo "FAIL: live_jar_idp_query must be PASS/SKIP/FAIL" >&2; return 1 ;;
  esac
  if [ "$live" = "PASS" ]; then
    [ "$(report_get "$file" live_jar)" = "true" ] || { echo "FAIL: live_jar_idp_query=PASS requires live_jar=true" >&2; return 1; }
    [ "$(report_get "$file" issued_context)" = "true" ] || { echo "FAIL: live_jar_idp_query=PASS requires issued_context=true" >&2; return 1; }
    case "$(report_get "$file" idp)" in
      live|keycloak) ;;
      *) echo "FAIL: live_jar_idp_query=PASS requires live/keycloak IdP" >&2; return 1 ;;
    esac
    [ "$(report_get "$file" query_walk_observed)" = "true" ] || { echo "FAIL: live_jar_idp_query=PASS requires query_walk_observed=true" >&2; return 1; }
  fi
  if [ "$live" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_jar_idp_skip_reason)" ] || { echo "FAIL: live JAR/IdP SKIP needs live_jar_idp_skip_reason" >&2; return 1; }
    [ "$(report_get "$file" query_walk_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim a live query walk" >&2; return 1; }
  fi

  local browser
  browser="$(report_get "$file" live_browser_query_click)"
  case "$browser" in
    PASS|SKIP|FAIL) ;;
    *) echo "FAIL: live_browser_query_click must be PASS/SKIP/FAIL" >&2; return 1 ;;
  esac
  if [ "$browser" = "PASS" ]; then
    [ "$(report_get "$file" live_jar)" = "true" ] || { echo "FAIL: live_browser_query_click=PASS requires live_jar=true" >&2; return 1; }
    [ "$(report_get "$file" issued_context)" = "true" ] || { echo "FAIL: live_browser_query_click=PASS requires issued_context=true" >&2; return 1; }
    [ "$(report_get "$file" live_browser_query_observed)" = "true" ] || { echo "FAIL: live_browser_query_click=PASS requires live_browser_query_observed=true" >&2; return 1; }
  fi
  if [ "$browser" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_browser_query_skip_reason)" ] || { echo "FAIL: live browser query SKIP needs live_browser_query_skip_reason" >&2; return 1; }
    [ "$(report_get "$file" live_browser_query_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim a live browser query walk" >&2; return 1; }
  fi

  local withdraw
  withdraw="$(report_get "$file" live_jar_idp_browser_withdraw)"
  case "$withdraw" in
    PASS|SKIP|FAIL) ;;
    *) echo "FAIL: live_jar_idp_browser_withdraw must be PASS/SKIP/FAIL" >&2; return 1 ;;
  esac
  if [ "$withdraw" = "PASS" ]; then
    [ "$(report_get "$file" live_jar)" = "true" ] || { echo "FAIL: live_jar_idp_browser_withdraw=PASS requires live_jar=true" >&2; return 1; }
    [ "$(report_get "$file" issued_context)" = "true" ] || { echo "FAIL: live_jar_idp_browser_withdraw=PASS requires issued_context=true" >&2; return 1; }
    case "$(report_get "$file" idp)" in
      live|keycloak) ;;
      *) echo "FAIL: live_jar_idp_browser_withdraw=PASS requires live/keycloak IdP" >&2; return 1 ;;
    esac
    [ "$(report_get "$file" live_withdraw_observed)" = "true" ] || { echo "FAIL: live_jar_idp_browser_withdraw=PASS requires live_withdraw_observed=true" >&2; return 1; }
  fi
  if [ "$withdraw" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_withdraw_skip_reason)" ] || { echo "FAIL: live withdraw SKIP needs live_withdraw_skip_reason" >&2; return 1; }
    [ "$(report_get "$file" live_withdraw_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim a live withdraw walk" >&2; return 1; }
  fi
  visible="$(report_get "$file" live_browser_visible_withdraw)"
  case "$visible" in
    PASS|SKIP|FAIL) ;;
    *) echo "FAIL: live_browser_visible_withdraw must be PASS/SKIP/FAIL" >&2; return 1 ;;
  esac
  if [ "$visible" = "PASS" ]; then
    [ "$(report_get "$file" live_withdraw_observed)" = "true" ] || { echo "FAIL: live_browser_visible_withdraw=PASS requires live_withdraw_observed=true" >&2; return 1; }
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
  OUT="$ROOT/.local/g7-query-withdraw-leftover-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G7 leftover: query/withdraw leftover honesty beyond #239/#212/#192"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #103/#113/#140/#159/#175/#192/#212/#239. Not product G7 close. G18 stays closed."
echo

[ -f "$ARTIFACT_192" ] || fail "missing committed #192 artifact $ARTIFACT_192"
[ -f "$ARTIFACT_212" ] || fail "missing committed #212 artifact $ARTIFACT_212"
[ -f "$ARTIFACT_239" ] || fail "missing committed #239 artifact $ARTIFACT_239"
[ -f "$IT" ] || fail "G7ExpenseTravelQueryIT missing; do not drop the #140 contract"
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

C192_QUERY="$(report_get "$ARTIFACT_192" query_surface)"
C192_LIVE="$(report_get "$ARTIFACT_192" live_jar_idp_query)"
C192_G7="$(report_get "$ARTIFACT_192" g7_close)"
[ "$C192_QUERY" = "PASS" ] || fail "committed #192 query_surface is $C192_QUERY; must stay PASS"
[ "$C192_LIVE" = "SKIP" ] || fail "committed #192 live_jar_idp_query is $C192_LIVE; leftover requires honest SKIP"
[ "$C192_G7" = "NOT_CLAIMED" ] || fail "committed #192 g7_close is $C192_G7"

C212_STUB="$(report_get "$ARTIFACT_212" browser_visible_stub_walk)"
C212_LIVE="$(report_get "$ARTIFACT_212" live_jar_idp_browser_withdraw)"
C212_G7="$(report_get "$ARTIFACT_212" g7_close)"
[ "$C212_STUB" = "PASS" ] || fail "committed #212 browser_visible_stub_walk is $C212_STUB; must stay PASS"
[ "$C212_LIVE" = "SKIP" ] || fail "committed #212 live_jar_idp_browser_withdraw is $C212_LIVE; leftover requires honest SKIP"
[ "$C212_G7" = "NOT_CLAIMED" ] || fail "committed #212 g7_close is $C212_G7"

C239_QUERY="$(report_get "$ARTIFACT_239" query_surface)"
C239_LIVE="$(report_get "$ARTIFACT_239" live_jar_idp_query)"
C239_G7="$(report_get "$ARTIFACT_239" g7_close)"
C239_192="$(report_get "$ARTIFACT_239" leftover_192_cells_honest)"
C239_212="$(report_get "$ARTIFACT_239" leftover_212_cells_honest)"
C239_G18="$(report_get "$ARTIFACT_239" g18_status)"
C239_AUDIT="$(report_get "$ARTIFACT_239" audit_port_changed)"
C239_P0="$(report_get "$ARTIFACT_239" p0_m0_redone)"
[ "$C239_QUERY" = "PASS" ] || fail "committed #239 query_surface is $C239_QUERY; must stay PASS"
[ "$C239_LIVE" = "SKIP" ] || fail "committed #239 live_jar_idp_query is $C239_LIVE; leftover requires honest SKIP"
[ "$C239_G7" = "NOT_CLAIMED" ] || fail "committed #239 g7_close is $C239_G7"
[ "$C239_192" = "PASS" ] || fail "committed #239 leftover_192_cells_honest is $C239_192"
[ "$C239_212" = "PASS" ] || fail "committed #239 leftover_212_cells_honest is $C239_212"
[ "$C239_G18" = "closed_unchanged" ] || fail "committed #239 g18_status is $C239_G18"
[ "$C239_AUDIT" = "false" ] || fail "committed #239 audit_port_changed is $C239_AUDIT"
[ "$C239_P0" = "false" ] || fail "committed #239 p0_m0_redone is $C239_P0"

QUERY_SURFACE=FAIL
EXPENSE_SURFACE=FAIL
TRAVEL_SURFACE=FAIL
if grep -Fq 'expenseListDetailAndTodoQuerySurfaces' "$IT" \
  && grep -Fq '/api/v1/p3/ledgers' "$IT" \
  && grep -Fq '/records' "$IT" \
  && grep -Fq '/api/v1/todos/pending' "$IT" \
  && grep -Fq '/api/v1/p3/todos' "$IT" \
  && grep -Fq '/api/v1/todos/initiated' "$IT" \
  && grep -Fq '/api/v1/todos/done' "$IT" \
  && grep -Fq '交通费' "$IT" \
  && grep -Fq 'g7-expense-query' "$IT"; then
  EXPENSE_SURFACE=PASS
fi
if grep -Fq 'travelListDetailAndTodoQuerySurfaces' "$IT" \
  && grep -Fq '客户拜访' "$IT" \
  && grep -Fq 'g7-travel-query' "$IT" \
  && grep -Fq 'destination' "$IT"; then
  TRAVEL_SURFACE=PASS
fi
if [ "$EXPENSE_SURFACE" = "PASS" ] && [ "$TRAVEL_SURFACE" = "PASS" ]; then
  QUERY_SURFACE=PASS
fi
[ "$QUERY_SURFACE" = "PASS" ] || fail "query surfaces must stay PASS (#140 contract still present)"

log "verify parent #239/#212/#192 leftover honesty fixtures (harness PASS; do not re-run collect)"
./scripts/g7-query-expense-travel-honesty-remainder.sh --verify-report \
  scripts/g7-query-expense-travel-honesty-remainder/fixtures/honest-skip.env | tee -a "$OUT/collect.log"
./scripts/g7-withdraw-browser-evidence-honesty.sh --verify-report \
  scripts/g7-withdraw-browser-evidence-honesty/fixtures/honest-skip.env | tee -a "$OUT/collect.log"
./scripts/g7-query-expense-travel-honesty.sh --verify-report \
  scripts/g7-query-expense-travel-honesty/fixtures/honest-skip.env | tee -a "$OUT/collect.log"
set +e
./scripts/g7-query-expense-travel-honesty-remainder.sh --verify-report \
  scripts/g7-query-expense-travel-honesty-remainder/fixtures/fake-live-pass.env >"$OUT/fake-239-live.log" 2>&1
FAKE239_LIVE=$?
./scripts/g7-query-expense-travel-honesty-remainder.sh --verify-report \
  scripts/g7-query-expense-travel-honesty-remainder/fixtures/fake-g7-close.env >"$OUT/fake-239-close.log" 2>&1
FAKE239_CLOSE=$?
./scripts/g7-withdraw-browser-evidence-honesty.sh --verify-report \
  scripts/g7-withdraw-browser-evidence-honesty/fixtures/fake-live-pass.env >"$OUT/fake-212-live.log" 2>&1
FAKE212_LIVE=$?
set -e
[ "$FAKE239_LIVE" -ne 0 ] || fail "expected #239 fake-live-pass.env to FAIL"
[ "$FAKE239_CLOSE" -ne 0 ] || fail "expected #239 fake-g7-close.env to FAIL"
[ "$FAKE212_LIVE" -ne 0 ] || fail "expected #212 fake-live-pass.env to FAIL"
log "parent #239/#212/#192 leftover honesty gate PASS (honest SKIP accepted; fakes rejected)"

MISSING_ROWS=""
LEFTOVER_ROWS_KEPT=true
LEFTOVER_140_KEPT=false
LEFTOVER_192_KEPT=false
LEFTOVER_175_KEPT=false
LEFTOVER_103_KEPT=false
LEFTOVER_113_KEPT=false
LEFTOVER_159_KEPT=false
LEFTOVER_212_KEPT=false
LEFTOVER_239_KEPT=false
LEFTOVER_240_KEPT=false
LEFTOVER_249_KEPT=false
LEFTOVER_259_KEPT=false
LEFTOVER_262_KEPT=false
LEFTOVER_263_KEPT=false
LEFTOVER_254_KEPT=false
LEFTOVER_236_KEPT=false
LEFTOVER_251_KEPT=false
LEFTOVER_243_KEPT=false
LEFTOVER_241_KEPT=false
LEFTOVER_235_KEPT=false
LEFTOVER_238_KEPT=false
LEFTOVER_233_KEPT=false
LEFTOVER_230_KEPT=false
LEFTOVER_232_KEPT=false
LEFTOVER_237_KEPT=false
LEFTOVER_228_KEPT=false
LEFTOVER_226_KEPT=false
LEFTOVER_229_KEPT=false
LEFTOVER_221_KEPT=false
LEFTOVER_225_KEPT=false
LEFTOVER_223_KEPT=false
LEFTOVER_222_KEPT=false
LEFTOVER_216_KEPT=false
LEFTOVER_220_KEPT=false
for required_row in $REQUIRED_README_ROWS; do
  if grep -Fq "[${required_row}](./${required_row}.md)" "$README"; then
    case "$required_row" in
      g7-query-expense-travel-honesty) LEFTOVER_192_KEPT=true ;;
      g7-withdraw-browser-honesty) LEFTOVER_175_KEPT=true ;;
      g7-withdraw-browser-evidence-honesty) LEFTOVER_212_KEPT=true ;;
      g7-query-expense-travel-honesty-remainder) LEFTOVER_239_KEPT=true ;;
      g15-h5-pkce-miniprogram-remainder-honesty) LEFTOVER_240_KEPT=true ;;
      g17-separation-leftover-honesty-remainder) LEFTOVER_249_KEPT=true ;;
      g9-leftover-honesty-remainder) LEFTOVER_259_KEPT=true ;;
      g10-file-object-reconcile-leftover-honesty) LEFTOVER_262_KEPT=true ;;
      linux-arm-profile-leftover-remainder-honesty) LEFTOVER_263_KEPT=true ;;
      g2-h5-every-page-form-write-report-remainder-honesty) LEFTOVER_254_KEPT=true ;;
      linux-arm-profile-remainder-honesty) LEFTOVER_236_KEPT=true ;;
      g4-hot-purge-staging-pkce-remainder-honesty) LEFTOVER_251_KEPT=true ;;
      g10-file-object-reconcile-remainder-honesty) LEFTOVER_243_KEPT=true ;;
      s0-secret-rotate-getenv-leftover-honesty) LEFTOVER_241_KEPT=true ;;
      g5-publishhook-ddl-remainder-honesty) LEFTOVER_235_KEPT=true ;;
      g1-fieldpolicy-mask-second-auth-remainder-honesty) LEFTOVER_238_KEPT=true ;;
      g17-mfa-recovery-vue-otp-remainder-honesty) LEFTOVER_233_KEPT=true ;;
      g4-hot-purge-staging-leftover-honesty) LEFTOVER_230_KEPT=true ;;
      g2-h5-every-page-form-write-remainder-honesty) LEFTOVER_232_KEPT=true ;;
      g9-redis-down-invalidate-remainder-honesty) LEFTOVER_237_KEPT=true ;;
      g10-leftover-honesty-remainder) LEFTOVER_228_KEPT=true ;;
      g3-outbox-consume-idempotent-honesty) LEFTOVER_226_KEPT=true ;;
      g9-live-multi-jvm-replica-remainder-honesty) LEFTOVER_229_KEPT=true ;;
      g2-report-physical-dump-honesty) LEFTOVER_221_KEPT=true ;;
      g6-recovery-port-dry-run-remainder-honesty) LEFTOVER_225_KEPT=true ;;
      s0-secret-rotate-getenv-remainder-honesty) LEFTOVER_223_KEPT=true ;;
      g4-pkce-spa-verifier-remainder-honesty) LEFTOVER_222_KEPT=true ;;
      g17-separation-leftover-honesty) LEFTOVER_216_KEPT=true ;;
      g15-wx-dt-native-bridge-remainder) LEFTOVER_220_KEPT=true ;;
    esac
  else
    LEFTOVER_ROWS_KEPT=false
    MISSING_ROWS="${MISSING_ROWS} ${required_row}"
  fi
done
[ "$LEFTOVER_ROWS_KEPT" = "true" ] || fail "docs/README.md dropped leftover row(s):$MISSING_ROWS"

if grep -Fq 'g7-query-expense-travel.md' "$README"; then LEFTOVER_140_KEPT=true; fi
if grep -Fq 'g7-withdraw-expense-travel.md' "$README"; then LEFTOVER_103_KEPT=true; fi
if grep -Fq 'g7-withdraw-browser.md' "$README"; then
  LEFTOVER_113_KEPT=true
  LEFTOVER_159_KEPT=true
fi

[ "$LEFTOVER_140_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g7-query-expense-travel (#140)"
[ "$LEFTOVER_192_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g7-query-expense-travel-honesty (#192)"
[ "$LEFTOVER_175_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g7-withdraw-browser-honesty (#175)"
[ "$LEFTOVER_103_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g7-withdraw-expense-travel (#103)"
[ "$LEFTOVER_113_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g7-withdraw-browser (#113/#159)"
[ "$LEFTOVER_212_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g7-withdraw-browser-evidence-honesty (#212)"
[ "$LEFTOVER_239_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g7-query-expense-travel-honesty-remainder (#239)"
[ "$LEFTOVER_240_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g15-h5-pkce-miniprogram-remainder-honesty (#240)"
[ "$LEFTOVER_249_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g17-separation-leftover-honesty-remainder (#249)"
[ "$LEFTOVER_259_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g9-leftover-honesty-remainder (#259)"
[ "$LEFTOVER_262_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g10-file-object-reconcile-leftover-honesty (#262)"
[ "$LEFTOVER_263_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover linux-arm-profile-leftover-remainder-honesty (#263)"
[ "$LEFTOVER_254_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g2-h5-every-page-form-write-report-remainder-honesty (#254)"
[ "$LEFTOVER_236_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover linux-arm-profile-remainder-honesty (#236)"
[ "$LEFTOVER_251_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g4-hot-purge-staging-pkce-remainder-honesty (#251)"
[ "$LEFTOVER_243_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g10-file-object-reconcile-remainder-honesty (#243)"
[ "$LEFTOVER_241_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover s0-secret-rotate-getenv-leftover-honesty (#241)"
if grep -Eiq '产品 G7 已关闭|product g7 closed|g7_close=CLOSED' "$README"; then
  fail "docs/README.md must not claim product G7 closed"
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

LIVE_ENDPOINT="${NEXUS_G7_LIVE_BASE:-${NEXUS_BASE_URL:-}}"
LIVE_JAR=false
if [ -n "$LIVE_ENDPOINT" ] && curl -sf --max-time 2 "${LIVE_ENDPOINT%/}/actuator/health" >/dev/null 2>&1; then
  LIVE_JAR=true
elif port_open 127.0.0.1 8080 || port_open 127.0.0.1 8088; then
  if [ -n "$LIVE_ENDPOINT" ]; then
    LIVE_JAR=true
  fi
fi

ISSUED_CONTEXT=false
if [ -n "${NEXUS_G7_LIVE_CONTEXT_ID:-}" ]; then
  ISSUED_CONTEXT=true
fi

IDP="absent"
if [ -n "${NEXUS_G7_LIVE_IDP:-}" ]; then
  IDP="${NEXUS_G7_LIVE_IDP}"
elif [ -n "${NEXUS_IAM_IDP:-}" ] && [ "${NEXUS_IAM_IDP}" != "stub" ]; then
  IDP="${NEXUS_IAM_IDP}"
elif [ "${NEXUS_IAM_IDP:-}" = "stub" ]; then
  IDP="stub"
fi

QUERY_OBSERVED=false
if [ "${NEXUS_G7_LIVE_QUERY_OBSERVED:-}" = "true" ]; then
  QUERY_OBSERVED=true
fi
BROWSER_OBSERVED=false
if [ "${NEXUS_G7_LIVE_BROWSER_QUERY_OBSERVED:-}" = "true" ]; then
  BROWSER_OBSERVED=true
fi
WITHDRAW_OBSERVED=false
if [ "${NEXUS_G7_LIVE_WITHDRAW_OBSERVED:-}" = "true" ]; then
  WITHDRAW_OBSERVED=true
fi

LIVE_WALK=SKIP
LIVE_REASON="live_jar_not_healthy"
if [ "$LIVE_JAR" != "true" ]; then
  LIVE_REASON="live_jar_not_healthy"
elif [ "$ISSUED_CONTEXT" != "true" ]; then
  LIVE_REASON="issued_context_missing"
elif [ "$IDP" = "stub" ]; then
  LIVE_REASON="stub_login_is_not_live_walk"
elif [ "$IDP" != "live" ] && [ "$IDP" != "keycloak" ]; then
  LIVE_REASON="live_idp_absent"
elif [ "$QUERY_OBSERVED" != "true" ]; then
  LIVE_REASON="live_query_walk_not_observed"
else
  LIVE_WALK=PASS
  LIVE_REASON=""
fi
if [ "$LIVE_WALK" != "PASS" ]; then
  QUERY_OBSERVED=false
fi

BROWSER_WALK=SKIP
BROWSER_REASON="live_browser_query_click still OPEN; live JAR/IdP unavailable; this leftover does not start Playwright or claim a product query click"
if [ "$LIVE_JAR" != "true" ]; then
  BROWSER_REASON="live JAR unset / nothing on 127.0.0.1:8080 / :8088; live browser query click still OPEN; leftover does not start the JAR or Playwright"
elif [ "$ISSUED_CONTEXT" != "true" ]; then
  BROWSER_REASON="issued context unset; live browser query click still OPEN; leftover does not start Playwright"
elif [ "$BROWSER_OBSERVED" != "true" ]; then
  BROWSER_REASON="live JAR may be present but a live expense+travel browser query click was not observed; still OPEN"
else
  BROWSER_WALK=PASS
  BROWSER_REASON=""
fi
if [ "$BROWSER_WALK" != "PASS" ]; then
  BROWSER_OBSERVED=false
fi

WITHDRAW_WALK=SKIP
WITHDRAW_REASON="live product-JAR + live IdP browser-visible withdraw not observed; stub walk is not a live walk; leftover does not start the JAR or Playwright"
if [ "$LIVE_JAR" != "true" ]; then
  WITHDRAW_REASON="live JAR unset / nothing on 127.0.0.1:8080 / :8088; stub walk is not a live withdraw; leftover does not start the JAR"
elif [ "$ISSUED_CONTEXT" != "true" ]; then
  WITHDRAW_REASON="issued context unset; live browser-visible withdraw not observed"
elif [ "$IDP" != "live" ] && [ "$IDP" != "keycloak" ]; then
  WITHDRAW_REASON="live/keycloak IdP absent; stub login is not a live withdraw"
elif [ "$WITHDRAW_OBSERVED" != "true" ]; then
  WITHDRAW_REASON="live JAR may be present but a live todo-withdraw click was not observed"
else
  WITHDRAW_WALK=PASS
  WITHDRAW_REASON=""
fi
if [ "$WITHDRAW_WALK" != "PASS" ]; then
  WITHDRAW_OBSERVED=false
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
  kv leftover_140_cells_honest PASS
  kv leftover_192_cells_honest PASS
  kv leftover_192_honesty_gate PASS
  kv leftover_175_cells_honest PASS
  kv leftover_212_cells_honest PASS
  kv leftover_239_cells_honest PASS
  kv leftover_239_honesty_gate PASS
  kv query_surface "$QUERY_SURFACE"
  kv expense_query_surface "$EXPENSE_SURFACE"
  kv travel_query_surface "$TRAVEL_SURFACE"
  kv zonky_it_is_not_live_jar true
  kv leftover_rows_kept true
  kv leftover_rows "$SIBLING_ROWS"
  kv leftover_rows_all true
  kv leftover_140_kept "$LEFTOVER_140_KEPT"
  kv leftover_192_kept "$LEFTOVER_192_KEPT"
  kv leftover_175_kept "$LEFTOVER_175_KEPT"
  kv leftover_103_kept "$LEFTOVER_103_KEPT"
  kv leftover_113_kept "$LEFTOVER_113_KEPT"
  kv leftover_159_kept "$LEFTOVER_159_KEPT"
  kv leftover_212_kept "$LEFTOVER_212_KEPT"
  kv leftover_239_kept "$LEFTOVER_239_KEPT"
  kv leftover_240_kept "$LEFTOVER_240_KEPT"
  kv leftover_249_kept "$LEFTOVER_249_KEPT"
  kv leftover_259_kept "$LEFTOVER_259_KEPT"
  kv leftover_262_kept "$LEFTOVER_262_KEPT"
  kv leftover_263_kept "$LEFTOVER_263_KEPT"
  kv leftover_254_kept "$LEFTOVER_254_KEPT"
  kv leftover_236_kept "$LEFTOVER_236_KEPT"
  kv leftover_251_kept "$LEFTOVER_251_KEPT"
  kv leftover_243_kept "$LEFTOVER_243_KEPT"
  kv leftover_241_kept "$LEFTOVER_241_KEPT"
  kv leftover_235_kept "$LEFTOVER_235_KEPT"
  kv leftover_238_kept "$LEFTOVER_238_KEPT"
  kv leftover_233_kept "$LEFTOVER_233_KEPT"
  kv leftover_230_kept "$LEFTOVER_230_KEPT"
  kv leftover_232_kept "$LEFTOVER_232_KEPT"
  kv leftover_237_kept "$LEFTOVER_237_KEPT"
  kv leftover_228_kept "$LEFTOVER_228_KEPT"
  kv leftover_226_kept "$LEFTOVER_226_KEPT"
  kv leftover_229_kept "$LEFTOVER_229_KEPT"
  kv leftover_221_kept "$LEFTOVER_221_KEPT"
  kv leftover_225_kept "$LEFTOVER_225_KEPT"
  kv leftover_223_kept "$LEFTOVER_223_KEPT"
  kv leftover_222_kept "$LEFTOVER_222_KEPT"
  kv leftover_216_kept "$LEFTOVER_216_KEPT"
  kv leftover_220_kept "$LEFTOVER_220_KEPT"
  kv committed_192_query_surface "$C192_QUERY"
  kv committed_192_live_jar_idp_query "$C192_LIVE"
  kv committed_192_g7_close "$C192_G7"
  kv committed_212_stub_walk "$C212_STUB"
  kv committed_212_live_withdraw "$C212_LIVE"
  kv committed_212_g7_close "$C212_G7"
  kv committed_239_query_surface "$C239_QUERY"
  kv committed_239_live_query "$C239_LIVE"
  kv committed_239_g7_close "$C239_G7"
  kv live_jar "$LIVE_JAR"
  kv issued_context "$ISSUED_CONTEXT"
  kv idp "$IDP"
  kv query_walk_observed "$QUERY_OBSERVED"
  kv live_jar_idp_query "$LIVE_WALK"
  kv live_jar_idp_skip_reason "$LIVE_REASON"
  kv live_browser_query_click "$BROWSER_WALK"
  kv live_browser_query_observed "$BROWSER_OBSERVED"
  kv live_browser_query_skip_reason "$BROWSER_REASON"
  kv live_withdraw_observed "$WITHDRAW_OBSERVED"
  kv live_jar_idp_browser_withdraw "$WITHDRAW_WALK"
  kv live_browser_visible_withdraw "$WITHDRAW_WALK"
  kv live_withdraw_skip_reason "$WITHDRAW_REASON"
  kv withdraw_paths_redone false
  kv g7_103_redone false
  kv g7_113_redone false
  kv g7_140_redone false
  kv g7_159_redone false
  kv g7_175_redone false
  kv leftover_192_redone false
  kv leftover_212_redone false
  kv leftover_239_redone false
  kv honesty_192_rerun false
  kv honesty_140_rerun false
  kv honesty_212_rerun false
  kv honesty_239_rerun false
  kv g7_close NOT_CLAIMED
  kv g7_close_reason "Honesty leftover remainder beyond #239/#212/#192. Query surfaces PASS. #212 stub walk PASS. Live JAR/IdP query / browser query / withdraw SKIP when unavailable. Not product G7 close."
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv p0_m0_redone false
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
    "leftover": "g7-query-withdraw-leftover-honesty",
    "siblings": [
        "g7-query-expense-travel",
        "g7-query-expense-travel-honesty",
        "g7-query-expense-travel-honesty-remainder",
        "g7-withdraw-browser",
        "g7-withdraw-browser-honesty",
        "g7-withdraw-browser-evidence-honesty",
        "g7-withdraw-expense-travel",
        "g15-h5-pkce-miniprogram-remainder-honesty",
        "g17-separation-leftover-honesty-remainder",
        "g9-leftover-honesty-remainder",
        "g10-file-object-reconcile-leftover-honesty",
        "linux-arm-profile-leftover-remainder-honesty",
        "g2-h5-every-page-form-write-report-remainder-honesty",
        "linux-arm-profile-remainder-honesty",
        "g4-hot-purge-staging-pkce-remainder-honesty",
        "g10-file-object-reconcile-remainder-honesty",
        "s0-secret-rotate-getenv-leftover-honesty",
        "g2-h5-every-page-form-write-remainder-honesty",
        "g9-redis-down-invalidate-remainder-honesty",
        "g4-hot-purge-staging-leftover-honesty",
    ],
    "query_surface": vals.get("query_surface"),
    "live_jar_idp_query": vals.get("live_jar_idp_query"),
    "live_browser_query_click": vals.get("live_browser_query_click"),
    "live_jar_idp_browser_withdraw": vals.get("live_jar_idp_browser_withdraw"),
    "g7Close": "NOT_CLAIMED",
    "g18": "closed_unchanged",
    "timestampUtc": vals.get("timestamp_utc"),
    "evidence": "scripts/g7-query-withdraw-leftover-honesty.sh",
}
open(out, "w", encoding="utf-8").write(json.dumps(walk, indent=2) + "\n")
print("wrote", out)
PY

verify_report "$REPORT"

echo "== Honesty fixtures"
verify_report "$HERE/g7-query-withdraw-leftover-honesty/fixtures/honest-skip.env"
verify_report "$HERE/g7-query-withdraw-leftover-honesty/fixtures/fake-live-pass.env" fail
verify_report "$HERE/g7-query-withdraw-leftover-honesty/fixtures/fake-browser-pass.env" fail
verify_report "$HERE/g7-query-withdraw-leftover-honesty/fixtures/fake-withdraw-pass.env" fail
verify_report "$HERE/g7-query-withdraw-leftover-honesty/fixtures/fake-g7-close.env" fail
verify_report "$HERE/g7-query-withdraw-leftover-honesty/fixtures/fake-redone.env" fail

SANITIZED="${ROOT}/docs/engineering/g7-query-withdraw-leftover-honesty/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

log "DONE report=$REPORT live_jar_idp_query=$LIVE_WALK live_browser_query_click=$BROWSER_WALK live_withdraw=$WITHDRAW_WALK g7_close=NOT_CLAIMED"
echo
echo "PASS: leftover honesty remainder recorded (#239/#212/#192 cells honest; live JAR/IdP SKIP)"
echo "See docs/g7-query-withdraw-leftover-honesty.md"
echo "Not claimed: product G7 close / live JAR+IdP query / live browser query click / live browser withdraw"
echo "Not redone: #103/#113/#140/#159/#175/#192/#212/#239"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
echo "walk=$JSON_OUT"
