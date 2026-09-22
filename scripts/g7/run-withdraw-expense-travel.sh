#!/usr/bin/env bash
# G7 leftover: expense + travel withdraw evidence (catalog already on main #69).
# Always runs the Zonky HTTP IT (PostgreSQL 15.18, no Docker, no live listener).
# If a product JAR is already healthy at NEXUS_BASE_URL, also runs the live HTTP script.
# Does not start Docker. Does not touch AuditPort. Does not rewrite leave or the catalog.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

echo "== G7 expense + travel withdraw (scripted HTTP)"
echo "cwd=$ROOT"
java -version
mvn -version | head -n 1

mvn -pl nexus-p3-studio -am test \
  -Dtest=G7ExpenseTravelWithdrawIT \
  -Dsurefire.failIfNoSpecifiedTests=false

echo
echo "PASS: G7ExpenseTravelWithdrawIT expense/travel submit-start → non-initiator 403 → withdraw + audit"

: "${NEXUS_BASE_URL:=http://127.0.0.1:${NEXUS_HTTP_PORT:-8080}}"
LIVE_JSON="${NEXUS_G7_LIVE_JSON:-$ROOT/docs/g7-withdraw-expense-travel/live-http.json}"
if curl -sf "${NEXUS_BASE_URL}/actuator/health" >/dev/null 2>&1; then
  echo "== live JAR at ${NEXUS_BASE_URL}"
  python3 "$ROOT/scripts/g7/withdraw-expense-travel-http.py" \
    --base "${NEXUS_BASE_URL}" \
    --bootstrap-token "${NEXUS_PLATFORM_BOOTSTRAP_TOKEN:-}" \
    --json-out "${LIVE_JSON}"
else
  echo "SKIP live JAR: ${NEXUS_BASE_URL} not healthy (Zonky IT is the recorded HTTP evidence)"
fi

echo
echo "See docs/g7-withdraw-expense-travel.md"
echo "Not claimed: G7 three-type product close; leave-path evidence stays in docs/g7-withdraw-evidence.md"
