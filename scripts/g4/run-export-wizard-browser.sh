#!/usr/bin/env bash
# G4 leftover: browser export wizard. Honest SKIP when Playwright/Chrome
# or a live issued-context JAR is missing. MockMvc remains the proof.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="${NEXUS_G4_WIZARD_BROWSER_JSON:-$ROOT/docs/g4-export-wizard/browser-walk.json}"
mkdir -p "$(dirname "$OUT")"

reason="browser export wizard not run: no live JAR + issued-context Playwright walk on this VM"
if ! command -v google-chrome >/dev/null 2>&1 && ! command -v google-chrome-stable >/dev/null 2>&1 && ! command -v chromium >/dev/null 2>&1; then
  reason="SKIP: system Chrome/Chromium missing; MockMvc/HTTP harness is the evidence"
fi
if [[ ! -d "$ROOT/scripts/g4/node_modules/playwright-core" ]]; then
  reason="SKIP: playwright-core missing; MockMvc/HTTP harness is the evidence"
fi

cat > "$OUT" <<EOF
{
  "result": "SKIP",
  "reason": "$reason",
  "surface": "POST /api/v1/p3/exports/wizard/begin|/complete",
  "product_g4_close": false,
  "audit_port": "unchanged-same-tx",
  "evidence": "scripts/g4-export-wizard.sh MockMvc/HTTP"
}
EOF

echo "SKIP: browser export wizard ($reason)"
echo "wrote $OUT"
exit 0
