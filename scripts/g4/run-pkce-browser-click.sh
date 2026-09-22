#!/usr/bin/env bash
# G4 leftover: desktop Authorization Code + PKCE browser click walk.
# Playwright + system Chrome, stub IdP (G7 pattern), no Docker.
# Does not reopen G18. Does not change AuditPort same-TX. Does not claim product G4 closed.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

WEB_DIR="$ROOT/web"
G4_DIR="$ROOT/scripts/g4"
STUB_HOST="${NEXUS_G4_STUB_HOST:-127.0.0.1}"
STUB_PORT="${NEXUS_G4_STUB_PORT:-18089}"
WEB_PORT="${NEXUS_G4_WEB_PORT:-4174}"
JSON_OUT="${NEXUS_G4_BROWSER_JSON:-$ROOT/docs/g4-pkce-browser-click/walk.json}"
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

echo "== G4 PKCE browser click leftover"
echo "cwd=$ROOT"
java -version
node -v
echo "docker=no audit_port=same-types-canonical-sha256 g18=closed g4_product_close=false"

if [[ ! -d "$WEB_DIR/node_modules" ]]; then
  (cd "$WEB_DIR" && npm install)
fi
if [[ ! -d "$G4_DIR/node_modules/playwright-core" ]]; then
  (cd "$G4_DIR" && npm install)
fi

echo "== Vitest desktop PKCE click (happy-dom)"
(cd "$WEB_DIR" && npm test -- src/views/login-pkce.browser.test.js)

echo "== Vite production preview + stub PKCE IdP"
(cd "$WEB_DIR" && npm run build)

NEXUS_G4_WEB_PORT="${WEB_PORT}" node "$G4_DIR/stub-pkce-idp.mjs" &
STUB_PID=$!
for _ in $(seq 1 40); do
  if curl -sf "http://${STUB_HOST}:${STUB_PORT}/actuator/health" >/dev/null 2>&1; then
    break
  fi
  sleep 0.1
done
if ! curl -sf "http://${STUB_HOST}:${STUB_PORT}/actuator/health" >/dev/null 2>&1; then
  echo "FAIL: stub PKCE IdP did not start on ${STUB_HOST}:${STUB_PORT}"
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

echo "== Playwright walk (system Chrome, data-testid=pkce-login)"
node "$G4_DIR/pkce-browser-click.mjs" \
  --base "http://127.0.0.1:${WEB_PORT}" \
  --stub "http://${STUB_HOST}:${STUB_PORT}" \
  --json-out "${JSON_OUT}"

echo
echo "See docs/g4-pkce-browser-click.md"
echo "G18 stays closed. Product G4 close is not claimed."
