#!/usr/bin/env bash
# G7 withdraw + reject walkthrough evidence (not a new business class).
# Primary: dual-tenant HTTP IT on Zonky PostgreSQL 15 (no Docker).
# Optional: product-JAR Python walk if NEXUS_BASE_URL is already healthy.
# AuditPort is not modified.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "== G7 withdraw / reject walkthrough evidence"
echo "cwd=$ROOT"
java -version
mvn -version | head -n 1

mvn -pl nexus-p3-studio -am test \
  -Dtest=G7WithdrawWalkthroughIT,G18BusinessPathIT \
  -Dsurefire.failIfNoSpecifiedTests=false

if [[ -f "$ROOT/web/package.json" ]] && command -v node >/dev/null 2>&1; then
  echo "== Vue withdraw/reject wiring"
  (cd "$ROOT/web" && node scripts/assert-p3-live.mjs)
fi

if [[ -n "${NEXUS_BASE_URL:-}" ]] && curl -sf "${NEXUS_BASE_URL}/actuator/health" >/dev/null 2>&1; then
  echo "== product JAR dual-tenant HTTP walk at ${NEXUS_BASE_URL}"
  python3 "$ROOT/scripts/g7-withdraw-walkthrough.py" \
    --base "${NEXUS_BASE_URL}" \
    --bootstrap-token "${NEXUS_PLATFORM_BOOTSTRAP_TOKEN:-}" \
    --json-out "${NEXUS_G7_JSON:-/tmp/nexus-g7-withdraw-walkthrough.json}"
else
  echo "== skip product JAR Python walk (set NEXUS_BASE_URL to a healthy product boot to run it)"
fi

echo
echo "PASS: travel-shaped withdraw + reject dual-tenant HTTP evidence"
echo "See docs/g7-business-types.md"
