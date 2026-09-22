#!/usr/bin/env bash
# G15 leftover: APP WebView native-bridge honesty beyond #151.
# Extends honest PASS/SKIP. Does not claim IPA/APK production accept.
# Does not close product G15. Keeps both leftover rows.
# No Docker. Desktop P3 untouched.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

verify_report() {
  local file="$1"
  local expect="$2"
  node --input-type=module --eval '
import { readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";
const file = process.argv[1];
const expect = process.argv[2];
const modUrl = pathToFileURL(process.argv[3]).href;
const { verifyNativeBridgeHonestyReport } = await import(modUrl);
const report = {};
for (const raw of readFileSync(file, "utf8").split("\n")) {
  const line = raw.trim();
  if (!line || line.startsWith("#") || !line.includes("=")) continue;
  const idx = line.indexOf("=");
  report[line.slice(0, idx).trim()] = line.slice(idx + 1).trim();
}
const result = verifyNativeBridgeHonestyReport(report);
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
' "$file" "$expect" "$ROOT/web/src/mobile/host/nativeBridgeHonesty.js"
}

if [ "${1:-}" = "--verify-report" ]; then
  file="${2:?report.env}"
  expect="${3:-pass}"
  verify_report "$file" "$expect"
  exit 0
fi

echo "== G15 APP WebView native-bridge honesty beyond #151"
echo "cwd=$ROOT"
node -v

echo
echo "== Honesty fixtures"
verify_report scripts/g15-native-bridge-honesty/fixtures/honest-leftover.env pass
verify_report scripts/g15-native-bridge-honesty/fixtures/fake-g15-close.env fail
verify_report scripts/g15-native-bridge-honesty/fixtures/fake-ipa-apk-pass.env fail

echo
echo "== Web contract"
(
  cd web
  npm test -- src/mobile/host/nativeBridgeHonesty.test.js
  npm run assert:h5-native-bridge-honesty
)

echo
echo "PASS: native-bridge honesty leftover; not IPA/APK accept"
echo "g15_close=NOT_CLAIMED"
echo "See docs/g15-native-bridge-honesty.md"
