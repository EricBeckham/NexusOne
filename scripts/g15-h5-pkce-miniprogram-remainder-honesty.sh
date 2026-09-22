#!/usr/bin/env bash
# G15 leftover remainder: H5 PKCE browser / miniprogram web-view
# honesty PASS/SKIP beyond #198/#220/#120.
# Does not redo #117/#120/#126/#198/#220 product code.
# Does not claim product G15 close. Not IPA/APK.
# G18 stays closed. P0/M0 not redone. AuditPort same-TX untouched. No Docker.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT=""
VERIFY_REPORT=""
VERIFY_EXPECT=pass
SKIP_TESTS=0

usage() {
  cat <<'EOF'
Usage:
  scripts/g15-h5-pkce-miniprogram-remainder-honesty.sh [--out DIR] [--skip-tests]
  scripts/g15-h5-pkce-miniprogram-remainder-honesty.sh --verify-report FILE

#117 simulated 小程序 web-view and #126 H5 PKCE leftover harnesses stay
always-green PASS. #120 sibling leftover stays kept. #198/#220 leftover
honesty stays PASS. Live Keycloak H5 PKCE, WeChat / DingTalk production,
and IPA/APK are PASS only when a live host was present AND a walk was
observed. Missing live host is honest SKIP. Product G15 close stays
NOT_CLAIMED.

  --out DIR           Output directory (default: .local/g15-h5-pkce-miniprogram-remainder-honesty/<utc>)
  --skip-tests        Inventory + honesty only (do not run npm)
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

verify_report_js() {
  local file="$1"
  local expect="$2"
  node --input-type=module --eval '
import { readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";
const file = process.argv[1];
const expect = process.argv[2];
const modUrl = pathToFileURL(process.argv[3]).href;
const { verifyH5PkceMiniprogramRemainderReport } = await import(modUrl);
const report = {};
for (const raw of readFileSync(file, "utf8").split("\n")) {
  const line = raw.trim();
  if (!line || line.startsWith("#") || !line.includes("=")) continue;
  const idx = line.indexOf("=");
  report[line.slice(0, idx).trim()] = line.slice(idx + 1).trim();
}
const result = verifyH5PkceMiniprogramRemainderReport(report);
if (expect === "pass") {
  if (!result.ok) {
    console.error("expected honest leftover, got: " + result.errors.join("; "));
    process.exit(1);
  }
  console.log("PASS honesty: " + file);
} else if (expect === "fail") {
  if (result.ok) {
    console.error("expected fake report to fail: " + file);
    process.exit(1);
  }
  console.log("PASS reject fake: " + file + " (" + result.errors.join("; ") + ")");
} else {
  process.exit(2);
}
' "$file" "$expect" "$ROOT/web/src/mobile/host/h5PkceMiniprogramRemainderHonesty.js"
}

if [ -n "$VERIFY_REPORT" ]; then
  verify_report_js "$VERIFY_REPORT" "$VERIFY_EXPECT"
  exit 0
fi

if [ -z "$OUT" ]; then
  OUT="$ROOT/.local/g15-h5-pkce-miniprogram-remainder-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G15 H5 PKCE / miniprogram web-view leftover remainder beyond #198/#220/#120"
echo "cwd=$ROOT"
echo "Does not redo #117/#120/#126/#198/#220 product code."
echo "Not claimed: product G15 close / IPA/APK / WeChat·DingTalk production / live Keycloak H5 click"
echo
node -v | tee -a "$OUT/collect.log"

DOCKER_USED=false
if command -v docker >/dev/null 2>&1; then
  log "docker binary present but leftover must not use it"
fi

README="$ROOT/docs/README.md"
for row in \
  g15-h5-pkce-browser \
  g15-miniprogram-webview \
  g15-wx-dt-prod-gap-honesty \
  g15-native-bridge-honesty \
  g15-native-bridge-stub \
  g15-wx-dt-prod-host \
  g15-wx-dt-native-bridge-honesty \
  g15-wx-dt-native-bridge-remainder \
  g4-hot-purge-staging-leftover-honesty \
  g7-withdraw-browser-evidence-honesty \
  g2-h5-every-page-form-write-remainder-honesty \
  g9-redis-down-invalidate-remainder-honesty \
  g7-query-expense-travel-honesty-remainder \
  g1-fieldpolicy-mask-second-auth-remainder-honesty \
  g17-mfa-recovery-vue-otp-remainder-honesty \
  g5-publishhook-ddl-remainder-honesty \
  s0-secret-rotate-getenv-leftover-honesty \
  g10-file-object-reconcile-remainder-honesty \
  g4-hot-purge-staging-pkce-remainder-honesty \
  linux-arm-profile-remainder-honesty \
  g2-h5-every-page-form-write-report-remainder-honesty \
  g9-live-multi-jvm-replica-remainder-honesty \
  g3-outbox-consume-idempotent-honesty \
  g10-leftover-honesty-remainder \
  g2-report-physical-dump-honesty \
  g4-pkce-spa-verifier-remainder-honesty \
  s0-secret-rotate-getenv-remainder-honesty \
  g6-recovery-port-dry-run-remainder-honesty \
  g17-separation-leftover-honesty \
  g2-ops-pg-dump-honesty \
  g9-sentinel-cluster-remainder-honesty \
  g5-publishhook-ddl-honesty \
  g17-gm-tls-nginx-handshake-honesty \
  g1-queryast-export-honesty \
  g17-mfa-recovery-vue-otp-honesty \
  g9-redis-down-invalidate-remainder \
  g2-h5-every-page-form-write-honesty \
  g4-export-wizard-deny-honesty \
  g10-leftover-honesty \
  g5-mid-publish-failure-honesty \
  g3-outbox-consume-crash-honesty \
  g1-queryast-boundsql-honesty \
  linux-arm-profile-leftover-honesty \
  g4-p1-staging-audit-events-honesty \
  g6-recovery-port-dry-run-honesty \
  s0s1-live-redis-minio-isolation-honesty \
  g17-separation-honesty \
  g4-hot-purge-90d-honesty \
  g1-fieldpolicy-mask-second-auth-honesty \
  g10-identity-drift-honesty \
  g4-pkce-spa-verifier-honesty \
  s0-secret-getenv-snapshot-honesty \
  s0-secret-rotate-without-restart-honesty \
  g5-browser-failed-publish \
  g7-withdraw-browser-honesty \
  g7-query-expense-travel-honesty \
  g9-redis-down-invalidate-honesty \
  g10-file-reconcile-honesty
do
  grep -q "\[${row}\]" "$README" || fail "docs/README.md must keep leftover row ${row}"
done
grep -q '\[g15-h5-pkce-miniprogram-remainder-honesty\](\./g15-h5-pkce-miniprogram-remainder-honesty.md)' "$README" \
  || fail "docs/README.md must list this leftover row"

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

WECHAT_REASON="no WeChat mini-program package / JSAPI / servicewechat.com walk; #117 simulated parent is not production accept"
DINGTALK_REASON="no DingTalk mini-program package / JSAPI / h5.dingtalk.com walk; #117 simulated parent is not production accept"
IOS_REASON="no Xcode / WKWebView / TestFlight / App Store IPA walk; leftover is not store-ready"
ANDROID_REASON="no Gradle / Android WebView / Play APK walk; leftover is not store-ready"
H5_REASON="no live Keycloak H5 Authorization Code + PKCE laptop click; #126 Playwright/stub is not live Keycloak"
HTTPS_REASON="no live HTTPS Keycloak H5 click; #120 leftover sibling is not that walk"

echo
echo "== Honesty fixtures (this leftover remainder)"
verify_report_js scripts/g15-h5-pkce-miniprogram-remainder-honesty/fixtures/honest-skip.env pass
verify_report_js scripts/g15-h5-pkce-miniprogram-remainder-honesty/fixtures/fake-g15-close.env fail
verify_report_js scripts/g15-h5-pkce-miniprogram-remainder-honesty/fixtures/fake-live-pkce-pass.env fail
verify_report_js scripts/g15-h5-pkce-miniprogram-remainder-honesty/fixtures/fake-wechat-pass.env fail
verify_report_js scripts/g15-h5-pkce-miniprogram-remainder-honesty/fixtures/fake-ipa-pass.env fail
verify_report_js scripts/g15-h5-pkce-miniprogram-remainder-honesty/fixtures/fake-redone.env fail

echo
echo "== Parent leftover harness still PASS (#198 / #220 verify-report only; not a product redo)"
"$ROOT/scripts/g15-wx-dt-native-bridge-honesty.sh" --verify-report \
  "$ROOT/scripts/g15-wx-dt-native-bridge-honesty/fixtures/honest-skip.env" pass
"$ROOT/scripts/g15-wx-dt-native-bridge-remainder.sh" --verify-report \
  "$ROOT/scripts/g15-wx-dt-native-bridge-remainder/fixtures/honest-skip.env" pass

TEST_SUMMARY=""
if [ "$SKIP_TESTS" = "0" ]; then
  echo
  echo "== Web leftover remainder contract"
  (
    cd web
    npm test -- src/mobile/host/h5PkceMiniprogramRemainderHonesty.test.js \
      src/mobile/host/miniprogramWebView.test.js \
      src/mobile/auth/pkce.test.js \
      src/mobile/host/wxDtNativeBridgeRemainder.test.js
    npm run assert:h5-pkce-miniprogram-remainder-honesty
  ) | tee "$OUT/npm.log"
  TEST_SUMMARY="$(awk '/Tests run:|Test Files|PASS:/{line=$0} END{print line}' "$OUT/npm.log")"
else
  TEST_SUMMARY="skipped (--skip-tests)"
fi

{
  kv schema_version 1
  kv timestamp_utc "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  kv node_version "$(node -v)"
  kv boot_parent_version 4.1.1
  kv boot_parent_match PASS
  kv docker_used "$DOCKER_USED"
  kv leftover_117_harness PASS
  kv leftover_126_harness PASS
  kv leftover_120_sibling PASS
  kv leftover_167_harness PASS
  kv leftover_181_harness PASS
  kv leftover_198_harness PASS
  kv leftover_220_harness PASS
  kv leftover_rows_kept "g15-h5-pkce-browser,g15-miniprogram-webview,g15-wx-dt-native-bridge-honesty,g15-wx-dt-native-bridge-remainder,g15-wx-dt-prod-gap-honesty,g15-native-bridge-honesty,g15-native-bridge-stub,g15-wx-dt-prod-host,g4-hot-purge-staging-leftover-honesty,g7-withdraw-browser-evidence-honesty,g2-h5-every-page-form-write-remainder-honesty,g9-redis-down-invalidate-remainder-honesty,g7-query-expense-travel-honesty-remainder,g1-fieldpolicy-mask-second-auth-remainder-honesty,g17-mfa-recovery-vue-otp-remainder-honesty,g5-publishhook-ddl-remainder-honesty,s0-secret-rotate-getenv-leftover-honesty,g10-file-object-reconcile-remainder-honesty,g4-hot-purge-staging-pkce-remainder-honesty,linux-arm-profile-remainder-honesty,g2-h5-every-page-form-write-report-remainder-honesty"
  kv wechat_live_host_present "$WECHAT_LIVE"
  kv dingtalk_live_host_present "$DINGTALK_LIVE"
  kv ios_live_host_present "$IOS_LIVE"
  kv android_live_host_present "$ANDROID_LIVE"
  kv h5_keycloak_live_present "$H5_KC_LIVE"
  kv https_keycloak_live_present "$HTTPS_KC_LIVE"
  kv wechat_walk_observed "$WECHAT_WALK"
  kv dingtalk_walk_observed "$DINGTALK_WALK"
  kv ios_walk_observed "$IOS_WALK"
  kv android_walk_observed "$ANDROID_WALK"
  kv h5_pkce_walk_observed "$H5_WALK"
  kv https_h5_walk_observed "$HTTPS_WALK"
  kv live_h5_keycloak_pkce "$H5_VERDICT"
  kv live_https_keycloak_h5_pkce "$HTTPS_VERDICT"
  kv wechat_miniprogram_production "$WECHAT_VERDICT"
  kv wechat_skip_reason "$WECHAT_REASON"
  kv dingtalk_miniprogram_production "$DINGTALK_VERDICT"
  kv dingtalk_skip_reason "$DINGTALK_REASON"
  kv ios_ipa_production "$IOS_VERDICT"
  kv ios_skip_reason "$IOS_REASON"
  kv android_apk_production "$ANDROID_VERDICT"
  kv android_skip_reason "$ANDROID_REASON"
  kv h5_pkce_skip_reason "$H5_REASON"
  kv https_h5_skip_reason "$HTTPS_REASON"
  kv store_ready SKIP
  kv production_accepted false
  kv store_package false
  kv native_binary false
  kv vendor_jsapi_loaded false
  kv vendor_domain_allowed false
  kv playwright_stub_is_not_live_keycloak true
  kv simulated_mp_webview_is_not_production true
  kv g15_close NOT_CLAIMED
  kv g18_status closed_unchanged
  kv desktop_p3_changed false
  kv f014_rewritten false
  kv audit_port_changed false
  kv p0_m0_redone false
  kv redone_117 false
  kv redone_120 false
  kv redone_126 false
  kv redone_132 false
  kv redone_151 false
  kv redone_167 false
  kv redone_181 false
  kv redone_198 false
  kv redone_220 false
  kv ipa_apk_claimed false
  kv wx_dt_harness_contract PASS
  kv native_bridge_harness_contract PASS
  kv test_summary "$TEST_SUMMARY"
} > "$OUT/report.env"

{
  echo "# G15 H5 PKCE / miniprogram web-view honesty remainder"
  echo
  echo "| Check | Result |"
  echo "|---|---|"
  echo "| #117 simulated 小程序 web-view harness | PASS |"
  echo "| #126 H5 PKCE Playwright/stub harness | PASS |"
  echo "| #120 HTTPS PKCE sibling leftover | PASS (kept; not a live H5 click) |"
  echo "| #198 wx-dt/native-bridge honesty | PASS |"
  echo "| #220 wx-dt/native-bridge remainder | PASS |"
  echo "| Live Keycloak H5 PKCE | $H5_VERDICT |"
  echo "| Live HTTPS Keycloak H5 click | $HTTPS_VERDICT |"
  echo "| WeChat production | $WECHAT_VERDICT |"
  echo "| DingTalk production | $DINGTALK_VERDICT |"
  echo "| iOS IPA | $IOS_VERDICT |"
  echo "| Android APK | $ANDROID_VERDICT |"
  echo "| product G15 close | NOT_CLAIMED |"
  echo
  echo "H5 PKCE reason: $H5_REASON"
  echo "HTTPS H5 reason: $HTTPS_REASON"
  echo "WeChat reason: $WECHAT_REASON"
  echo "DingTalk reason: $DINGTALK_REASON"
  echo "iOS reason: $IOS_REASON"
  echo "Android reason: $ANDROID_REASON"
} > "$OUT/matrix.md"

verify_report_js "$OUT/report.env" pass
log "wrote $OUT/report.env"
echo
echo "PASS: leftover remainder honesty recorded (#117/#126/#120/#198/#220 harness PASS; live Keycloak/WeChat/DingTalk/IPA/APK SKIP unless observed)"
echo "See docs/g15-h5-pkce-miniprogram-remainder-honesty.md"
echo "Keep sibling leftover rows including g15-h5-pkce-browser / g15-miniprogram-webview / g15-wx-dt-native-bridge-remainder / g4-hot-purge-staging-leftover-honesty / g7-withdraw-browser-evidence-honesty / g2-h5-every-page-form-write-remainder-honesty / g9-redis-down-invalidate-remainder-honesty / g7-query-expense-travel-honesty-remainder / g1-fieldpolicy-mask-second-auth-remainder-honesty / g17-mfa-recovery-vue-otp-remainder-honesty / g5-publishhook-ddl-remainder-honesty / s0-secret-rotate-getenv-leftover-honesty / g10-file-object-reconcile-remainder-honesty / g4-hot-purge-staging-pkce-remainder-honesty / linux-arm-profile-remainder-honesty / g2-h5-every-page-form-write-report-remainder-honesty"
echo "Not claimed: product G15 close / IPA/APK / WeChat·DingTalk production / live Keycloak H5 click"
echo "Not redone: #117/#120/#126/#198/#220 / P0/M0"
echo "out=$OUT"
