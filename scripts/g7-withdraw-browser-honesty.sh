#!/usr/bin/env bash
# G7 leftover: Vue todo-withdraw / browser evidence honesty beyond #113/#159.
# Does not redo #103/#113/#140/#159. Does not claim product G7 close.
# Live JAR/IdP withdraw is PASS only when a healthy JAR, issued context,
# live/Keycloak IdP, Chrome, and an observed live withdraw click exist.
# Otherwise honest SKIP. G18 stays closed. No Docker.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HERE="$ROOT/scripts/g7-withdraw-browser-honesty"
cd "$ROOT"

OUT=""
JSON_OUT="${NEXUS_G7_WITHDRAW_BROWSER_HONESTY_JSON:-$ROOT/docs/g7-withdraw-browser-honesty/walk.json}"
VERIFY_REPORT=""
SKIP_TESTS=0

usage() {
  cat <<'EOF'
Usage:
  scripts/g7-withdraw-browser-honesty.sh [--out DIR] [--json-out FILE] [--skip-tests]
  scripts/g7-withdraw-browser-honesty.sh --verify-report FILE

Vue stub click + HTTP reachability stay on #113/#159. Live product-JAR +
live/Keycloak IdP browser withdraw is PASS only when those are present
AND a live withdraw click was observed. Missing JAR/IdP is honest SKIP.
Product G7 close stays NOT_CLAIMED. Both leftover rows stay in docs/README.md.

  --out DIR           Output directory (default: .local/g7-withdraw-browser-honesty/<utc>)
  --json-out FILE     Sanitized walk.json (default: docs/g7-withdraw-browser-honesty/walk.json)
  --skip-tests        Inventory + honesty only (do not run Vitest)
  --verify-report F   Honesty gate on an existing report.env
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --out) OUT="${2:?}"; shift 2 ;;
    --json-out) JSON_OUT="${2:?}"; shift 2 ;;
    --skip-tests) SKIP_TESTS=1; shift ;;
    --verify-report) VERIFY_REPORT="${2:?}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

if [ -n "$VERIFY_REPORT" ]; then
  node "$HERE/probe.mjs" --verify-report "$VERIFY_REPORT"
  exit 0
fi

echo "== G7 Vue todo-withdraw / browser evidence leftover honesty"
echo "cwd=$ROOT"
echo "Does not redo #103/#113/#140/#159."
echo "Not claimed: product G7 close / live JAR+IdP withdraw / G18 reopen"
java -version
node -v

if [ "$SKIP_TESTS" = "0" ]; then
  if [[ ! -d "$ROOT/web/node_modules" ]]; then
    (cd "$ROOT/web" && npm install)
  fi
  echo
  echo "== Always-green honesty (does not rerun #113/#159 Vue/Playwright)"
  (cd "$ROOT/web" && npm run test:g7-withdraw-browser-honesty)
  (cd "$ROOT/web" && npm run assert:g7-withdraw-browser-honesty)
fi

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
if [ -z "$OUT" ]; then
  OUT="$ROOT/.local/g7-withdraw-browser-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

echo
echo "== Live JAR/IdP withdraw probe (honest SKIP when unavailable)"
node "$HERE/probe.mjs" --out "$OUT" --json-out "$JSON_OUT"

echo
echo "== Honesty fixtures"
node "$HERE/probe.mjs" --verify-report "$HERE/fixtures/honest-skip.env"
if node "$HERE/probe.mjs" --verify-report "$HERE/fixtures/fake-live-pass.env"; then
  echo "FAIL: fake-live-pass.env must be rejected" >&2
  exit 1
fi
if node "$HERE/probe.mjs" --verify-report "$HERE/fixtures/fake-g7-close.env"; then
  echo "FAIL: fake-g7-close.env must be rejected" >&2
  exit 1
fi

echo
echo "PASS: G7 withdraw-browser leftover honesty recorded (live JAR/IdP SKIP)"
echo "See docs/g7-withdraw-browser-honesty.md"
echo "Not claimed: product G7 close / live JAR+IdP withdraw"
echo "Not redone: #103/#113/#140/#159"
echo "Both leftover rows kept: g7-withdraw-browser + g7-query-expense-travel"
echo "out=$OUT walk=$JSON_OUT"
