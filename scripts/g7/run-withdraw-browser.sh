#!/usr/bin/env bash
# G7 leftover: Vue todo-withdraw browser walk against stub login.
# Playwright + system Chrome, no Docker. HTTP withdraw on main stays the product proof.
# Does not reopen G18. Does not touch AuditPort. Does not claim a G7 three-type close.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

WEB_DIR="$ROOT/web"
G7_DIR="$ROOT/scripts/g7"
STUB_HOST="${NEXUS_G7_STUB_HOST:-127.0.0.1}"
STUB_PORT="${NEXUS_G7_STUB_PORT:-18087}"
WEB_PORT="${NEXUS_G7_WEB_PORT:-4173}"
JSON_OUT="${NEXUS_G7_BROWSER_JSON:-$ROOT/docs/g7-withdraw-browser/walk.json}"
STUB_PID=""
WEB_PID=""

cleanup() {
  if [[ -n "${WEB_PID}" ]]; then
    kill "${WEB_PID}" >/dev/null 2>&1 || true
    pkill -P "${WEB_PID}" >/dev/null 2>&1 || true
    wait "${WEB_PID}" >/dev/null 2>&1 || true
  fi
  if [[ -n "${STUB_PID}" ]]; then
    kill "${STUB_PID}" >/dev/null 2>&1 || true
    wait "${STUB_PID}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

echo "== G7 withdraw browser leftover"
echo "cwd=$ROOT"
java -version
node -v
echo "docker=no audit_port=untouched g18=closed g7_product_close=false"

if [[ ! -d "$WEB_DIR/node_modules" ]]; then
  (cd "$WEB_DIR" && npm install)
fi
if [[ ! -d "$G7_DIR/node_modules/playwright-core" ]]; then
  (cd "$G7_DIR" && npm install)
fi

echo "== Vitest Vue todo-withdraw click (happy-dom)"
(cd "$WEB_DIR" && npm test -- src/views/studio/todo-withdraw.browser.test.js)

echo "== Vite production preview + stub login API"
(cd "$WEB_DIR" && npm run build)

node "$G7_DIR/stub-withdraw-api.mjs" &
STUB_PID=$!
for _ in $(seq 1 40); do
  if curl -sf "http://${STUB_HOST}:${STUB_PORT}/actuator/health" >/dev/null 2>&1; then
    break
  fi
  sleep 0.1
done
if ! curl -sf "http://${STUB_HOST}:${STUB_PORT}/actuator/health" >/dev/null 2>&1; then
  echo "FAIL: stub login API did not start on ${STUB_HOST}:${STUB_PORT}"
  exit 1
fi

(
  cd "$WEB_DIR"
  NEXUS_API_ORIGIN="http://${STUB_HOST}:${STUB_PORT}" \
    npx vite preview --host 127.0.0.1 --port "${WEB_PORT}"
) &
WEB_PID=$!
for _ in $(seq 1 50); do
  if curl -sf "http://127.0.0.1:${WEB_PORT}/login" >/dev/null 2>&1; then
    break
  fi
  sleep 0.2
done
if ! curl -sf "http://127.0.0.1:${WEB_PORT}/login" >/dev/null 2>&1; then
  echo "FAIL: Vite preview did not start on :${WEB_PORT}"
  exit 1
fi

echo "== Playwright walk (system Chrome, data-testid=todo-withdraw)"
node "$G7_DIR/withdraw-leave-browser.mjs" \
  --base "http://127.0.0.1:${WEB_PORT}" \
  --stub "http://${STUB_HOST}:${STUB_PORT}" \
  --json-out "${JSON_OUT}"

echo
echo "See docs/g7-withdraw-browser.md"
echo "G18 stays closed. G7 product close is not claimed."
