#!/usr/bin/env bash
# G17 leftover: in-app TOTP enrollment UX / copy / error paths.
# Playwright + system Chrome, stub login, no Docker.
# Does not redo MFA/三权/Tongsuo. Does not claim 等保/密评/G17. G18 stays closed.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

WEB_DIR="$ROOT/web"
G17_DIR="$ROOT/scripts/g17"
STUB_HOST="${NEXUS_G17_STUB_HOST:-127.0.0.1}"
STUB_PORT="${NEXUS_G17_STUB_PORT:-18091}"
WEB_PORT="${NEXUS_G17_WEB_PORT:-4175}"
JSON_OUT="${NEXUS_G17_BROWSER_JSON:-$ROOT/docs/g17-totp-enrollment-ux/walk.json}"
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

echo "== G17 TOTP enrollment UX leftover"
echo "cwd=$ROOT"
java -version
node -v
echo "docker=no audit_port=untouched g18=closed g17_product_close=false dengbao=not-claimed"

if [[ ! -d "$WEB_DIR/node_modules" ]]; then
  (cd "$WEB_DIR" && npm install)
fi
if [[ ! -d "$G17_DIR/node_modules/playwright-core" ]]; then
  (cd "$G17_DIR" && npm install)
fi

echo "== Vitest enrollment copy / error paths (happy-dom)"
(cd "$WEB_DIR" && npm run test:g17-totp-enroll)
(cd "$WEB_DIR" && npm run assert:g17-totp-enroll)

echo "== Vite production preview + stub login API"
(cd "$WEB_DIR" && npm run build)

node "$G17_DIR/stub-totp-enrollment-api.mjs" &
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

echo "== Playwright walk (system Chrome)"
node "$G17_DIR/totp-enrollment-ux-browser.mjs" \
  --base "http://127.0.0.1:${WEB_PORT}" \
  --stub "http://${STUB_HOST}:${STUB_PORT}" \
  --json-out "${JSON_OUT}"

echo
echo "See docs/g17-totp-enrollment-ux.md"
echo "G18 stays closed. G17 / 等保 / 密评 are not claimed."
