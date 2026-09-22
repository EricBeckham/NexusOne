#!/usr/bin/env bash
# G17 leftover remainder: 三权分立 admin-force vs dual-control honesty
# beyond #216 / #187 / #99.
#
# Extends docs/g17-separation-leftover-honesty.md and
# docs/g17-separation-honesty.md. After #251 G4 hot-purge/staging/PKCE
# leftover honesty on main (5af7a84) after #243 / #241 / #235 / #233 / #238 / #239 / #237 / #232.
# Parent #216 / #187 harness --verify-report fixtures stay PASS.
# That #216 harness PASS is NOT a live admin-force / dual-control walk.
# Live IdP / admin-force / dual-control walks stay honest SKIP unless
# observed. Does NOT redo #96/#99/#187/#216. Does NOT claim product
# G17 / SoD / dual-control / 等保 / 密评 close. G18 stays closed.
# No P0/M0 redo. AuditPort same-TX untouched. No Docker.
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
JSON_OUT="${NEXUS_G17_SOD_REMAINDER_JSON:-$ROOT/docs/g17-separation-leftover-honesty-remainder/walk.json}"
VERIFY_REPORT=""
VERIFY_EXPECT=pass

ARTIFACT_187="$ROOT/docs/engineering/g17-separation-honesty/agent-vm.env"
ARTIFACT_216="$ROOT/docs/engineering/g17-separation-leftover-honesty/agent-vm.env"
FIXTURE_187="$ROOT/scripts/g17-separation-honesty/fixtures/honest-skip.env"
FIXTURE_216="$ROOT/scripts/g17-separation-leftover-honesty/fixtures/honest-skip.env"
README="$ROOT/docs/README.md"

SIBLING_ROWS="g17-separation-of-duties,g17-separation-honesty,g17-separation-leftover-honesty,g17-separation-leftover-honesty-remainder,g4-hot-purge-staging-pkce-remainder-honesty,g10-file-object-reconcile-remainder-honesty,s0-secret-rotate-getenv-leftover-honesty,g17-mfa-recovery-vue-otp-remainder-honesty,g5-publishhook-ddl-remainder-honesty,g1-fieldpolicy-mask-second-auth-remainder-honesty,g1-fieldpolicy-mask-second-auth-honesty,g1-fieldpolicy-mask-second-auth,g7-query-expense-travel-honesty-remainder,g7-query-expense-travel-honesty,g7-withdraw-browser-evidence-honesty,g7-withdraw-browser-honesty,g7-withdraw-browser,g7-withdraw-expense-travel,g9-redis-down-invalidate-remainder-honesty,g9-redis-down-invalidate-remainder,g9-redis-down-invalidate-honesty,g2-h5-every-page-form-write-remainder-honesty,g2-h5-every-page-form-write-honesty,g4-hot-purge-staging-leftover-honesty,g10-leftover-honesty-remainder,g3-outbox-consume-idempotent-honesty,g9-live-multi-jvm-replica-remainder-honesty,g2-report-physical-dump-honesty,g6-recovery-port-dry-run-remainder-honesty,s0-secret-rotate-getenv-remainder-honesty,g4-pkce-spa-verifier-remainder-honesty,g15-wx-dt-native-bridge-remainder,g1-queryast-export-honesty,g17-gm-tls-nginx-handshake-honesty,g5-publishhook-ddl-honesty,g9-sentinel-cluster-remainder-honesty,g2-ops-pg-dump-honesty,g17-mfa-recovery-vue-otp-honesty,g10-leftover-honesty,g5-mid-publish-failure-honesty,g3-outbox-consume-crash-honesty,g1-queryast-boundsql-honesty,g15-wx-dt-native-bridge-honesty,s0-secret-rotate-without-restart-honesty,linux-arm-profile-leftover-honesty,g6-recovery-port-dry-run-honesty,s0s1-live-redis-minio-isolation-honesty,g10-identity-drift-honesty,g15-native-bridge-honesty,s0-secret-getenv-snapshot-honesty,g5-browser-failed-publish,g4-hot-purge-90d-honesty,g4-p1-staging-audit-events-honesty,g4-export-wizard-deny-honesty,g9-sentinel-cluster-honesty"

REQUIRED_README_ROWS="g15-h5-mobile g15-f010-h5-runtime g15-miniprogram-webview g15-app-webview-host g15-h5-pkce-browser g15-wx-dt-prod-host g15-native-bridge-stub g15-native-bridge-honesty g15-wx-dt-prod-gap-honesty g15-wx-dt-native-bridge-honesty g15-wx-dt-native-bridge-remainder g7-business-types g7-withdraw-browser-honesty g7-query-expense-travel-honesty g7-withdraw-browser-evidence-honesty g7-query-expense-travel-honesty-remainder g17-separation-of-duties g17-separation-honesty g17-separation-leftover-honesty g17-separation-leftover-honesty-remainder g17-gm-tls-dual-cert g17-admin-totp-enrollment g17-totp-enrollment-ux g17-admin-mfa-recovery-email g17-mfa-recovery-email-honesty g17-vue-otp-writeback g17-mfa-recovery-vue-otp-honesty g17-mfa-recovery-vue-otp-remainder-honesty g17-gm-tls-nginx-dual-stack g17-gm-tls-nginx-handshake g17-gm-tls-nginx-handshake-honesty g2-form-submission-write g2-submit-start-form-write g2-form-write-honesty g2-h5-permission g2-h5-every-page g2-h5-every-page-form-write-honesty g2-h5-every-page-form-write-remainder-honesty g2-report-surface-guard g2-physical-dump-surface g2-report-physical-dump-honesty g1-queryast-product-optin g1-datascope-or-in-ast g1-queryast-boundsql-guard g1-queryast-boundsql-honesty g1-queryast-export-honesty g2-surfaceguard-bypass-ci g2-tenant-only-catalog g2-todo-http-surface g2-ops-pg-dump g2-ops-pg-dump-honesty g4-hot-purge-90d g4-hot-purge-90d-honesty g4-hot-purge-staging-leftover-honesty g4-hot-purge-staging-pkce-remainder-honesty g4-pkce-browser-click g4-guard-deny-audit g4-export-wizard g4-export-wizard-deny g4-export-wizard-deny-honesty g4-p1-staging-audit-events g4-p1-staging-audit-events-honesty g4-pkce-spa-verifier g4-pkce-spa-verifier-honesty g4-pkce-spa-verifier-remainder-honesty g6-recovery-port-dry-run g6-recovery-port-honesty g6-recovery-port-dry-run-honesty g6-recovery-port-dry-run-remainder-honesty g10-identity-drift-dry-run g10-file-reconcile-honesty g10-identity-drift-honesty g10-leftover-honesty g10-leftover-honesty-remainder g10-file-object-reconcile-remainder-honesty linux-arm-profile-honesty linux-arm-profile-leftover-honesty g5-mid-publish-failure g5-browser-failed-publish g5-mid-publish-failure-honesty g5-publishhook-ddl-honesty g5-publishhook-ddl-remainder-honesty g9-multinode-partition g9-sentinel-cluster-honesty g9-sentinel-cluster-remainder-honesty g9-live-multi-jvm-replica g9-live-multi-jvm-replica-remainder-honesty g9-policysnapshot-invalidate g9-failover-remainder g9-redis-down-invalidate-honesty g9-redis-down-invalidate-remainder g9-redis-down-invalidate-remainder-honesty g1-export-fieldpolicy-drop g1-fieldpolicy-mask-second-auth g1-fieldpolicy-mask-second-auth-honesty g1-fieldpolicy-mask-second-auth-remainder-honesty g3-outbox-consume-idempotent g3-claim-crash-window g3-outbox-consume-crash-honesty g3-outbox-consume-idempotent-honesty s0-secret-rotate-without-restart s0-secret-getenv-snapshot-honesty s0-secret-rotate-without-restart-honesty s0-secret-rotate-getenv-remainder-honesty s0-secret-rotate-getenv-leftover-honesty s0s1-live-redis-minio-isolation-honesty"

usage() {
  cat <<'EOF'
Usage:
  scripts/g17-separation-leftover-honesty-remainder.sh [--out DIR]
  scripts/g17-separation-leftover-honesty-remainder.sh --verify-report FILE [pass|fail]

Beyond #216/#187/#99: parent leftover harness fixtures stay PASS.
#216 harness PASS is not a live admin-force / dual-control walk.
Live IdP / admin-force / dual-control are PASS only when present AND
observed. Missing live path is honest SKIP. Does not claim product
G17 / SoD / dual-control close.

  --out DIR           Output directory (default: .local/g17-separation-leftover-honesty-remainder/<utc>)
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
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used leftover_99_cells_honest leftover_187_cells_honest leftover_216_cells_honest leftover_187_honesty_gate leftover_216_honesty_gate leftover_216_is_not_live_admin_force leftover_216_is_not_live_dual_control leftover_216_is_not_product_sod leftover_216_is_not_product_dual_control catalog_99_inventory exclusive_pairs_documented operator_not_platform_duty no_admin_force_bypass_api surfaceguard_still_required g4_deny_on_chain_unchanged same_tx_append_unchanged p1_all_demo_rewired p1_all_demo_status product_duty_ui product_duty_ui_status product_dual_control_ui product_dual_control_ui_status leftover_rows_kept leftover_rows leftover_rows_all leftover_99_kept leftover_187_kept leftover_216_kept leftover_251_kept leftover_243_kept leftover_241_kept leftover_235_kept leftover_233_kept leftover_238_kept leftover_239_kept leftover_237_kept leftover_232_kept leftover_212_kept leftover_230_kept leftover_229_kept leftover_226_kept leftover_228_kept leftover_221_kept leftover_225_kept leftover_223_kept leftover_222_kept leftover_220_kept leftover_197_kept leftover_204_kept live_jar_endpoint_set live_jar_listener live_jar_present live_idp_reachable live_idp_context_set oidc_discovery live_admin_force_walk live_admin_force_skip_reason live_admin_force_observed live_dual_control_walk live_dual_control_skip_reason live_dual_control_observed live_sod_walk dengbao_claimed mipin_claimed hsm_claimed g17_close g17_sod_close product_dual_control_close g18_status audit_port_changed p0_m0_redone redo_96 redo_99 redo_187 leftover_216_redone honesty_216_rerun honesty_187_rerun honesty_99_rerun"
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
  [ "$(report_get "$file" dengbao_claimed)" = "false" ] || { echo "FAIL: dengbao_claimed must stay false" >&2; return 1; }
  [ "$(report_get "$file" mipin_claimed)" = "false" ] || { echo "FAIL: mipin_claimed must stay false" >&2; return 1; }
  [ "$(report_get "$file" hsm_claimed)" = "false" ] || { echo "FAIL: hsm_claimed must stay false" >&2; return 1; }
  [ "$(report_get "$file" g17_close)" = "NOT_CLAIMED" ] || { echo "FAIL: g17_close must stay NOT_CLAIMED" >&2; return 1; }
  [ "$(report_get "$file" g17_sod_close)" = "NOT_CLAIMED" ] || { echo "FAIL: g17_sod_close must stay NOT_CLAIMED" >&2; return 1; }
  [ "$(report_get "$file" product_dual_control_close)" = "NOT_CLAIMED" ] || { echo "FAIL: product_dual_control_close must stay NOT_CLAIMED" >&2; return 1; }
  [ "$(report_get "$file" redo_96)" = "false" ] || { echo "FAIL: redo_96 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redo_99)" = "false" ] || { echo "FAIL: redo_99 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redo_187)" = "false" ] || { echo "FAIL: redo_187 must stay false" >&2; return 1; }
  [ "$(report_get "$file" leftover_216_redone)" = "false" ] || { echo "FAIL: leftover_216_redone must stay false" >&2; return 1; }
  [ "$(report_get "$file" honesty_216_rerun)" = "false" ] || { echo "FAIL: honesty_216_rerun must stay false" >&2; return 1; }
  [ "$(report_get "$file" honesty_187_rerun)" = "false" ] || { echo "FAIL: honesty_187_rerun must stay false" >&2; return 1; }
  [ "$(report_get "$file" honesty_99_rerun)" = "false" ] || { echo "FAIL: honesty_99_rerun must stay false" >&2; return 1; }
  [ "$(report_get "$file" leftover_99_cells_honest)" = "PASS" ] || { echo "FAIL: leftover_99_cells_honest must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_187_cells_honest)" = "PASS" ] || { echo "FAIL: leftover_187_cells_honest must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_216_cells_honest)" = "PASS" ] || { echo "FAIL: leftover_216_cells_honest must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_187_honesty_gate)" = "PASS" ] || { echo "FAIL: leftover_187_honesty_gate must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_216_honesty_gate)" = "PASS" ] || { echo "FAIL: leftover_216_honesty_gate must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_216_is_not_live_admin_force)" = "true" ] || { echo "FAIL: leftover_216_is_not_live_admin_force must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_216_is_not_live_dual_control)" = "true" ] || { echo "FAIL: leftover_216_is_not_live_dual_control must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_216_is_not_product_sod)" = "true" ] || { echo "FAIL: leftover_216_is_not_product_sod must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_216_is_not_product_dual_control)" = "true" ] || { echo "FAIL: leftover_216_is_not_product_dual_control must stay true" >&2; return 1; }
  [ "$(report_get "$file" catalog_99_inventory)" = "PASS" ] || { echo "FAIL: catalog_99_inventory must be PASS" >&2; return 1; }
  [ "$(report_get "$file" exclusive_pairs_documented)" = "true" ] || { echo "FAIL: exclusive_pairs_documented must be true" >&2; return 1; }
  [ "$(report_get "$file" operator_not_platform_duty)" = "true" ] || { echo "FAIL: operator_not_platform_duty must be true" >&2; return 1; }
  [ "$(report_get "$file" no_admin_force_bypass_api)" = "true" ] || { echo "FAIL: no_admin_force_bypass_api must be true" >&2; return 1; }
  [ "$(report_get "$file" surfaceguard_still_required)" = "PASS" ] || { echo "FAIL: surfaceguard_still_required must be PASS" >&2; return 1; }
  [ "$(report_get "$file" g4_deny_on_chain_unchanged)" = "PASS" ] || { echo "FAIL: g4_deny_on_chain_unchanged must be PASS" >&2; return 1; }
  [ "$(report_get "$file" same_tx_append_unchanged)" = "PASS" ] || { echo "FAIL: same_tx_append_unchanged must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_rows_kept)" = "true" ] || { echo "FAIL: leftover_rows_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_rows_all)" = "true" ] || { echo "FAIL: leftover_rows_all must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_99_kept)" = "true" ] || { echo "FAIL: leftover_99_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_187_kept)" = "true" ] || { echo "FAIL: leftover_187_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_216_kept)" = "true" ] || { echo "FAIL: leftover_216_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_251_kept)" = "true" ] || { echo "FAIL: leftover_251_kept must stay true (keep g4-hot-purge-staging-pkce-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_243_kept)" = "true" ] || { echo "FAIL: leftover_243_kept must stay true (keep g10-file-object-reconcile-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_241_kept)" = "true" ] || { echo "FAIL: leftover_241_kept must stay true (keep s0-secret-rotate-getenv-leftover-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_235_kept)" = "true" ] || { echo "FAIL: leftover_235_kept must stay true (keep g5-publishhook-ddl-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_233_kept)" = "true" ] || { echo "FAIL: leftover_233_kept must stay true (keep g17-mfa-recovery-vue-otp-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_238_kept)" = "true" ] || { echo "FAIL: leftover_238_kept must stay true (keep g1-fieldpolicy-mask-second-auth-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_239_kept)" = "true" ] || { echo "FAIL: leftover_239_kept must stay true (keep g7-query-expense-travel-honesty-remainder)" >&2; return 1; }
  [ "$(report_get "$file" leftover_237_kept)" = "true" ] || { echo "FAIL: leftover_237_kept must stay true (keep g9-redis-down-invalidate-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_232_kept)" = "true" ] || { echo "FAIL: leftover_232_kept must stay true (keep g2-h5-every-page-form-write-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_212_kept)" = "true" ] || { echo "FAIL: leftover_212_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_230_kept)" = "true" ] || { echo "FAIL: leftover_230_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_229_kept)" = "true" ] || { echo "FAIL: leftover_229_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_226_kept)" = "true" ] || { echo "FAIL: leftover_226_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_228_kept)" = "true" ] || { echo "FAIL: leftover_228_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_221_kept)" = "true" ] || { echo "FAIL: leftover_221_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_225_kept)" = "true" ] || { echo "FAIL: leftover_225_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_223_kept)" = "true" ] || { echo "FAIL: leftover_223_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_222_kept)" = "true" ] || { echo "FAIL: leftover_222_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_220_kept)" = "true" ] || { echo "FAIL: leftover_220_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_197_kept)" = "true" ] || { echo "FAIL: leftover_197_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_204_kept)" = "true" ] || { echo "FAIL: leftover_204_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" p1_all_demo_rewired)" = "false" ] || { echo "FAIL: p1_all_demo_rewired must stay false" >&2; return 1; }
  [ "$(report_get "$file" p1_all_demo_status)" = "OPEN" ] || { echo "FAIL: p1_all_demo_status must stay OPEN" >&2; return 1; }
  [ "$(report_get "$file" product_duty_ui)" = "false" ] || { echo "FAIL: product_duty_ui must stay false" >&2; return 1; }
  [ "$(report_get "$file" product_duty_ui_status)" = "OPEN" ] || { echo "FAIL: product_duty_ui_status must stay OPEN" >&2; return 1; }
  [ "$(report_get "$file" product_dual_control_ui)" = "false" ] || { echo "FAIL: product_dual_control_ui must stay false" >&2; return 1; }
  [ "$(report_get "$file" product_dual_control_ui_status)" = "OPEN" ] || { echo "FAIL: product_dual_control_ui_status must stay OPEN" >&2; return 1; }

  local rows
  rows="$(report_get "$file" leftover_rows)"
  local required_row
  for required_row in $(echo "$SIBLING_ROWS" | tr ',' ' '); do
    echo "$rows" | grep -Fq "$required_row" || { echo "FAIL: leftover_rows must keep $required_row" >&2; return 1; }
  done
  echo "$rows" | grep -Fq "g4-hot-purge-staging-pkce-remainder-honesty" || { echo "FAIL: leftover_rows must keep g4-hot-purge-staging-pkce-remainder-honesty" >&2; return 1; }
  echo "$rows" | grep -Fq "g10-file-object-reconcile-remainder-honesty" || { echo "FAIL: leftover_rows must keep g10-file-object-reconcile-remainder-honesty" >&2; return 1; }
  echo "$rows" | grep -Fq "s0-secret-rotate-getenv-leftover-honesty" || { echo "FAIL: leftover_rows must keep s0-secret-rotate-getenv-leftover-honesty" >&2; return 1; }
  echo "$rows" | grep -Fq "g5-publishhook-ddl-remainder-honesty" || { echo "FAIL: leftover_rows must keep g5-publishhook-ddl-remainder-honesty" >&2; return 1; }
  echo "$rows" | grep -Fq "g17-mfa-recovery-vue-otp-remainder-honesty" || { echo "FAIL: leftover_rows must keep g17-mfa-recovery-vue-otp-remainder-honesty" >&2; return 1; }
  echo "$rows" | grep -Fq "g1-fieldpolicy-mask-second-auth-remainder-honesty" || { echo "FAIL: leftover_rows must keep g1-fieldpolicy-mask-second-auth-remainder-honesty" >&2; return 1; }
  echo "$rows" | grep -Fq "g7-query-expense-travel-honesty-remainder" || { echo "FAIL: leftover_rows must keep g7-query-expense-travel-honesty-remainder" >&2; return 1; }
  echo "$rows" | grep -Fq "g9-redis-down-invalidate-remainder-honesty" || { echo "FAIL: leftover_rows must keep g9-redis-down-invalidate-remainder-honesty" >&2; return 1; }
  echo "$rows" | grep -Fq "g2-h5-every-page-form-write-remainder-honesty" || { echo "FAIL: leftover_rows must keep g2-h5-every-page-form-write-remainder-honesty" >&2; return 1; }

  local force dual
  force="$(report_get "$file" live_admin_force_walk)"
  dual="$(report_get "$file" live_dual_control_walk)"
  case "$force" in
    PASS|SKIP|FAIL) ;;
    *) echo "FAIL: live_admin_force_walk must be PASS/SKIP/FAIL" >&2; return 1 ;;
  esac
  case "$dual" in
    PASS|SKIP|FAIL) ;;
    *) echo "FAIL: live_dual_control_walk must be PASS/SKIP/FAIL" >&2; return 1 ;;
  esac
  if [ "$force" = "PASS" ]; then
    [ "$(report_get "$file" live_jar_present)" = "true" ] || { echo "FAIL: live_admin_force_walk=PASS requires live_jar_present=true" >&2; return 1; }
    [ "$(report_get "$file" live_idp_reachable)" = "true" ] || { echo "FAIL: live_admin_force_walk=PASS requires live_idp_reachable=true" >&2; return 1; }
    [ "$(report_get "$file" live_admin_force_observed)" = "true" ] || { echo "FAIL: live_admin_force_walk=PASS requires live_admin_force_observed=true" >&2; return 1; }
  fi
  if [ "$force" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_admin_force_skip_reason)" ] || { echo "FAIL: live admin force SKIP needs live_admin_force_skip_reason" >&2; return 1; }
    [ "$(report_get "$file" live_admin_force_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim live_admin_force_observed=true" >&2; return 1; }
  fi
  if [ "$dual" = "PASS" ]; then
    [ "$(report_get "$file" live_jar_present)" = "true" ] || { echo "FAIL: live_dual_control_walk=PASS requires live_jar_present=true" >&2; return 1; }
    [ "$(report_get "$file" live_idp_reachable)" = "true" ] || { echo "FAIL: live_dual_control_walk=PASS requires live_idp_reachable=true" >&2; return 1; }
    [ "$(report_get "$file" live_dual_control_observed)" = "true" ] || { echo "FAIL: live_dual_control_walk=PASS requires live_dual_control_observed=true" >&2; return 1; }
  fi
  if [ "$dual" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_dual_control_skip_reason)" ] || { echo "FAIL: live dual-control SKIP needs live_dual_control_skip_reason" >&2; return 1; }
    [ "$(report_get "$file" live_dual_control_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim live_dual_control_observed=true" >&2; return 1; }
  fi
  if grep -Eiq 'PASSWORD=|passwd=|secret=|totpSecret=|ACCESS_KEY=|TOKEN=' "$file"; then
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
  OUT="$ROOT/.local/g17-separation-leftover-honesty-remainder/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G17 leftover remainder: 三权分立 admin-force vs dual-control honesty beyond #216/#187/#99"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #96/#99/#187/#216. Not product G17 / SoD / dual-control close. G18 stays closed."
echo

[ -f "$ARTIFACT_187" ] || fail "missing committed #187 artifact $ARTIFACT_187"
[ -f "$ARTIFACT_216" ] || fail "missing committed #216 artifact $ARTIFACT_216"
[ -f "$FIXTURE_187" ] || fail "missing $FIXTURE_187"
[ -f "$FIXTURE_216" ] || fail "missing $FIXTURE_216"
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

log "verify parent #216 leftover honesty fixtures (harness PASS; do not re-run collect)"
./scripts/g17-separation-leftover-honesty.sh --verify-report \
  scripts/g17-separation-leftover-honesty/fixtures/honest-skip.env | tee -a "$OUT/collect.log"
set +e
./scripts/g17-separation-leftover-honesty.sh --verify-report \
  scripts/g17-separation-leftover-honesty/fixtures/fake-live-admin-force-pass.env fail >"$OUT/fake-216-admin-force.log" 2>&1
FAKE216_FORCE=$?
./scripts/g17-separation-leftover-honesty.sh --verify-report \
  scripts/g17-separation-leftover-honesty/fixtures/fake-live-dual-control-pass.env fail >"$OUT/fake-216-dual.log" 2>&1
FAKE216_DUAL=$?
./scripts/g17-separation-leftover-honesty.sh --verify-report \
  scripts/g17-separation-leftover-honesty/fixtures/fake-g17-close.env fail >"$OUT/fake-216-close.log" 2>&1
FAKE216_CLOSE=$?
./scripts/g17-separation-leftover-honesty.sh --verify-report \
  scripts/g17-separation-leftover-honesty/fixtures/fake-dual-control-close.env fail >"$OUT/fake-216-dual-close.log" 2>&1
FAKE216_DUAL_CLOSE=$?
./scripts/g17-separation-leftover-honesty.sh --verify-report \
  scripts/g17-separation-leftover-honesty/fixtures/fake-redo.env fail >"$OUT/fake-216-redo.log" 2>&1
FAKE216_REDO=$?
set -e
[ "$FAKE216_FORCE" -eq 0 ] || fail "expected #216 fake-live-admin-force-pass.env to be rejected"
[ "$FAKE216_DUAL" -eq 0 ] || fail "expected #216 fake-live-dual-control-pass.env to be rejected"
[ "$FAKE216_CLOSE" -eq 0 ] || fail "expected #216 fake-g17-close.env to be rejected"
[ "$FAKE216_DUAL_CLOSE" -eq 0 ] || fail "expected #216 fake-dual-control-close.env to be rejected"
[ "$FAKE216_REDO" -eq 0 ] || fail "expected #216 fake-redo.env to be rejected"
log "parent #216 leftover honesty gate PASS (honest SKIP accepted; fakes rejected)"

log "verify parent #187 leftover honesty fixtures (harness PASS; do not re-run collect)"
./scripts/g17-separation-honesty.sh --verify-report \
  scripts/g17-separation-honesty/fixtures/honest-skip.env | tee -a "$OUT/collect.log"
set +e
./scripts/g17-separation-honesty.sh --verify-report \
  scripts/g17-separation-honesty/fixtures/fake-live-pass.env >"$OUT/fake-187-live.log" 2>&1
FAKE187_LIVE=$?
./scripts/g17-separation-honesty.sh --verify-report \
  scripts/g17-separation-honesty/fixtures/fake-g17-close.env >"$OUT/fake-187-close.log" 2>&1
FAKE187_CLOSE=$?
set -e
[ "$FAKE187_LIVE" -ne 0 ] || fail "expected #187 fake-live-pass.env to FAIL"
[ "$FAKE187_CLOSE" -ne 0 ] || fail "expected #187 fake-g17-close.env to FAIL"
log "parent #187 leftover honesty gate PASS (honest SKIP accepted; fakes rejected)"

C187_CATALOG="$(report_get "$ARTIFACT_187" catalog_99_inventory)"
C187_LIVE="$(report_get "$ARTIFACT_187" live_sod_walk)"
C187_SOD="$(report_get "$ARTIFACT_187" g17_sod_close)"
C187_G17="$(report_get "$ARTIFACT_187" g17_close)"
[ "$C187_CATALOG" = "PASS" ] || fail "committed #187 catalog_99_inventory is $C187_CATALOG; must stay PASS"
[ "$C187_LIVE" = "SKIP" ] || fail "committed #187 live_sod_walk is $C187_LIVE; leftover requires honest SKIP"
[ "$C187_SOD" = "NOT_CLAIMED" ] || fail "committed #187 g17_sod_close is $C187_SOD"
[ "$C187_G17" = "NOT_CLAIMED" ] || fail "committed #187 g17_close is $C187_G17"

C216_FORCE="$(report_get "$ARTIFACT_216" live_admin_force_walk)"
C216_DUAL="$(report_get "$ARTIFACT_216" live_dual_control_walk)"
C216_SOD="$(report_get "$ARTIFACT_216" g17_sod_close)"
C216_G17="$(report_get "$ARTIFACT_216" g17_close)"
C216_DUAL_CLOSE="$(report_get "$ARTIFACT_216" product_dual_control_close)"
[ "$C216_FORCE" = "SKIP" ] || fail "committed #216 live_admin_force_walk is $C216_FORCE; leftover requires honest SKIP"
[ "$C216_DUAL" = "SKIP" ] || fail "committed #216 live_dual_control_walk is $C216_DUAL; leftover requires honest SKIP"
[ "$C216_SOD" = "NOT_CLAIMED" ] || fail "committed #216 g17_sod_close is $C216_SOD"
[ "$C216_G17" = "NOT_CLAIMED" ] || fail "committed #216 g17_close is $C216_G17"
[ "$C216_DUAL_CLOSE" = "NOT_CLAIMED" ] || fail "committed #216 product_dual_control_close is $C216_DUAL_CLOSE"

MISSING_ROWS=""
LEFTOVER_ROWS_KEPT=true
LEFTOVER_99_KEPT=false
LEFTOVER_187_KEPT=false
LEFTOVER_216_KEPT=false
LEFTOVER_251_KEPT=false
LEFTOVER_243_KEPT=false
LEFTOVER_241_KEPT=false
LEFTOVER_235_KEPT=false
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
LEFTOVER_222_KEPT=false
LEFTOVER_220_KEPT=false
LEFTOVER_197_KEPT=false
LEFTOVER_204_KEPT=false
for required_row in $REQUIRED_README_ROWS; do
  if grep -Fq "[${required_row}](./${required_row}.md)" "$README"; then
    case "$required_row" in
      g17-separation-of-duties) LEFTOVER_99_KEPT=true ;;
      g17-separation-honesty) LEFTOVER_187_KEPT=true ;;
      g17-separation-leftover-honesty) LEFTOVER_216_KEPT=true ;;
      g17-mfa-recovery-vue-otp-remainder-honesty) LEFTOVER_233_KEPT=true ;;
      g4-hot-purge-staging-pkce-remainder-honesty) LEFTOVER_251_KEPT=true ;;
      g10-file-object-reconcile-remainder-honesty) LEFTOVER_243_KEPT=true ;;
      s0-secret-rotate-getenv-leftover-honesty) LEFTOVER_241_KEPT=true ;;
      g5-publishhook-ddl-remainder-honesty) LEFTOVER_235_KEPT=true ;;
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
      g4-pkce-spa-verifier-remainder-honesty) LEFTOVER_222_KEPT=true ;;
      g15-wx-dt-native-bridge-remainder) LEFTOVER_220_KEPT=true ;;
      g9-redis-down-invalidate-remainder) LEFTOVER_197_KEPT=true ;;
      g2-h5-every-page-form-write-honesty) LEFTOVER_204_KEPT=true ;;
    esac
  else
    LEFTOVER_ROWS_KEPT=false
    MISSING_ROWS="${MISSING_ROWS} ${required_row}"
  fi
done
[ "$LEFTOVER_ROWS_KEPT" = "true" ] || fail "docs/README.md dropped leftover row(s):$MISSING_ROWS"
[ "$LEFTOVER_99_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g17-separation-of-duties (#99)"
[ "$LEFTOVER_187_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g17-separation-honesty (#187)"
[ "$LEFTOVER_216_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g17-separation-leftover-honesty (#216)"
[ "$LEFTOVER_233_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g17-mfa-recovery-vue-otp-remainder-honesty (#233)"
[ "$LEFTOVER_251_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g4-hot-purge-staging-pkce-remainder-honesty (#251)"
[ "$LEFTOVER_243_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g10-file-object-reconcile-remainder-honesty (#243)"
[ "$LEFTOVER_241_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover s0-secret-rotate-getenv-leftover-honesty (#241)"
[ "$LEFTOVER_235_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g5-publishhook-ddl-remainder-honesty (#235)"
[ "$LEFTOVER_238_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g1-fieldpolicy-mask-second-auth-remainder-honesty (#238)"
[ "$LEFTOVER_239_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g7-query-expense-travel-honesty-remainder (#239)"
[ "$LEFTOVER_237_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g9-redis-down-invalidate-remainder-honesty (#237)"
[ "$LEFTOVER_232_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g2-h5-every-page-form-write-remainder-honesty (#232)"
if grep -Eiq '产品 G17 已关闭|product g17 closed|g17_close=CLOSED|g17_sod_close=CLOSED' "$README"; then
  fail "docs/README.md must not claim product G17 / SoD closed"
fi

eval "$(python3 - "$ROOT" <<'PY'
import pathlib, sys
root = pathlib.Path(sys.argv[1])
catalog = (root / "nexus-permission-core/src/main/java/one/nexus/permission/admin/PlatformDutyCatalog.java").read_text(encoding="utf-8")
admin = (root / "nexus-permission-core/src/main/java/one/nexus/permission/admin/PolicyAdminService.java").read_text(encoding="utf-8")
engine = (root / "nexus-permission-core/src/main/java/one/nexus/permission/engine/PermissionEngine.java").read_text(encoding="utf-8")
guard = (root / "nexus-permission-core/src/main/java/one/nexus/permission/surface/SurfaceGuard.java").read_text(encoding="utf-8")
actor = (root / "nexus-p1-tenant/src/main/java/one/nexus/p1/domain/Actor.java").read_text(encoding="utf-8")
demo = (root / "nexus-p1-tenant/src/main/java/one/nexus/p1/infra/isolation/DemoPlatformContextFilter.java").read_text(encoding="utf-8")
needed = (
    'SYSTEM_ADMIN = "platform_system_admin"' in catalog
    and 'SECURITY_ADMIN = "platform_security_admin"' in catalog
    and 'AUDIT_ADMIN = "platform_audit_admin"' in catalog
    and "exclusivePairs()" in catalog
    and "duty_conflict" in admin
    and "duty_conflict" in engine
)
print("CATALOG_OK=%s" % ("true" if needed else "false"))
print("OPERATOR_NOT_DUTY=%s" % ("true" if "tenant_member remains demo bootstrap and is not a platform duty" in catalog else "false"))
print("SURFACEGUARD_OK=%s" % ("true" if "appendNewTransaction" in guard and "PermissionEngine" in guard else "false"))
print("G4_DENY_OK=%s" % ("true" if "appendNewTransaction(AuditCaptures.permission(" in guard and "appendNewTransaction(AuditCaptures.guardReject(" in guard else "false"))
print("SAME_TX_OK=%s" % ("true" if "same-TX" in guard or "appendNewTransaction" in guard else "false"))
all_demo = "duties.contains(PlatformDuty.ALL_DEMO)" in actor
demo_stamp = "Set.of(PlatformDuty.ALL_DEMO)" in demo
print("P1_ALL_DEMO_STILL=%s" % ("true" if all_demo and demo_stamp else "false"))
needles = (
    "forceApprove", "force_approve", "forceOverride", "force_override",
    "dualControl", "dual_control", "makerChecker", "maker_checker",
)
skip_parts = ("honesty", "leftover", "EVIDENCE", "fixtures")
hits = []
for base in (
    root / "nexus-permission-core/src/main",
    root / "nexus-bootstrap/src/main",
    root / "web/src",
):
    if not base.exists():
        continue
    for path in base.rglob("*"):
        if path.suffix.lower() not in {".java", ".vue", ".js", ".ts"}:
            continue
        text = path.read_text(encoding="utf-8", errors="ignore")
        if any(n in text for n in needles):
            rel = str(path.relative_to(root))
            if not any(p in rel for p in skip_parts):
                hits.append(rel)
print("ADMIN_FORCE_BYPASS=%s" % ("true" if hits else "false"))
ui_hits = []
web = root / "web/src"
if web.is_dir():
    for path in web.rglob("*"):
        if path.suffix.lower() not in {".vue", ".js", ".ts"}:
            continue
        text = path.read_text(encoding="utf-8", errors="ignore")
        if "platform_system_admin" in text and ("assign" in text.lower() or "duties/example" in text):
            ui_hits.append(str(path))
        lowered = text.lower()
        if ("dual-control" in lowered or "maker-checker" in lowered or "双人复核" in text) and "honesty" not in str(path):
            ui_hits.append(str(path))
print("PRODUCT_DUTY_UI=%s" % ("true" if ui_hits else "false"))
print("PRODUCT_DUAL_CONTROL_UI=%s" % ("true" if ui_hits else "false"))
PY
)"
[ "$CATALOG_OK" = "true" ] || fail "#99 PlatformDutyCatalog / assign / evaluate inventory missing"
[ "$OPERATOR_NOT_DUTY" = "true" ] || fail "tenant_member must stay documented as not a platform duty"
[ "$SURFACEGUARD_OK" = "true" ] || fail "G2 SurfaceGuard must still be required (admin does not bypass)"
[ "$G4_DENY_OK" = "true" ] || fail "G4 deny-on-chain appendNewTransaction pair must stay"
[ "$SAME_TX_OK" = "true" ] || fail "AuditPort same-TX appendNewTransaction must stay untouched"
[ "$P1_ALL_DEMO_STILL" = "true" ] || fail "P1 ALL_DEMO bypass must still be present (remaining SoD path)"
[ "$ADMIN_FORCE_BYPASS" = "false" ] || fail "product admin-force / dual-control bypass API appeared; leftover must stay honest"
[ "$PRODUCT_DUTY_UI" = "false" ] || fail "product duty-assign / dual-control UI appeared; leftover must stay honest"
[ "$PRODUCT_DUAL_CONTROL_UI" = "false" ] || fail "product dual-control UI appeared; leftover must stay honest"

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

LIVE_ENDPOINT="${NEXUS_G17_SOD_LIVE_BASE:-${NEXUS_BASE_URL:-}}"
LIVE_ENDPOINT_SET=false
LIVE_CONTEXT_SET=false
if [ -n "$LIVE_ENDPOINT" ]; then
  LIVE_ENDPOINT_SET=true
fi
if [ -n "${NEXUS_G17_SOD_LIVE_CONTEXT_ID:-}" ]; then
  LIVE_CONTEXT_SET=true
fi

LIVE_LISTENER=false
if port_open 127.0.0.1 8080 || port_open 127.0.0.1 8088; then
  LIVE_LISTENER=true
fi

KEYCLOAK_ISSUER="${NEXUS_KEYCLOAK_ISSUER:-}"
KC_REACHABLE=false
OIDC_DISCOVERY=SKIP
if [[ -z "$KEYCLOAK_ISSUER" ]]; then
  for candidate in http://127.0.0.1:8080/realms/nexus http://127.0.0.1:8180/realms/nexus; do
    if curl -sf --max-time 2 "${candidate}/.well-known/openid-configuration" >/dev/null 2>&1; then
      KEYCLOAK_ISSUER="$candidate"
      KC_REACHABLE=true
      OIDC_DISCOVERY=PASS
      break
    fi
  done
elif curl -sf --max-time 2 "${KEYCLOAK_ISSUER}/.well-known/openid-configuration" >/dev/null 2>&1; then
  KC_REACHABLE=true
  OIDC_DISCOVERY=PASS
fi

LIVE_JAR_PRESENT=false
if [ "$LIVE_ENDPOINT_SET" = "true" ] && [ "$LIVE_CONTEXT_SET" = "true" ] && [ "$LIVE_LISTENER" = "true" ]; then
  LIVE_JAR_PRESENT=true
fi

if [ "$LIVE_ENDPOINT_SET" != "true" ] && [ "$LIVE_LISTENER" != "true" ] && [ "$KC_REACHABLE" != "true" ]; then
  FORCE_REASON="live JAR unset (NEXUS_BASE_URL / NEXUS_G17_SOD_LIVE_BASE); nothing on 127.0.0.1:8080 / :8088; live IdP unreachable; #216 harness PASS is not a live admin-force walk; Docker forbidden; this leftover does not start the JAR and does not re-run #99/#187/#216"
  DUAL_REASON="live JAR unset; live IdP unreachable; product dual-control (maker-checker) walk not observed; #216 harness PASS is not a live dual-control walk; Docker forbidden"
elif [ "$KC_REACHABLE" != "true" ]; then
  FORCE_REASON="live IdP unreachable (NEXUS_KEYCLOAK_ISSUER / loopback :8080/:8180); admin force HTTP SKIP; #216 harness PASS is not a live walk"
  DUAL_REASON="live IdP unreachable; dual-control (maker + checker) walk SKIP; #216 harness PASS is not a live walk"
elif [ "$LIVE_CONTEXT_SET" != "true" ]; then
  FORCE_REASON="issued SoD context unset (NEXUS_G17_SOD_LIVE_CONTEXT_ID); live admin force walk SKIP"
  DUAL_REASON="issued SoD context unset; live dual-control walk SKIP"
elif [ "$LIVE_LISTENER" != "true" ]; then
  FORCE_REASON="live IdP reachable but product JAR not listening; live admin force walk SKIP"
  DUAL_REASON="live IdP reachable but product JAR not listening; live dual-control walk SKIP"
else
  FORCE_REASON="live listener/IdP present but admin force HTTP walk was not observed; #216 harness PASS is not a live walk; this leftover does not fabricate PASS"
  DUAL_REASON="live listener/IdP present but maker+checker dual-control walk was not observed; #216 harness PASS is not a live walk; this leftover does not fabricate PASS"
fi

LEFTOVER_ROWS_CSV="$SIBLING_ROWS"
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
  kv leftover_99_cells_honest PASS
  kv leftover_187_cells_honest PASS
  kv leftover_216_cells_honest PASS
  kv leftover_187_honesty_gate PASS
  kv leftover_216_honesty_gate PASS
  kv leftover_216_is_not_live_admin_force true
  kv leftover_216_is_not_live_dual_control true
  kv leftover_216_is_not_product_sod true
  kv leftover_216_is_not_product_dual_control true
  kv catalog_99_inventory PASS
  kv exclusive_pairs_documented true
  kv operator_not_platform_duty "$OPERATOR_NOT_DUTY"
  kv no_admin_force_bypass_api true
  kv surfaceguard_still_required PASS
  kv g4_deny_on_chain_unchanged PASS
  kv same_tx_append_unchanged PASS
  kv p1_all_demo_rewired false
  kv p1_all_demo_status OPEN
  kv product_duty_ui false
  kv product_duty_ui_status OPEN
  kv product_dual_control_ui false
  kv product_dual_control_ui_status OPEN
  kv leftover_rows_kept "$LEFTOVER_ROWS_KEPT"
  kv leftover_rows "$LEFTOVER_ROWS_CSV"
  kv leftover_rows_all true
  kv leftover_99_kept "$LEFTOVER_99_KEPT"
  kv leftover_187_kept "$LEFTOVER_187_KEPT"
  kv leftover_216_kept "$LEFTOVER_216_KEPT"
  kv leftover_251_kept "$LEFTOVER_251_KEPT"
  kv leftover_243_kept "$LEFTOVER_243_KEPT"
  kv leftover_241_kept "$LEFTOVER_241_KEPT"
  kv leftover_235_kept "$LEFTOVER_235_KEPT"
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
  kv leftover_222_kept "$LEFTOVER_222_KEPT"
  kv leftover_220_kept "$LEFTOVER_220_KEPT"
  kv leftover_197_kept "$LEFTOVER_197_KEPT"
  kv leftover_204_kept "$LEFTOVER_204_KEPT"
  kv live_jar_endpoint_set "$LIVE_ENDPOINT_SET"
  kv live_jar_listener "$LIVE_LISTENER"
  kv live_jar_present "$LIVE_JAR_PRESENT"
  kv live_idp_reachable "$KC_REACHABLE"
  kv live_idp_context_set "$LIVE_CONTEXT_SET"
  kv oidc_discovery "$OIDC_DISCOVERY"
  kv probed_issuer "${KEYCLOAK_ISSUER:-}"
  kv live_admin_force_walk SKIP
  kv live_admin_force_skip_reason "$FORCE_REASON"
  kv live_admin_force_observed false
  kv live_dual_control_walk SKIP
  kv live_dual_control_skip_reason "$DUAL_REASON"
  kv live_dual_control_observed false
  kv live_sod_walk SKIP
  kv dengbao_claimed false
  kv mipin_claimed false
  kv hsm_claimed false
  kv g17_close NOT_CLAIMED
  kv g17_sod_close NOT_CLAIMED
  kv product_dual_control_close NOT_CLAIMED
  kv g17_close_reason "Honesty leftover remainder beyond #216/#187/#99 after #251/#243/#241/#235/#233/#238/#239. Parent harness fixtures PASS. #216 harness PASS is not a live admin-force / dual-control walk. Live admin-force / dual-control SKIP when JAR/IdP unavailable. Not product G17 / SoD / dual-control / 等保 / 密评."
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv p0_m0_redone false
  kv redo_96 false
  kv redo_99 false
  kv redo_187 false
  kv leftover_216_redone false
  kv honesty_216_rerun false
  kv honesty_187_rerun false
  kv honesty_99_rerun false
} >"$REPORT"

MATRIX="$OUT/matrix.md"
{
  echo "# G17 leftover remainder — 三权分立 admin-force vs dual-control beyond #216/#187/#99"
  echo
  echo "| Check | Result |"
  echo "|---|---|"
  echo "| leftover #99 cells still honest | PASS |"
  echo "| leftover #187 cells still honest | PASS |"
  echo "| leftover #216 cells still honest | PASS |"
  echo "| #187 leftover honesty fixtures | PASS |"
  echo "| #216 leftover honesty fixtures | PASS |"
  echo "| #216 harness is live admin-force | false |"
  echo "| #216 harness is live dual-control | false |"
  echo "| live admin-force | SKIP |"
  echo "| live dual-control | SKIP |"
  echo "| leftover rows kept | $LEFTOVER_ROWS_CSV |"
  echo "| #96/#99/#187/#216 redo | false |"
  echo "| product G17 / SoD / dual-control close | NOT_CLAIMED |"
  echo
  echo "Admin-force reason: $FORCE_REASON"
  echo "Dual-control reason: $DUAL_REASON"
} >"$MATRIX"

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
    "leftover": "g17-separation-leftover-honesty-remainder",
    "siblings": [
        "g17-separation-of-duties",
        "g17-separation-honesty",
        "g17-separation-leftover-honesty",
        "g4-hot-purge-staging-pkce-remainder-honesty",
        "g10-file-object-reconcile-remainder-honesty",
        "g17-mfa-recovery-vue-otp-remainder-honesty",
        "s0-secret-rotate-getenv-leftover-honesty",
        "g5-publishhook-ddl-remainder-honesty",
        "g1-fieldpolicy-mask-second-auth-remainder-honesty",
        "g7-query-expense-travel-honesty-remainder",
        "g9-redis-down-invalidate-remainder-honesty",
        "g2-h5-every-page-form-write-remainder-honesty",
    ],
    "catalog_99_inventory": vals.get("catalog_99_inventory"),
    "leftover_216_honesty_gate": vals.get("leftover_216_honesty_gate"),
    "leftover_216_is_not_live_admin_force": vals.get("leftover_216_is_not_live_admin_force"),
    "leftover_216_is_not_live_dual_control": vals.get("leftover_216_is_not_live_dual_control"),
    "no_admin_force_bypass_api": vals.get("no_admin_force_bypass_api"),
    "live_admin_force_walk": vals.get("live_admin_force_walk"),
    "live_dual_control_walk": vals.get("live_dual_control_walk"),
    "product_dual_control_ui_status": vals.get("product_dual_control_ui_status"),
    "g17Close": "NOT_CLAIMED",
    "g17SodClose": "NOT_CLAIMED",
    "productDualControlClose": "NOT_CLAIMED",
    "g18": "closed_unchanged",
    "timestampUtc": vals.get("timestamp_utc"),
    "evidence": "scripts/g17-separation-leftover-honesty-remainder.sh",
}
open(out, "w", encoding="utf-8").write(json.dumps(walk, indent=2) + "\n")
print("wrote", out)
PY

verify_report "$REPORT"

echo "== Honesty fixtures"
verify_report "$HERE/g17-separation-leftover-honesty-remainder/fixtures/honest-skip.env"
verify_report "$HERE/g17-separation-leftover-honesty-remainder/fixtures/fake-live-admin-force-pass.env" fail
verify_report "$HERE/g17-separation-leftover-honesty-remainder/fixtures/fake-live-dual-control-pass.env" fail
verify_report "$HERE/g17-separation-leftover-honesty-remainder/fixtures/fake-g17-close.env" fail
verify_report "$HERE/g17-separation-leftover-honesty-remainder/fixtures/fake-dual-control-close.env" fail
verify_report "$HERE/g17-separation-leftover-honesty-remainder/fixtures/fake-redo.env" fail
verify_report "$HERE/g17-separation-leftover-honesty-remainder/fixtures/fake-harness-as-live.env" fail

SANITIZED="${ROOT}/docs/engineering/g17-separation-leftover-honesty-remainder/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

log "DONE report=$REPORT live_admin_force=SKIP live_dual_control=SKIP g17_close=NOT_CLAIMED"
echo
echo "PASS: leftover remainder honesty recorded (#99/#187/#216 cells honest; admin force SKIP; dual-control SKIP)"
echo "See docs/g17-separation-leftover-honesty-remainder.md"
echo "Keep sibling leftovers: docs/g17-separation-leftover-honesty.md, docs/g17-separation-honesty.md, docs/g17-separation-of-duties.md"
echo "Keep #251 g4-hot-purge-staging-pkce-remainder-honesty, #243 g10-file-object-reconcile-remainder-honesty, #241 s0-secret-rotate-getenv-leftover-honesty, #235 g5-publishhook-ddl-remainder-honesty, #233 g17-mfa-recovery-vue-otp-remainder-honesty, #238 g1-fieldpolicy-mask-second-auth-remainder-honesty, #239 g7-query-expense-travel-honesty-remainder, #237 g9-redis-down-invalidate-remainder-honesty, #232 g2-h5-every-page-form-write-remainder-honesty"
echo "Not claimed: product G17 / SoD / dual-control close / 等保 / 密评"
echo "Not redone: #96/#99/#187/#216"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
echo "walk=$JSON_OUT"
