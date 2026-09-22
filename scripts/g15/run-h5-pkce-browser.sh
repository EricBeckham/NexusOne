#!/usr/bin/env bash
# G15 leftover: H5 Authorization Code + PKCE on /m/* against a PKCE stub.
# Playwright + system Chrome, no Docker. Does not claim product G15.
# Does not duplicate APP WebView (#114) or 小程序 (#117) harnesses.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

WEB_DIR="$ROOT/web"
G15_DIR="$ROOT/scripts/g15"
STUB_HOST="${NEXUS_G15_STUB_HOST:-127.0.0.1}"
STUB_PORT="${NEXUS_G15_STUB_PORT:-18089}"
WEB_PORT="${NEXUS_G15_WEB_PORT:-4173}"
JSON_OUT="${NEXUS_G15_BROWSER_JSON:-$ROOT/docs/g15-h5-pkce-browser/walk.json}"
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

echo "== G15 H5 PKCE leftover"
echo "cwd=$ROOT"
java -version || true
node -v
echo "docker=no audit_port=untouched g18=closed g15_product_close=false"

if [[ ! -d "$WEB_DIR/node_modules" ]]; then
  (cd "$WEB_DIR" && npm install)
fi
if [[ ! -d "$G15_DIR/node_modules/playwright-core" ]]; then
  (cd "$G15_DIR" && npm install)
fi

echo "== Vitest + wiring (no Chrome)"
(cd "$WEB_DIR" && npm test -- src/mobile/auth/pkce.test.js src/mobile/auth/pkceSession.test.js src/mobile/products/p3-studio/pages/h5-pkce-login.browser.test.js)
(cd "$WEB_DIR" && npm run assert:h5-pkce)

echo "== Vite production preview + PKCE stub API"
(cd "$WEB_DIR" && npm run build)

node "$G15_DIR/stub-h5-pkce-api.mjs" &
STUB_PID=$!
for _ in $(seq 1 40); do
  if curl -sf "http://${STUB_HOST}:${STUB_PORT}/actuator/health" >/dev/null 2>&1; then
    break
  fi
  sleep 0.1
done
if ! curl -sf "http://${STUB_HOST}:${STUB_PORT}/actuator/health" >/dev/null 2>&1; then
  echo "FAIL: PKCE stub did not start on ${STUB_HOST}:${STUB_PORT}"
  exit 1
fi

(
  cd "$WEB_DIR"
  NEXUS_API_ORIGIN="http://${STUB_HOST}:${STUB_PORT}" \
    npx vite preview --host 127.0.0.1 --port "${WEB_PORT}"
) &
WEB_PID=$!
for _ in $(seq 1 50); do
  if curl -sf "http://127.0.0.1:${WEB_PORT}/m/login" >/dev/null 2>&1; then
    break
  fi
  sleep 0.2
done
if ! curl -sf "http://127.0.0.1:${WEB_PORT}/m/login" >/dev/null 2>&1; then
  echo "FAIL: Vite preview did not start on :${WEB_PORT}"
  exit 1
fi

echo "== Playwright walk (system Chrome, data-testid=mobile-pkce-login)"
node "$G15_DIR/h5-pkce-browser.mjs" \
  --base "http://127.0.0.1:${WEB_PORT}" \
  --stub "http://${STUB_HOST}:${STUB_PORT}" \
  --json-out "${JSON_OUT}"

echo
echo "See docs/g15-h5-pkce-browser.md"
echo "Product G15 stays open. APP native / WeChat production stay open."
