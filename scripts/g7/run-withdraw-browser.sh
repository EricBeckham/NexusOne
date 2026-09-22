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

echo "== Vitest Vue todo-withdraw + HTTP reachability (happy-dom)"
(cd "$WEB_DIR" && npm run test:g7-withdraw)
echo "== assert leftover rows + HTTP withdraw wiring"
(cd "$WEB_DIR" && npm run assert:g7-withdraw-browser)

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

echo "== live JAR + IdP browser (honest SKIP on this VM unless topology is present)"
"$G7_DIR/run-withdraw-live-browser.sh"
echo "== honesty fixtures (must reject fakes)"
node "$G7_DIR/verify-withdraw-live-report.mjs" "$G7_DIR/fixtures/honest-skip.json"
if node "$G7_DIR/verify-withdraw-live-report.mjs" "$G7_DIR/fixtures/fake-live-pass.json"; then
  echo "FAIL: fake-live-pass.json must be rejected"
  exit 1
fi
if node "$G7_DIR/verify-withdraw-live-report.mjs" "$G7_DIR/fixtures/fake-g7-close.json"; then
  echo "FAIL: fake-g7-close.json must be rejected"
  exit 1
fi

echo
echo "See docs/g7-withdraw-browser.md"
echo "G18 stays closed. G7 product close is not claimed."
