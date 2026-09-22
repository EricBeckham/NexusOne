#!/usr/bin/env bash
# G15 leftover: WeChat / DingTalk prod-gap + native-bridge honesty
# PASS/SKIP beyond #167/#181.
# Does not redo #151/#167/#181 product code.
# Does not claim product G15 close. Not IPA/APK.
# G18 stays closed. P0/M0 not redone. No Docker.
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
  scripts/g15-wx-dt-native-bridge-honesty.sh [--out DIR] [--skip-tests]
  scripts/g15-wx-dt-native-bridge-honesty.sh --verify-report FILE

#167 / #181 leftover harness contracts stay always-green PASS.
WeChat / DingTalk production and IPA/APK are PASS only when a live
host was present AND a walk was observed. Missing live host is
honest SKIP. Product G15 close stays NOT_CLAIMED.

  --out DIR           Output directory (default: .local/g15-wx-dt-native-bridge-honesty/<utc>)
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
const { verifyWxDtNativeBridgeHonestyReport } = await import(modUrl);
const report = {};
for (const raw of readFileSync(file, "utf8").split("\n")) {
  const line = raw.trim();
  if (!line || line.startsWith("#") || !line.includes("=")) continue;
  const idx = line.indexOf("=");
  report[line.slice(0, idx).trim()] = line.slice(idx + 1).trim();
}
const result = verifyWxDtNativeBridgeHonestyReport(report);
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
' "$file" "$expect" "$ROOT/web/src/mobile/host/wxDtNativeBridgeHonesty.js"
}

if [ -n "$VERIFY_REPORT" ]; then
  verify_report_js "$VERIFY_REPORT" "$VERIFY_EXPECT"
  exit 0
fi

if [ -z "$OUT" ]; then
  OUT="$ROOT/.local/g15-wx-dt-native-bridge-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G15 WeChat/DingTalk prod-gap + native-bridge leftover honesty beyond #167/#181"
echo "cwd=$ROOT"
echo "Does not redo #151/#167/#181 product code."
echo "Not claimed: product G15 close / IPA/APK / WeChat·DingTalk production"
echo
node -v | tee -a "$OUT/collect.log"

DOCKER_USED=false
if command -v docker >/dev/null 2>&1; then
  log "docker binary present but leftover must not use it"
fi

README="$ROOT/docs/README.md"
for row in \
  g15-wx-dt-prod-gap-honesty \
  g15-native-bridge-honesty \
  g15-native-bridge-stub \
  g15-wx-dt-prod-host \
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
  g5-browser-failed-publish \
  g7-withdraw-browser-honesty \
  g9-redis-down-invalidate-honesty \
  g10-file-reconcile-honesty
do
  grep -q "\[${row}\]" "$README" || fail "docs/README.md must keep leftover row ${row}"
done
grep -q '\[g15-wx-dt-native-bridge-honesty\](\./g15-wx-dt-native-bridge-honesty.md)' "$README" \
  || fail "docs/README.md must list this leftover row"

WECHAT_LIVE=false
DINGTALK_LIVE=false
IOS_LIVE=false
ANDROID_LIVE=false
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

WECHAT_WALK=false
DINGTALK_WALK=false
IOS_WALK=false
ANDROID_WALK=false
[ "${WECHAT_WALK_OBSERVED:-}" = "true" ] && WECHAT_WALK=true
[ "${DINGTALK_WALK_OBSERVED:-}" = "true" ] && DINGTALK_WALK=true
[ "${IOS_WALK_OBSERVED:-}" = "true" ] && IOS_WALK=true
[ "${ANDROID_WALK_OBSERVED:-}" = "true" ] && ANDROID_WALK=true

WECHAT_VERDICT=SKIP
DINGTALK_VERDICT=SKIP
IOS_VERDICT=SKIP
ANDROID_VERDICT=SKIP
[ "$WECHAT_LIVE" = "true" ] && [ "$WECHAT_WALK" = "true" ] && WECHAT_VERDICT=PASS
[ "$DINGTALK_LIVE" = "true" ] && [ "$DINGTALK_WALK" = "true" ] && DINGTALK_VERDICT=PASS
[ "$IOS_LIVE" = "true" ] && [ "$IOS_WALK" = "true" ] && IOS_VERDICT=PASS
[ "$ANDROID_LIVE" = "true" ] && [ "$ANDROID_WALK" = "true" ] && ANDROID_VERDICT=PASS

WECHAT_REASON="no WeChat mini-program package / JSAPI / servicewechat.com walk; #167 leftover harness is not production accept"
DINGTALK_REASON="no DingTalk mini-program package / JSAPI / h5.dingtalk.com walk; #167 leftover harness is not production accept"
IOS_REASON="no Xcode / WKWebView / TestFlight / App Store IPA walk; #151/#181 leftover is not store-ready"
ANDROID_REASON="no Gradle / Android WebView / Play APK walk; #151/#181 leftover is not store-ready"

echo
echo "== Honesty fixtures (this leftover)"
verify_report_js scripts/g15-wx-dt-native-bridge-honesty/fixtures/honest-skip.env pass
verify_report_js scripts/g15-wx-dt-native-bridge-honesty/fixtures/fake-g15-close.env fail
verify_report_js scripts/g15-wx-dt-native-bridge-honesty/fixtures/fake-wechat-pass.env fail
verify_report_js scripts/g15-wx-dt-native-bridge-honesty/fixtures/fake-ipa-pass.env fail
verify_report_js scripts/g15-wx-dt-native-bridge-honesty/fixtures/fake-redone.env fail

echo
echo "== Parent leftover harness still PASS (#167 / #181 verify-report only; not a product redo)"
"$ROOT/scripts/g15-wx-dt-prod-gap-honesty.sh" --verify-report \
  "$ROOT/scripts/g15-wx-dt-prod-gap-honesty/fixtures/honest-leftover.env" pass
"$ROOT/scripts/g15-native-bridge-honesty.sh" --verify-report \
  "$ROOT/scripts/g15-native-bridge-honesty/fixtures/honest-leftover.env" pass

TEST_SUMMARY=""
if [ "$SKIP_TESTS" = "0" ]; then
  echo
  echo "== Web leftover contract"
  (
    cd web
    npm test -- src/mobile/host/wechatDingTalkProdGapHonesty.test.js \
      src/mobile/host/nativeBridgeHonesty.test.js \
      src/mobile/host/wxDtNativeBridgeHonesty.test.js
    npm run assert:h5-wx-dt-native-bridge-honesty
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
  kv wx_dt_harness_contract PASS
  kv native_bridge_harness_contract PASS
  kv leftover_rows_kept "g15-wx-dt-prod-gap-honesty,g15-native-bridge-honesty,g15-native-bridge-stub,g15-wx-dt-prod-host"
  kv wechat_live_host_present "$WECHAT_LIVE"
  kv dingtalk_live_host_present "$DINGTALK_LIVE"
  kv ios_live_host_present "$IOS_LIVE"
  kv android_live_host_present "$ANDROID_LIVE"
  kv wechat_walk_observed "$WECHAT_WALK"
  kv dingtalk_walk_observed "$DINGTALK_WALK"
  kv ios_walk_observed "$IOS_WALK"
  kv android_walk_observed "$ANDROID_WALK"
  kv wechat_miniprogram_production "$WECHAT_VERDICT"
  kv wechat_skip_reason "$WECHAT_REASON"
  kv dingtalk_miniprogram_production "$DINGTALK_VERDICT"
  kv dingtalk_skip_reason "$DINGTALK_REASON"
  kv ios_ipa_production "$IOS_VERDICT"
  kv ios_skip_reason "$IOS_REASON"
  kv android_apk_production "$ANDROID_VERDICT"
  kv android_skip_reason "$ANDROID_REASON"
  kv store_ready SKIP
  kv production_accepted false
  kv store_package false
  kv native_binary false
  kv vendor_jsapi_loaded false
  kv vendor_domain_allowed false
  kv g15_close NOT_CLAIMED
  kv g18_status closed_unchanged
  kv desktop_p3_changed false
  kv f014_rewritten false
  kv audit_port_changed false
  kv p0_m0_redone false
  kv redone_151 false
  kv redone_167 false
  kv redone_181 false
  kv ipa_apk_claimed false
  kv test_summary "$TEST_SUMMARY"
} > "$OUT/report.env"

{
  echo "# G15 WeChat/DingTalk prod-gap + native-bridge honesty"
  echo
  echo "| Check | Result |"
  echo "|---|---|"
  echo "| #167 wx-dt prod-gap harness | PASS |"
  echo "| #181 native-bridge harness | PASS |"
  echo "| WeChat production | $WECHAT_VERDICT |"
  echo "| DingTalk production | $DINGTALK_VERDICT |"
  echo "| iOS IPA | $IOS_VERDICT |"
  echo "| Android APK | $ANDROID_VERDICT |"
  echo "| product G15 close | NOT_CLAIMED |"
  echo
  echo "WeChat reason: $WECHAT_REASON"
  echo "DingTalk reason: $DINGTALK_REASON"
  echo "iOS reason: $IOS_REASON"
  echo "Android reason: $ANDROID_REASON"
} > "$OUT/matrix.md"

verify_report_js "$OUT/report.env" pass
log "wrote $OUT/report.env"
echo
echo "PASS: leftover honesty recorded (#167/#181 harness PASS; live WeChat/DingTalk/IPA/APK SKIP unless observed)"
echo "See docs/g15-wx-dt-native-bridge-honesty.md"
echo "Keep sibling leftover rows including g15-wx-dt-prod-gap-honesty / g15-native-bridge-honesty"
echo "Not claimed: product G15 close / IPA/APK / WeChat·DingTalk production"
echo "Not redone: #151 native-bridge stub / #167 prod-gap honesty / #181 native-bridge honesty / P0/M0"
echo "out=$OUT"
