#!/usr/bin/env bash
# G17 leftover: Vue → Keycloak otp / authenticator enrollment write-back
# honesty harness. Docs + thin probe. Does not redo #96/#118/#133.
# Does not claim 等保 / 密评 / 完整 G17. G18 stays closed.
# Live Keycloak write-back is PASS only with a reachable issuer, a product
# Vue write-back endpoint, and a stored otp credential. Otherwise honest SKIP.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

OUT=""
JSON_OUT="${NEXUS_G17_WRITEBACK_JSON:-$ROOT/docs/g17-vue-otp-writeback/walk.json}"
VERIFY_REPORT=""

usage() {
  cat <<'EOF'
Usage:
  scripts/g17-vue-otp-writeback/run.sh [--out DIR] [--json-out FILE]
  scripts/g17-vue-otp-writeback/run.sh --verify-report FILE

Always-green Vue write-back honesty leftover. Live Keycloak otp write-back
is SKIP when the issuer is down or the product Vue endpoint is absent.

  --out DIR           Output directory (default: .local/g17-vue-otp-writeback/<utc>)
  --json-out FILE     Sanitized walk.json (default: docs/g17-vue-otp-writeback/walk.json)
  --verify-report F   Honesty gate on an existing report.env
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --out) OUT="${2:?}"; shift 2 ;;
    --json-out) JSON_OUT="${2:?}"; shift 2 ;;
    --verify-report) VERIFY_REPORT="${2:?}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

if [ -n "$VERIFY_REPORT" ]; then
  node "$HERE/probe.mjs" --verify-report "$VERIFY_REPORT"
  exit 0
fi

echo "== G17 Vue Keycloak otp write-back leftover"
echo "cwd=$ROOT"
java -version
node -v
echo "docker=no audit_port=untouched g18=closed g17_product_close=false dengbao=not-claimed recovery_codes=not-this-leftover email_mfa=not-this-leftover"

if [[ ! -d "$ROOT/web/node_modules" ]]; then
  (cd "$ROOT/web" && npm install)
fi

echo "== Always-green #133 UX leftover (untouched)"
(cd "$ROOT/web" && npm run test:g17-totp-enroll)
(cd "$ROOT/web" && npm run assert:g17-totp-enroll)

echo "== Always-green write-back honesty"
(cd "$ROOT/web" && npm run test:g17-vue-otp-writeback)
(cd "$ROOT/web" && npm run assert:g17-vue-otp-writeback)

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
if [ -z "$OUT" ]; then
  OUT="$ROOT/.local/g17-vue-otp-writeback/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

echo "== Live Keycloak write-back probe (honest SKIP when unavailable)"
node "$HERE/probe.mjs" --out "$OUT" --json-out "$JSON_OUT"

echo "== Honesty fixtures"
node "$HERE/probe.mjs" --verify-report "$HERE/fixtures/honest-skip.env"
if node "$HERE/probe.mjs" --verify-report "$HERE/fixtures/fake-pass.env"; then
  echo "FAIL: fake-pass.env must be rejected" >&2
  exit 1
fi
if node "$HERE/probe.mjs" --verify-report "$HERE/fixtures/fake-claims.env"; then
  echo "FAIL: fake-claims.env must be rejected" >&2
  exit 1
fi

echo
echo "See docs/g17-vue-otp-writeback.md"
echo "G18 stays closed. G17 / 等保 / 密评 / recovery codes / email MFA are not claimed."
echo "report=$OUT/report.env walk=$JSON_OUT"
