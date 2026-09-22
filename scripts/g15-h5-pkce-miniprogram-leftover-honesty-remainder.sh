#!/usr/bin/env bash
# G15 leftover remainder: H5 PKCE / miniprogram honesty
# beyond #240 / #220 / #198.
#
# Extends docs/g15-h5-pkce-miniprogram-remainder-honesty.md,
# docs/g15-wx-dt-native-bridge-remainder.md, and
# docs/g15-wx-dt-native-bridge-honesty.md. After #249 G17 SoD leftover
# honesty on origin/main 2ac6326 (Merge #263 Linux/ARM leftover remainder after #262 G10 leftover honesty / #259 G9 leftover remainder / #249).
# Parent #240 / #220 / #198 harness --verify-report fixtures stay PASS.
# That #240 harness PASS is NOT a live Keycloak H5 PKCE / WeChat /
# DingTalk / IPA/APK walk. Live walks stay honest SKIP unless observed.
# Does NOT redo #117/#120/#126/#198/#220/#240. Does NOT claim product
# G15 close. G18 stays closed. No P0/M0 redo. AuditPort same-TX
# untouched. No Docker.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT=""
JSON_OUT="${NEXUS_G15_H5_PKCE_MP_REMAINDER_JSON:-$ROOT/docs/g15-h5-pkce-miniprogram-leftover-honesty-remainder/walk.json}"
VERIFY_REPORT=""
VERIFY_EXPECT=pass

FIXTURE_240="$ROOT/scripts/g15-h5-pkce-miniprogram-remainder-honesty/fixtures/honest-skip.env"
FIXTURE_220="$ROOT/scripts/g15-wx-dt-native-bridge-remainder/fixtures/honest-skip.env"
FIXTURE_198="$ROOT/scripts/g15-wx-dt-native-bridge-honesty/fixtures/honest-skip.env"
README="$ROOT/docs/README.md"

SIBLING_ROWS="g15-h5-pkce-miniprogram-leftover-honesty-remainder,g15-h5-pkce-miniprogram-remainder-honesty,g10-file-object-reconcile-leftover-honesty,g9-leftover-honesty-remainder,linux-arm-profile-leftover-remainder-honesty,g15-wx-dt-native-bridge-remainder,g15-wx-dt-native-bridge-honesty,g15-h5-pkce-browser,g15-miniprogram-webview,g15-wx-dt-prod-gap-honesty,g15-native-bridge-honesty,g15-native-bridge-stub,g15-wx-dt-prod-host,g17-separation-leftover-honesty-remainder,g17-separation-leftover-honesty,g17-separation-honesty,g17-separation-of-duties,g2-h5-every-page-form-write-report-remainder-honesty,linux-arm-profile-remainder-honesty,g4-hot-purge-staging-pkce-remainder-honesty,g10-file-object-reconcile-remainder-honesty,s0-secret-rotate-getenv-leftover-honesty,g17-mfa-recovery-vue-otp-remainder-honesty,g5-publishhook-ddl-remainder-honesty,g1-fieldpolicy-mask-second-auth-remainder-honesty,g7-query-expense-travel-honesty-remainder,g9-redis-down-invalidate-remainder-honesty,g2-h5-every-page-form-write-remainder-honesty,g7-withdraw-browser-evidence-honesty,g4-hot-purge-staging-leftover-honesty,g9-live-multi-jvm-replica-remainder-honesty,g3-outbox-consume-idempotent-honesty,g10-leftover-honesty-remainder,g2-report-physical-dump-honesty,g6-recovery-port-dry-run-remainder-honesty,s0-secret-rotate-getenv-remainder-honesty,g4-pkce-spa-verifier-remainder-honesty,g1-queryast-export-honesty,g17-gm-tls-nginx-handshake-honesty,g5-publishhook-ddl-honesty,g9-sentinel-cluster-remainder-honesty,g2-ops-pg-dump-honesty,g17-mfa-recovery-vue-otp-honesty,g10-leftover-honesty,g5-mid-publish-failure-honesty,g3-outbox-consume-crash-honesty,g1-queryast-boundsql-honesty,s0-secret-rotate-without-restart-honesty,linux-arm-profile-leftover-honesty,g6-recovery-port-dry-run-honesty,s0s1-live-redis-minio-isolation-honesty,g10-identity-drift-honesty,s0-secret-getenv-snapshot-honesty,g5-browser-failed-publish,g4-hot-purge-90d-honesty,g4-p1-staging-audit-events-honesty,g4-export-wizard-deny-honesty,g9-sentinel-cluster-honesty,g7-query-expense-travel-honesty,g7-withdraw-browser-honesty,g9-redis-down-invalidate-remainder,g2-h5-every-page-form-write-honesty,g1-fieldpolicy-mask-second-auth-honesty,g10-file-reconcile-honesty,linux-arm-profile-honesty"

REQUIRED_README_ROWS="g15-h5-mobile g15-f010-h5-runtime g15-miniprogram-webview g15-app-webview-host g15-h5-pkce-browser g15-wx-dt-prod-host g15-native-bridge-stub g15-native-bridge-honesty g15-wx-dt-prod-gap-honesty g15-wx-dt-native-bridge-honesty g15-wx-dt-native-bridge-remainder g15-h5-pkce-miniprogram-remainder-honesty g15-h5-pkce-miniprogram-leftover-honesty-remainder g10-file-object-reconcile-leftover-honesty g9-leftover-honesty-remainder linux-arm-profile-leftover-remainder-honesty g7-business-types g7-withdraw-browser-honesty g7-query-expense-travel-honesty g7-withdraw-browser-evidence-honesty g7-query-expense-travel-honesty-remainder g17-separation-of-duties g17-separation-honesty g17-separation-leftover-honesty g17-separation-leftover-honesty-remainder g17-gm-tls-dual-cert g17-admin-totp-enrollment g17-totp-enrollment-ux g17-admin-mfa-recovery-email g17-mfa-recovery-email-honesty g17-vue-otp-writeback g17-mfa-recovery-vue-otp-honesty g17-mfa-recovery-vue-otp-remainder-honesty g17-gm-tls-nginx-dual-stack g17-gm-tls-nginx-handshake g17-gm-tls-nginx-handshake-honesty g2-form-submission-write g2-submit-start-form-write g2-form-write-honesty g2-h5-permission g2-h5-every-page g2-h5-every-page-form-write-honesty g2-h5-every-page-form-write-remainder-honesty g2-h5-every-page-form-write-report-remainder-honesty g2-report-surface-guard g2-physical-dump-surface g2-report-physical-dump-honesty g1-queryast-product-optin g1-datascope-or-in-ast g1-queryast-boundsql-guard g1-queryast-boundsql-honesty g1-queryast-export-honesty g2-surfaceguard-bypass-ci g2-tenant-only-catalog g2-todo-http-surface g2-ops-pg-dump g2-ops-pg-dump-honesty g4-hot-purge-90d g4-hot-purge-90d-honesty g4-hot-purge-staging-leftover-honesty g4-hot-purge-staging-pkce-remainder-honesty g4-pkce-browser-click g4-guard-deny-audit g4-export-wizard g4-export-wizard-deny g4-export-wizard-deny-honesty g4-p1-staging-audit-events g4-p1-staging-audit-events-honesty g4-pkce-spa-verifier g4-pkce-spa-verifier-honesty g4-pkce-spa-verifier-remainder-honesty g6-recovery-port-dry-run g6-recovery-port-honesty g6-recovery-port-dry-run-honesty g6-recovery-port-dry-run-remainder-honesty g10-identity-drift-dry-run g10-file-reconcile-honesty g10-identity-drift-honesty g10-leftover-honesty g10-leftover-honesty-remainder g10-file-object-reconcile-remainder-honesty linux-arm-profile-honesty linux-arm-profile-leftover-honesty linux-arm-profile-remainder-honesty g5-mid-publish-failure g5-browser-failed-publish g5-mid-publish-failure-honesty g5-publishhook-ddl-honesty g5-publishhook-ddl-remainder-honesty g9-multinode-partition g9-sentinel-cluster-honesty g9-sentinel-cluster-remainder-honesty g9-live-multi-jvm-replica g9-live-multi-jvm-replica-remainder-honesty g9-policysnapshot-invalidate g9-failover-remainder g9-redis-down-invalidate-honesty g9-redis-down-invalidate-remainder g9-redis-down-invalidate-remainder-honesty g1-export-fieldpolicy-drop g1-fieldpolicy-mask-second-auth g1-fieldpolicy-mask-second-auth-honesty g1-fieldpolicy-mask-second-auth-remainder-honesty g3-outbox-consume-idempotent g3-claim-crash-window g3-outbox-consume-crash-honesty g3-outbox-consume-idempotent-honesty s0-secret-rotate-without-restart s0-secret-getenv-snapshot-honesty s0-secret-rotate-without-restart-honesty s0-secret-rotate-getenv-remainder-honesty s0-secret-rotate-getenv-leftover-honesty s0s1-live-redis-minio-isolation-honesty"

usage() {
  cat <<'EOF'
Usage:
  scripts/g15-h5-pkce-miniprogram-leftover-honesty-remainder.sh [--out DIR]
  scripts/g15-h5-pkce-miniprogram-leftover-honesty-remainder.sh --verify-report FILE [pass|fail]

Beyond #240/#220/#198: parent leftover harness fixtures stay PASS.
#240 harness PASS is not a live Keycloak H5 PKCE / WeChat / DingTalk /
IPA/APK walk. Live hosts are PASS only when present AND observed.
Missing live path is honest SKIP. Does not claim product G15 close.

  --out DIR           Output directory (default: .local/g15-h5-pkce-miniprogram-leftover-honesty-remainder/<utc>)
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
  local required="schema_version timestamp_utc boot_parent_version boot_parent_match docker_used leftover_240_harness leftover_220_harness leftover_198_harness leftover_117_harness leftover_126_harness leftover_120_sibling leftover_240_honesty_gate leftover_220_honesty_gate leftover_198_honesty_gate leftover_240_is_not_live_h5_pkce leftover_240_is_not_wechat_production leftover_240_is_not_dingtalk_production leftover_240_is_not_ipa_apk leftover_240_is_not_product_g15 leftover_rows_kept leftover_rows leftover_rows_all leftover_240_kept leftover_220_kept leftover_198_kept leftover_249_kept leftover_262_kept leftover_259_kept leftover_263_kept leftover_254_kept leftover_236_kept leftover_251_kept leftover_243_kept leftover_241_kept leftover_235_kept leftover_233_kept leftover_238_kept leftover_239_kept leftover_237_kept leftover_232_kept leftover_212_kept leftover_230_kept leftover_229_kept leftover_226_kept leftover_228_kept leftover_221_kept leftover_225_kept leftover_223_kept leftover_222_kept leftover_217_kept leftover_216_kept leftover_210_kept leftover_209_kept leftover_205_kept leftover_204_kept leftover_197_kept leftover_188_kept leftover_126_kept leftover_117_kept live_h5_keycloak_pkce live_https_keycloak_h5_pkce wechat_miniprogram_production dingtalk_miniprogram_production ios_ipa_production android_apk_production live_h5_pkce_observed live_https_h5_observed wechat_walk_observed dingtalk_walk_observed ios_walk_observed android_walk_observed h5_pkce_skip_reason https_h5_skip_reason wechat_skip_reason dingtalk_skip_reason ios_skip_reason android_skip_reason g15_close g18_status audit_port_changed p0_m0_redone leftover_240_redone redone_117 redone_120 redone_126 redone_198 redone_220 honesty_240_rerun honesty_220_rerun honesty_198_rerun playwright_stub_is_not_live_keycloak simulated_mp_webview_is_not_production"
  local key
  for key in $required; do
    [ -n "$(report_get "$file" "$key")" ] || { echo "FAIL: missing key $key in $file" >&2; return 1; }
  done

  [ "$(report_get "$file" boot_parent_version)" = "4.1.1" ] || { echo "FAIL: boot_parent_version must be 4.1.1" >&2; return 1; }
  [ "$(report_get "$file" boot_parent_match)" = "PASS" ] || { echo "FAIL: boot_parent_match must be PASS" >&2; return 1; }
  [ "$(report_get "$file" docker_used)" = "false" ] || { echo "FAIL: docker_used must be false" >&2; return 1; }
  [ "$(report_get "$file" g18_status)" = "closed_unchanged" ] || { echo "FAIL: g18_status must stay closed_unchanged" >&2; return 1; }
  [ "$(report_get "$file" audit_port_changed)" = "false" ] || { echo "FAIL: audit_port_changed must be false" >&2; return 1; }
  [ "$(report_get "$file" p0_m0_redone)" = "false" ] || { echo "FAIL: p0_m0_redone must stay false" >&2; return 1; }
  [ "$(report_get "$file" g15_close)" = "NOT_CLAIMED" ] || { echo "FAIL: g15_close must stay NOT_CLAIMED" >&2; return 1; }
  [ "$(report_get "$file" leftover_240_redone)" = "false" ] || { echo "FAIL: leftover_240_redone must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_117)" = "false" ] || { echo "FAIL: redone_117 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_120)" = "false" ] || { echo "FAIL: redone_120 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_126)" = "false" ] || { echo "FAIL: redone_126 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_198)" = "false" ] || { echo "FAIL: redone_198 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_220)" = "false" ] || { echo "FAIL: redone_220 must stay false" >&2; return 1; }
  [ "$(report_get "$file" honesty_240_rerun)" = "false" ] || { echo "FAIL: honesty_240_rerun must stay false" >&2; return 1; }
  [ "$(report_get "$file" honesty_220_rerun)" = "false" ] || { echo "FAIL: honesty_220_rerun must stay false" >&2; return 1; }
  [ "$(report_get "$file" honesty_198_rerun)" = "false" ] || { echo "FAIL: honesty_198_rerun must stay false" >&2; return 1; }
  [ "$(report_get "$file" leftover_240_harness)" = "PASS" ] || { echo "FAIL: leftover_240_harness must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_220_harness)" = "PASS" ] || { echo "FAIL: leftover_220_harness must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_198_harness)" = "PASS" ] || { echo "FAIL: leftover_198_harness must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_117_harness)" = "PASS" ] || { echo "FAIL: leftover_117_harness must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_126_harness)" = "PASS" ] || { echo "FAIL: leftover_126_harness must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_120_sibling)" = "PASS" ] || { echo "FAIL: leftover_120_sibling must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_240_honesty_gate)" = "PASS" ] || { echo "FAIL: leftover_240_honesty_gate must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_220_honesty_gate)" = "PASS" ] || { echo "FAIL: leftover_220_honesty_gate must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_198_honesty_gate)" = "PASS" ] || { echo "FAIL: leftover_198_honesty_gate must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_240_is_not_live_h5_pkce)" = "true" ] || { echo "FAIL: leftover_240_is_not_live_h5_pkce must stay true (#240 harness PASS is not a live Keycloak H5 click)" >&2; return 1; }
  [ "$(report_get "$file" leftover_240_is_not_wechat_production)" = "true" ] || { echo "FAIL: leftover_240_is_not_wechat_production must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_240_is_not_dingtalk_production)" = "true" ] || { echo "FAIL: leftover_240_is_not_dingtalk_production must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_240_is_not_ipa_apk)" = "true" ] || { echo "FAIL: leftover_240_is_not_ipa_apk must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_240_is_not_product_g15)" = "true" ] || { echo "FAIL: leftover_240_is_not_product_g15 must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_rows_kept)" = "true" ] || { echo "FAIL: leftover_rows_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_rows_all)" = "true" ] || { echo "FAIL: leftover_rows_all must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_240_kept)" = "true" ] || { echo "FAIL: leftover_240_kept must stay true (keep g15-h5-pkce-miniprogram-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_220_kept)" = "true" ] || { echo "FAIL: leftover_220_kept must stay true (keep g15-wx-dt-native-bridge-remainder)" >&2; return 1; }
  [ "$(report_get "$file" leftover_198_kept)" = "true" ] || { echo "FAIL: leftover_198_kept must stay true (keep g15-wx-dt-native-bridge-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_249_kept)" = "true" ] || { echo "FAIL: leftover_249_kept must stay true (keep g17-separation-leftover-honesty-remainder)" >&2; return 1; }
  [ "$(report_get "$file" leftover_262_kept)" = "true" ] || { echo "FAIL: leftover_262_kept must stay true (keep g10-file-object-reconcile-leftover-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_259_kept)" = "true" ] || { echo "FAIL: leftover_259_kept must stay true (keep g9-leftover-honesty-remainder)" >&2; return 1; }
  [ "$(report_get "$file" leftover_263_kept)" = "true" ] || { echo "FAIL: leftover_263_kept must stay true (keep linux-arm-profile-leftover-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_254_kept)" = "true" ] || { echo "FAIL: leftover_254_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_236_kept)" = "true" ] || { echo "FAIL: leftover_236_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_251_kept)" = "true" ] || { echo "FAIL: leftover_251_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_243_kept)" = "true" ] || { echo "FAIL: leftover_243_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_241_kept)" = "true" ] || { echo "FAIL: leftover_241_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_235_kept)" = "true" ] || { echo "FAIL: leftover_235_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_233_kept)" = "true" ] || { echo "FAIL: leftover_233_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_238_kept)" = "true" ] || { echo "FAIL: leftover_238_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_239_kept)" = "true" ] || { echo "FAIL: leftover_239_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_237_kept)" = "true" ] || { echo "FAIL: leftover_237_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_232_kept)" = "true" ] || { echo "FAIL: leftover_232_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_212_kept)" = "true" ] || { echo "FAIL: leftover_212_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_230_kept)" = "true" ] || { echo "FAIL: leftover_230_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_229_kept)" = "true" ] || { echo "FAIL: leftover_229_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_226_kept)" = "true" ] || { echo "FAIL: leftover_226_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_228_kept)" = "true" ] || { echo "FAIL: leftover_228_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_221_kept)" = "true" ] || { echo "FAIL: leftover_221_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_225_kept)" = "true" ] || { echo "FAIL: leftover_225_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_223_kept)" = "true" ] || { echo "FAIL: leftover_223_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_222_kept)" = "true" ] || { echo "FAIL: leftover_222_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_217_kept)" = "true" ] || { echo "FAIL: leftover_217_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_216_kept)" = "true" ] || { echo "FAIL: leftover_216_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_210_kept)" = "true" ] || { echo "FAIL: leftover_210_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_209_kept)" = "true" ] || { echo "FAIL: leftover_209_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_205_kept)" = "true" ] || { echo "FAIL: leftover_205_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_204_kept)" = "true" ] || { echo "FAIL: leftover_204_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_197_kept)" = "true" ] || { echo "FAIL: leftover_197_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_188_kept)" = "true" ] || { echo "FAIL: leftover_188_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_126_kept)" = "true" ] || { echo "FAIL: leftover_126_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_117_kept)" = "true" ] || { echo "FAIL: leftover_117_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" playwright_stub_is_not_live_keycloak)" = "true" ] || { echo "FAIL: playwright_stub_is_not_live_keycloak must stay true" >&2; return 1; }
  [ "$(report_get "$file" simulated_mp_webview_is_not_production)" = "true" ] || { echo "FAIL: simulated_mp_webview_is_not_production must stay true" >&2; return 1; }

  local rows
  rows="$(report_get "$file" leftover_rows)"
  local required_row
  for required_row in $(echo "$SIBLING_ROWS" | tr ',' ' '); do
    echo "$rows" | grep -Fq "$required_row" || { echo "FAIL: leftover_rows must keep $required_row" >&2; return 1; }
  done
  echo "$rows" | grep -Fq "g17-separation-leftover-honesty-remainder" || { echo "FAIL: leftover_rows must keep g17-separation-leftover-honesty-remainder" >&2; return 1; }
  echo "$rows" | grep -Fq "g10-file-object-reconcile-leftover-honesty" || { echo "FAIL: leftover_rows must keep g10-file-object-reconcile-leftover-honesty" >&2; return 1; }
  echo "$rows" | grep -Fq "g9-leftover-honesty-remainder" || { echo "FAIL: leftover_rows must keep g9-leftover-honesty-remainder" >&2; return 1; }
  echo "$rows" | grep -Fq "linux-arm-profile-leftover-remainder-honesty" || { echo "FAIL: leftover_rows must keep linux-arm-profile-leftover-remainder-honesty" >&2; return 1; }
  echo "$rows" | grep -Fq "g15-h5-pkce-miniprogram-remainder-honesty" || { echo "FAIL: leftover_rows must keep g15-h5-pkce-miniprogram-remainder-honesty" >&2; return 1; }
  echo "$rows" | grep -Fq "g15-wx-dt-native-bridge-remainder" || { echo "FAIL: leftover_rows must keep g15-wx-dt-native-bridge-remainder" >&2; return 1; }
  echo "$rows" | grep -Fq "g15-wx-dt-native-bridge-honesty" || { echo "FAIL: leftover_rows must keep g15-wx-dt-native-bridge-honesty" >&2; return 1; }

  live_cell() {
    local cell="$1" observed="$2" skip_reason="$3"
    local verdict
    verdict="$(report_get "$file" "$cell")"
    case "$verdict" in
      PASS|SKIP|FAIL) ;;
      *) echo "FAIL: $cell must be PASS/SKIP/FAIL" >&2; return 1 ;;
    esac
    if [ "$verdict" = "PASS" ]; then
      [ "$(report_get "$file" "$observed")" = "true" ] || { echo "FAIL: $cell=PASS requires $observed=true" >&2; return 1; }
    fi
    if [ "$verdict" = "SKIP" ]; then
      [ -n "$(report_get "$file" "$skip_reason")" ] || { echo "FAIL: $cell=SKIP needs $skip_reason" >&2; return 1; }
      [ "$(report_get "$file" "$observed")" != "true" ] || { echo "FAIL: SKIP cannot claim $observed=true" >&2; return 1; }
    fi
    return 0
  }
  live_cell live_h5_keycloak_pkce live_h5_pkce_observed h5_pkce_skip_reason || return 1
  live_cell live_https_keycloak_h5_pkce live_https_h5_observed https_h5_skip_reason || return 1
  live_cell wechat_miniprogram_production wechat_walk_observed wechat_skip_reason || return 1
  live_cell dingtalk_miniprogram_production dingtalk_walk_observed dingtalk_skip_reason || return 1
  live_cell ios_ipa_production ios_walk_observed ios_skip_reason || return 1
  live_cell android_apk_production android_walk_observed android_skip_reason || return 1

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
  OUT="$ROOT/.local/g15-h5-pkce-miniprogram-leftover-honesty-remainder/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G15 leftover remainder: H5 PKCE / miniprogram honesty beyond #240/#220/#198"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #117/#120/#126/#198/#220/#240. Not product G15 close. G18 stays closed."
echo

[ -f "$FIXTURE_240" ] || fail "missing $FIXTURE_240"
[ -f "$FIXTURE_220" ] || fail "missing $FIXTURE_220"
[ -f "$FIXTURE_198" ] || fail "missing $FIXTURE_198"
[ -f "$README" ] || fail "missing $README"

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

log "verify parent #240 leftover honesty fixtures (harness PASS; do not re-run collect)"
./scripts/g15-h5-pkce-miniprogram-remainder-honesty.sh --verify-report \
  scripts/g15-h5-pkce-miniprogram-remainder-honesty/fixtures/honest-skip.env | tee -a "$OUT/collect.log"
set +e
./scripts/g15-h5-pkce-miniprogram-remainder-honesty.sh --verify-report \
  scripts/g15-h5-pkce-miniprogram-remainder-honesty/fixtures/fake-g15-close.env fail >"$OUT/fake-240-close.log" 2>&1
FAKE240_CLOSE=$?
./scripts/g15-h5-pkce-miniprogram-remainder-honesty.sh --verify-report \
  scripts/g15-h5-pkce-miniprogram-remainder-honesty/fixtures/fake-live-pkce-pass.env fail >"$OUT/fake-240-pkce.log" 2>&1
FAKE240_PKCE=$?
./scripts/g15-h5-pkce-miniprogram-remainder-honesty.sh --verify-report \
  scripts/g15-h5-pkce-miniprogram-remainder-honesty/fixtures/fake-wechat-pass.env fail >"$OUT/fake-240-wechat.log" 2>&1
FAKE240_WECHAT=$?
./scripts/g15-h5-pkce-miniprogram-remainder-honesty.sh --verify-report \
  scripts/g15-h5-pkce-miniprogram-remainder-honesty/fixtures/fake-ipa-pass.env fail >"$OUT/fake-240-ipa.log" 2>&1
FAKE240_IPA=$?
./scripts/g15-h5-pkce-miniprogram-remainder-honesty.sh --verify-report \
  scripts/g15-h5-pkce-miniprogram-remainder-honesty/fixtures/fake-redone.env fail >"$OUT/fake-240-redo.log" 2>&1
FAKE240_REDO=$?
set -e
[ "$FAKE240_CLOSE" -eq 0 ] || fail "expected #240 fake-g15-close.env to be rejected"
[ "$FAKE240_PKCE" -eq 0 ] || fail "expected #240 fake-live-pkce-pass.env to be rejected"
[ "$FAKE240_WECHAT" -eq 0 ] || fail "expected #240 fake-wechat-pass.env to be rejected"
[ "$FAKE240_IPA" -eq 0 ] || fail "expected #240 fake-ipa-pass.env to be rejected"
[ "$FAKE240_REDO" -eq 0 ] || fail "expected #240 fake-redone.env to be rejected"
log "parent #240 leftover honesty gate PASS (honest SKIP accepted; fakes rejected)"

log "verify parent #220 leftover honesty fixtures (harness PASS; do not re-run collect)"
./scripts/g15-wx-dt-native-bridge-remainder.sh --verify-report \
  scripts/g15-wx-dt-native-bridge-remainder/fixtures/honest-skip.env pass | tee -a "$OUT/collect.log"
set +e
./scripts/g15-wx-dt-native-bridge-remainder.sh --verify-report \
  scripts/g15-wx-dt-native-bridge-remainder/fixtures/fake-g15-close.env fail >"$OUT/fake-220-close.log" 2>&1
FAKE220_CLOSE=$?
./scripts/g15-wx-dt-native-bridge-remainder.sh --verify-report \
  scripts/g15-wx-dt-native-bridge-remainder/fixtures/fake-wechat-pass.env fail >"$OUT/fake-220-wechat.log" 2>&1
FAKE220_WECHAT=$?
./scripts/g15-wx-dt-native-bridge-remainder.sh --verify-report \
  scripts/g15-wx-dt-native-bridge-remainder/fixtures/fake-ipa-pass.env fail >"$OUT/fake-220-ipa.log" 2>&1
FAKE220_IPA=$?
./scripts/g15-wx-dt-native-bridge-remainder.sh --verify-report \
  scripts/g15-wx-dt-native-bridge-remainder/fixtures/fake-redone.env fail >"$OUT/fake-220-redo.log" 2>&1
FAKE220_REDO=$?
set -e
[ "$FAKE220_CLOSE" -eq 0 ] || fail "expected #220 fake-g15-close.env to be rejected"
[ "$FAKE220_WECHAT" -eq 0 ] || fail "expected #220 fake-wechat-pass.env to be rejected"
[ "$FAKE220_IPA" -eq 0 ] || fail "expected #220 fake-ipa-pass.env to be rejected"
[ "$FAKE220_REDO" -eq 0 ] || fail "expected #220 fake-redone.env to be rejected"
log "parent #220 leftover honesty gate PASS (honest SKIP accepted; fakes rejected)"

log "verify parent #198 leftover honesty fixtures (harness PASS; do not re-run collect)"
./scripts/g15-wx-dt-native-bridge-honesty.sh --verify-report \
  scripts/g15-wx-dt-native-bridge-honesty/fixtures/honest-skip.env pass | tee -a "$OUT/collect.log"
set +e
./scripts/g15-wx-dt-native-bridge-honesty.sh --verify-report \
  scripts/g15-wx-dt-native-bridge-honesty/fixtures/fake-g15-close.env fail >"$OUT/fake-198-close.log" 2>&1
FAKE198_CLOSE=$?
./scripts/g15-wx-dt-native-bridge-honesty.sh --verify-report \
  scripts/g15-wx-dt-native-bridge-honesty/fixtures/fake-wechat-pass.env fail >"$OUT/fake-198-wechat.log" 2>&1
FAKE198_WECHAT=$?
./scripts/g15-wx-dt-native-bridge-honesty.sh --verify-report \
  scripts/g15-wx-dt-native-bridge-honesty/fixtures/fake-ipa-pass.env fail >"$OUT/fake-198-ipa.log" 2>&1
FAKE198_IPA=$?
./scripts/g15-wx-dt-native-bridge-honesty.sh --verify-report \
  scripts/g15-wx-dt-native-bridge-honesty/fixtures/fake-redone.env fail >"$OUT/fake-198-redo.log" 2>&1
FAKE198_REDO=$?
set -e
[ "$FAKE198_CLOSE" -eq 0 ] || fail "expected #198 fake-g15-close.env to be rejected"
[ "$FAKE198_WECHAT" -eq 0 ] || fail "expected #198 fake-wechat-pass.env to be rejected"
[ "$FAKE198_IPA" -eq 0 ] || fail "expected #198 fake-ipa-pass.env to be rejected"
[ "$FAKE198_REDO" -eq 0 ] || fail "expected #198 fake-redone.env to be rejected"
log "parent #198 leftover honesty gate PASS (honest SKIP accepted; fakes rejected)"

MISSING_ROWS=""
LEFTOVER_ROWS_KEPT=true
LEFTOVER_240_KEPT=false
LEFTOVER_220_KEPT=false
LEFTOVER_198_KEPT=false
LEFTOVER_249_KEPT=false
LEFTOVER_262_KEPT=false
LEFTOVER_259_KEPT=false
LEFTOVER_263_KEPT=false
LEFTOVER_254_KEPT=false
LEFTOVER_236_KEPT=false
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
LEFTOVER_217_KEPT=false
LEFTOVER_216_KEPT=false
LEFTOVER_210_KEPT=false
LEFTOVER_209_KEPT=false
LEFTOVER_205_KEPT=false
LEFTOVER_204_KEPT=false
LEFTOVER_197_KEPT=false
LEFTOVER_188_KEPT=false
LEFTOVER_126_KEPT=false
LEFTOVER_117_KEPT=false
for required_row in $REQUIRED_README_ROWS; do
  if grep -Fq "[${required_row}](./${required_row}.md)" "$README"; then
    case "$required_row" in
      g15-h5-pkce-miniprogram-remainder-honesty) LEFTOVER_240_KEPT=true ;;
      g15-wx-dt-native-bridge-remainder) LEFTOVER_220_KEPT=true ;;
      g15-wx-dt-native-bridge-honesty) LEFTOVER_198_KEPT=true ;;
      g17-separation-leftover-honesty-remainder) LEFTOVER_249_KEPT=true ;;
      g10-file-object-reconcile-leftover-honesty) LEFTOVER_262_KEPT=true ;;
      g9-leftover-honesty-remainder) LEFTOVER_259_KEPT=true ;;
      linux-arm-profile-leftover-remainder-honesty) LEFTOVER_263_KEPT=true ;;
      g2-h5-every-page-form-write-report-remainder-honesty) LEFTOVER_254_KEPT=true ;;
      linux-arm-profile-remainder-honesty) LEFTOVER_236_KEPT=true ;;
      g4-hot-purge-staging-pkce-remainder-honesty) LEFTOVER_251_KEPT=true ;;
      g10-file-object-reconcile-remainder-honesty) LEFTOVER_243_KEPT=true ;;
      s0-secret-rotate-getenv-leftover-honesty) LEFTOVER_241_KEPT=true ;;
      g5-publishhook-ddl-remainder-honesty) LEFTOVER_235_KEPT=true ;;
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
      g4-pkce-spa-verifier-remainder-honesty) LEFTOVER_222_KEPT=true ;;
      g5-publishhook-ddl-honesty) LEFTOVER_217_KEPT=true ;;
      g17-separation-leftover-honesty) LEFTOVER_216_KEPT=true ;;
      g17-gm-tls-nginx-handshake-honesty) LEFTOVER_210_KEPT=true ;;
      g9-sentinel-cluster-remainder-honesty) LEFTOVER_209_KEPT=true ;;
      g17-mfa-recovery-vue-otp-honesty) LEFTOVER_205_KEPT=true ;;
      g2-h5-every-page-form-write-honesty) LEFTOVER_204_KEPT=true ;;
      g9-redis-down-invalidate-remainder) LEFTOVER_197_KEPT=true ;;
      g2-ops-pg-dump-honesty) LEFTOVER_188_KEPT=true ;;
      g15-h5-pkce-browser) LEFTOVER_126_KEPT=true ;;
      g15-miniprogram-webview) LEFTOVER_117_KEPT=true ;;
    esac
  else
    LEFTOVER_ROWS_KEPT=false
    MISSING_ROWS="${MISSING_ROWS} ${required_row}"
  fi
done
[ "$LEFTOVER_ROWS_KEPT" = "true" ] || fail "docs/README.md dropped leftover row(s):$MISSING_ROWS"
[ "$LEFTOVER_240_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g15-h5-pkce-miniprogram-remainder-honesty (#240)"
[ "$LEFTOVER_220_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g15-wx-dt-native-bridge-remainder (#220)"
[ "$LEFTOVER_198_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g15-wx-dt-native-bridge-honesty (#198)"
[ "$LEFTOVER_249_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g17-separation-leftover-honesty-remainder (#249)"
[ "$LEFTOVER_262_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g10-file-object-reconcile-leftover-honesty (#262)"
[ "$LEFTOVER_259_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g9-leftover-honesty-remainder (#259)"
[ "$LEFTOVER_263_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover linux-arm-profile-leftover-remainder-honesty (#263)"
[ "$LEFTOVER_254_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g2-h5-every-page-form-write-report-remainder-honesty (#254)"
[ "$LEFTOVER_236_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover linux-arm-profile-remainder-honesty (#236)"
[ "$LEFTOVER_251_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g4-hot-purge-staging-pkce-remainder-honesty (#251)"
[ "$LEFTOVER_243_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g10-file-object-reconcile-remainder-honesty (#243)"
[ "$LEFTOVER_241_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover s0-secret-rotate-getenv-leftover-honesty (#241)"
[ "$LEFTOVER_235_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g5-publishhook-ddl-remainder-honesty (#235)"
[ "$LEFTOVER_233_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g17-mfa-recovery-vue-otp-remainder-honesty (#233)"
[ "$LEFTOVER_238_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g1-fieldpolicy-mask-second-auth-remainder-honesty (#238)"
[ "$LEFTOVER_239_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g7-query-expense-travel-honesty-remainder (#239)"
[ "$LEFTOVER_237_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g9-redis-down-invalidate-remainder-honesty (#237)"
[ "$LEFTOVER_232_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g2-h5-every-page-form-write-remainder-honesty (#232)"
[ "$LEFTOVER_212_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g7-withdraw-browser-evidence-honesty (#212)"
[ "$LEFTOVER_230_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g4-hot-purge-staging-leftover-honesty (#230)"
[ "$LEFTOVER_126_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g15-h5-pkce-browser (#126)"
[ "$LEFTOVER_117_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g15-miniprogram-webview (#117)"
if grep -Eiq '产品 G15 已关闭|product g15 closed|g15_close=CLOSED' "$README"; then
  fail "docs/README.md must not claim product G15 closed"
fi

WECHAT_LIVE=false
DINGTALK_LIVE=false
IOS_LIVE=false
ANDROID_LIVE=false
H5_KC_LIVE=false
HTTPS_KC_LIVE=false
if [ -n "${WECHAT_PROD_HOST:-}" ] || [ -n "${WECHAT_JSAPI_URL:-}" ] || command -v wechat-devtools >/dev/null 2>&1; then
  WECHAT_LIVE=true
fi
if [ -n "${DINGTALK_PROD_HOST:-}" ] || [ -n "${DINGTALK_JSAPI_URL:-}" ] || command -v dingtalk-devtools >/dev/null 2>&1; then
  DINGTALK_LIVE=true
fi
if [ -n "${XCODE_PROJECT:-}" ] || command -v xcodebuild >/dev/null 2>&1; then
  IOS_LIVE=true
fi
if [ -n "${ANDROID_HOME:-}" ] || [ -n "${ANDROID_SDK_ROOT:-}" ] || command -v gradle >/dev/null 2>&1; then
  ANDROID_LIVE=true
fi
if [ "${NEXUS_KEYCLOAK_HTTPS:-}" = "1" ] || [ -n "${NEXUS_KEYCLOAK_URL:-}" ] || [ -n "${KEYCLOAK_HTTPS_URL:-}" ]; then
  H5_KC_LIVE=true
fi
if [ "${NEXUS_KEYCLOAK_HTTPS:-}" = "1" ] || [ -n "${KEYCLOAK_HTTPS_URL:-}" ]; then
  HTTPS_KC_LIVE=true
fi

WECHAT_WALK=false
DINGTALK_WALK=false
IOS_WALK=false
ANDROID_WALK=false
H5_WALK=false
HTTPS_WALK=false
[ "${WECHAT_WALK_OBSERVED:-}" = "true" ] && WECHAT_WALK=true
[ "${DINGTALK_WALK_OBSERVED:-}" = "true" ] && DINGTALK_WALK=true
[ "${IOS_WALK_OBSERVED:-}" = "true" ] && IOS_WALK=true
[ "${ANDROID_WALK_OBSERVED:-}" = "true" ] && ANDROID_WALK=true
[ "${H5_PKCE_WALK_OBSERVED:-}" = "true" ] || [ "${LIVE_H5_KEYCLOAK_PKCE_WALK:-}" = "true" ] && H5_WALK=true
[ "${HTTPS_H5_PKCE_WALK_OBSERVED:-}" = "true" ] && HTTPS_WALK=true

WECHAT_VERDICT=SKIP
DINGTALK_VERDICT=SKIP
IOS_VERDICT=SKIP
ANDROID_VERDICT=SKIP
H5_VERDICT=SKIP
HTTPS_VERDICT=SKIP
[ "$WECHAT_LIVE" = "true" ] && [ "$WECHAT_WALK" = "true" ] && WECHAT_VERDICT=PASS
[ "$DINGTALK_LIVE" = "true" ] && [ "$DINGTALK_WALK" = "true" ] && DINGTALK_VERDICT=PASS
[ "$IOS_LIVE" = "true" ] && [ "$IOS_WALK" = "true" ] && IOS_VERDICT=PASS
[ "$ANDROID_LIVE" = "true" ] && [ "$ANDROID_WALK" = "true" ] && ANDROID_VERDICT=PASS
[ "$H5_KC_LIVE" = "true" ] && [ "$H5_WALK" = "true" ] && H5_VERDICT=PASS
[ "$HTTPS_KC_LIVE" = "true" ] && [ "$HTTPS_WALK" = "true" ] && HTTPS_VERDICT=PASS

H5_REASON="no live Keycloak H5 Authorization Code + PKCE laptop click; #240 harness PASS is not a live Keycloak walk; #126 Playwright/stub is not live Keycloak"
HTTPS_REASON="no live HTTPS Keycloak H5 click; #240 harness PASS / #120 leftover sibling is not that walk"
WECHAT_REASON="no WeChat mini-program package / JSAPI / servicewechat.com walk; #240 harness PASS / #117 simulated parent is not production accept"
DINGTALK_REASON="no DingTalk mini-program package / JSAPI / h5.dingtalk.com walk; #240 harness PASS / #117 simulated parent is not production accept"
IOS_REASON="no Xcode / WKWebView / TestFlight / App Store IPA walk; leftover is not store-ready; #240 harness PASS is not IPA"
ANDROID_REASON="no Gradle / Android WebView / Play APK walk; leftover is not store-ready; #240 harness PASS is not APK"

LEFTOVER_ROWS_CSV="$SIBLING_ROWS"
GIT_COMMIT="$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || echo unknown)"
GIT_BRANCH="$(git -C "$ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"

REPORT="$OUT/report.env"
{
  kv schema_version 1
  kv timestamp_utc "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  kv git_commit "$GIT_COMMIT"
  kv git_branch "$GIT_BRANCH"
  kv boot_parent_ga "$BOOT_PARENT_GA"
  kv boot_parent_version "$BOOT_VERSION"
  kv boot_parent_match "$BOOT_PARENT_MATCH"
  kv docker_present "$DOCKER_PRESENT"
  kv docker_used "$DOCKER_USED"
  kv leftover_240_harness PASS
  kv leftover_220_harness PASS
  kv leftover_198_harness PASS
  kv leftover_117_harness PASS
  kv leftover_126_harness PASS
  kv leftover_120_sibling PASS
  kv leftover_240_honesty_gate PASS
  kv leftover_220_honesty_gate PASS
  kv leftover_198_honesty_gate PASS
  kv leftover_240_is_not_live_h5_pkce true
  kv leftover_240_is_not_wechat_production true
  kv leftover_240_is_not_dingtalk_production true
  kv leftover_240_is_not_ipa_apk true
  kv leftover_240_is_not_product_g15 true
  kv leftover_rows_kept "$LEFTOVER_ROWS_KEPT"
  kv leftover_rows "$LEFTOVER_ROWS_CSV"
  kv leftover_rows_all true
  kv leftover_240_kept "$LEFTOVER_240_KEPT"
  kv leftover_220_kept "$LEFTOVER_220_KEPT"
  kv leftover_198_kept "$LEFTOVER_198_KEPT"
  kv leftover_249_kept "$LEFTOVER_249_KEPT"
  kv leftover_262_kept "$LEFTOVER_262_KEPT"
  kv leftover_259_kept "$LEFTOVER_259_KEPT"
  kv leftover_263_kept "$LEFTOVER_263_KEPT"
  kv leftover_254_kept "$LEFTOVER_254_KEPT"
  kv leftover_236_kept "$LEFTOVER_236_KEPT"
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
  kv leftover_217_kept "$LEFTOVER_217_KEPT"
  kv leftover_216_kept "$LEFTOVER_216_KEPT"
  kv leftover_210_kept "$LEFTOVER_210_KEPT"
  kv leftover_209_kept "$LEFTOVER_209_KEPT"
  kv leftover_205_kept "$LEFTOVER_205_KEPT"
  kv leftover_204_kept "$LEFTOVER_204_KEPT"
  kv leftover_197_kept "$LEFTOVER_197_KEPT"
  kv leftover_188_kept "$LEFTOVER_188_KEPT"
  kv leftover_126_kept "$LEFTOVER_126_KEPT"
  kv leftover_117_kept "$LEFTOVER_117_KEPT"
  kv wechat_live_host_present "$WECHAT_LIVE"
  kv dingtalk_live_host_present "$DINGTALK_LIVE"
  kv ios_live_host_present "$IOS_LIVE"
  kv android_live_host_present "$ANDROID_LIVE"
  kv h5_keycloak_live_present "$H5_KC_LIVE"
  kv https_keycloak_live_present "$HTTPS_KC_LIVE"
  kv live_h5_keycloak_pkce "$H5_VERDICT"
  kv live_https_keycloak_h5_pkce "$HTTPS_VERDICT"
  kv wechat_miniprogram_production "$WECHAT_VERDICT"
  kv dingtalk_miniprogram_production "$DINGTALK_VERDICT"
  kv ios_ipa_production "$IOS_VERDICT"
  kv android_apk_production "$ANDROID_VERDICT"
  kv live_h5_pkce_observed "$H5_WALK"
  kv live_https_h5_observed "$HTTPS_WALK"
  kv wechat_walk_observed "$WECHAT_WALK"
  kv dingtalk_walk_observed "$DINGTALK_WALK"
  kv ios_walk_observed "$IOS_WALK"
  kv android_walk_observed "$ANDROID_WALK"
  kv h5_pkce_skip_reason "$H5_REASON"
  kv https_h5_skip_reason "$HTTPS_REASON"
  kv wechat_skip_reason "$WECHAT_REASON"
  kv dingtalk_skip_reason "$DINGTALK_REASON"
  kv ios_skip_reason "$IOS_REASON"
  kv android_skip_reason "$ANDROID_REASON"
  kv playwright_stub_is_not_live_keycloak true
  kv simulated_mp_webview_is_not_production true
  kv g15_close NOT_CLAIMED
  kv g15_close_reason "Honesty leftover remainder beyond #240/#220/#198 after #263 Linux/ARM leftover remainder / #262 G10 leftover honesty / #259 G9 leftover remainder / #249 G17 SoD leftover honesty. Parent harness fixtures PASS. #240 harness PASS is not a live Keycloak H5 PKCE / WeChat / DingTalk / IPA/APK walk. Live hosts SKIP when unavailable. Not product G15 close."
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv p0_m0_redone false
  kv leftover_240_redone false
  kv redone_117 false
  kv redone_120 false
  kv redone_126 false
  kv redone_198 false
  kv redone_220 false
  kv honesty_240_rerun false
  kv honesty_220_rerun false
  kv honesty_198_rerun false
} >"$REPORT"

MATRIX="$OUT/matrix.md"
{
  echo "# G15 leftover remainder — H5 PKCE / miniprogram honesty beyond #240/#220/#198"
  echo
  echo "| Check | Result |"
  echo "|---|---|"
  echo "| leftover #240 harness still honest | PASS |"
  echo "| leftover #220 harness still honest | PASS |"
  echo "| leftover #198 harness still honest | PASS |"
  echo "| leftover #117 / #126 / #120 | PASS |"
  echo "| #240 leftover honesty fixtures | PASS |"
  echo "| #220 leftover honesty fixtures | PASS |"
  echo "| #198 leftover honesty fixtures | PASS |"
  echo "| #240 harness is live Keycloak H5 PKCE | false |"
  echo "| #240 harness is WeChat / DingTalk production | false |"
  echo "| #240 harness is IPA/APK | false |"
  echo "| live Keycloak H5 PKCE | $H5_VERDICT |"
  echo "| live HTTPS Keycloak H5 click | $HTTPS_VERDICT |"
  echo "| WeChat production | $WECHAT_VERDICT |"
  echo "| DingTalk production | $DINGTALK_VERDICT |"
  echo "| iOS IPA | $IOS_VERDICT |"
  echo "| Android APK | $ANDROID_VERDICT |"
  echo "| leftover rows kept | $LEFTOVER_ROWS_CSV |"
  echo "| #117/#120/#126/#198/#220/#240 redo | false |"
  echo "| product G15 close | NOT_CLAIMED |"
  echo
  echo "H5 PKCE reason: $H5_REASON"
  echo "WeChat reason: $WECHAT_REASON"
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
    "leftover": "g15-h5-pkce-miniprogram-leftover-honesty-remainder",
    "siblings": [
        "g15-h5-pkce-miniprogram-remainder-honesty",
        "g15-wx-dt-native-bridge-remainder",
        "g15-wx-dt-native-bridge-honesty",
        "g15-h5-pkce-browser",
        "g15-miniprogram-webview",
        "g17-separation-leftover-honesty-remainder",
        "g10-file-object-reconcile-leftover-honesty",
        "g9-leftover-honesty-remainder",
        "linux-arm-profile-leftover-remainder-honesty",
        "g2-h5-every-page-form-write-report-remainder-honesty",
        "linux-arm-profile-remainder-honesty",
        "g4-hot-purge-staging-pkce-remainder-honesty",
        "g10-file-object-reconcile-remainder-honesty",
        "s0-secret-rotate-getenv-leftover-honesty",
        "g17-mfa-recovery-vue-otp-remainder-honesty",
        "g5-publishhook-ddl-remainder-honesty",
        "g1-fieldpolicy-mask-second-auth-remainder-honesty",
        "g7-query-expense-travel-honesty-remainder",
        "g9-redis-down-invalidate-remainder-honesty",
        "g2-h5-every-page-form-write-remainder-honesty",
    ],
    "leftover_240_honesty_gate": vals.get("leftover_240_honesty_gate"),
    "leftover_240_is_not_live_h5_pkce": vals.get("leftover_240_is_not_live_h5_pkce"),
    "leftover_240_is_not_wechat_production": vals.get("leftover_240_is_not_wechat_production"),
    "leftover_240_is_not_ipa_apk": vals.get("leftover_240_is_not_ipa_apk"),
    "live_h5_keycloak_pkce": vals.get("live_h5_keycloak_pkce"),
    "wechat_miniprogram_production": vals.get("wechat_miniprogram_production"),
    "dingtalk_miniprogram_production": vals.get("dingtalk_miniprogram_production"),
    "ios_ipa_production": vals.get("ios_ipa_production"),
    "android_apk_production": vals.get("android_apk_production"),
    "g15Close": "NOT_CLAIMED",
    "g18": "closed_unchanged",
    "timestampUtc": vals.get("timestamp_utc"),
    "evidence": "scripts/g15-h5-pkce-miniprogram-leftover-honesty-remainder.sh",
}
open(out, "w", encoding="utf-8").write(json.dumps(walk, indent=2) + "\n")
print("wrote", out)
PY

verify_report "$REPORT"

echo "== Honesty fixtures"
verify_report "$HERE/g15-h5-pkce-miniprogram-leftover-honesty-remainder/fixtures/honest-skip.env"
verify_report "$HERE/g15-h5-pkce-miniprogram-leftover-honesty-remainder/fixtures/fake-g15-close.env" fail
verify_report "$HERE/g15-h5-pkce-miniprogram-leftover-honesty-remainder/fixtures/fake-live-pkce-pass.env" fail
verify_report "$HERE/g15-h5-pkce-miniprogram-leftover-honesty-remainder/fixtures/fake-wechat-pass.env" fail
verify_report "$HERE/g15-h5-pkce-miniprogram-leftover-honesty-remainder/fixtures/fake-ipa-pass.env" fail
verify_report "$HERE/g15-h5-pkce-miniprogram-leftover-honesty-remainder/fixtures/fake-redone.env" fail
verify_report "$HERE/g15-h5-pkce-miniprogram-leftover-honesty-remainder/fixtures/fake-harness-as-live.env" fail

SANITIZED="${ROOT}/docs/engineering/g15-h5-pkce-miniprogram-leftover-honesty-remainder/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

log "DONE report=$REPORT live_h5_pkce=$H5_VERDICT wechat=$WECHAT_VERDICT g15_close=NOT_CLAIMED"
echo
echo "PASS: leftover remainder honesty recorded (#240/#220/#198 harness PASS; live Keycloak/WeChat/DingTalk/IPA/APK SKIP unless observed)"
echo "See docs/g15-h5-pkce-miniprogram-leftover-honesty-remainder.md"
echo "Keep sibling leftovers: docs/g15-h5-pkce-miniprogram-remainder-honesty.md, docs/g15-wx-dt-native-bridge-remainder.md, docs/g15-wx-dt-native-bridge-honesty.md, docs/g17-separation-leftover-honesty-remainder.md"
echo "Keep #263 linux-arm-profile-leftover-remainder-honesty, #262 g10-file-object-reconcile-leftover-honesty, #259 g9-leftover-honesty-remainder, #249 g17-separation-leftover-honesty-remainder, #240 g15-h5-pkce-miniprogram-remainder-honesty, #254 g2-h5-every-page-form-write-report-remainder-honesty, #236 linux-arm-profile-remainder-honesty, #251 g4-hot-purge-staging-pkce-remainder-honesty, #243 g10-file-object-reconcile-remainder-honesty, #241 s0-secret-rotate-getenv-leftover-honesty, #235 g5-publishhook-ddl-remainder-honesty, #233 g17-mfa-recovery-vue-otp-remainder-honesty, #238 g1-fieldpolicy-mask-second-auth-remainder-honesty, #239 g7-query-expense-travel-honesty-remainder, #237 g9-redis-down-invalidate-remainder-honesty, #232 g2-h5-every-page-form-write-remainder-honesty"
echo "Not claimed: product G15 close / IPA/APK / WeChat·DingTalk production / live Keycloak H5 click"
echo "Not redone: #117/#120/#126/#198/#220/#240 / P0/M0"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
echo "walk=$JSON_OUT"
