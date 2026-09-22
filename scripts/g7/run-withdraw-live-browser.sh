#!/usr/bin/env bash
# G7 leftover: live product-JAR + IdP browser withdraw.
# Honest SKIP unless a healthy JAR, issued context, live/Keycloak IdP,
# and system Chrome are actually present. Stub login is not a live walk.
# Does not claim product G7 close. Does not reopen G18.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="${NEXUS_G7_LIVE_BROWSER_JSON:-$ROOT/docs/g7-withdraw-browser/live-walk.json}"
mkdir -p "$(dirname "$OUT")"

chrome_present=false
if [[ -x /usr/bin/google-chrome-stable || -x /usr/bin/google-chrome || -x /usr/bin/chromium || -x /usr/bin/chromium-browser ]]; then
  chrome_present=true
fi

jar_health=SKIP
jar_reason="NEXUS_BASE_URL unset or not healthy"
BASE="${NEXUS_BASE_URL:-}"
if [[ -n "${BASE}" ]] && curl -sf "${BASE}/actuator/health" >/dev/null 2>&1; then
  jar_health=PASS
  jar_reason="GET ${BASE}/actuator/health"
fi

issued_context=false
idp="absent"
if [[ -n "${NEXUS_G7_LIVE_CONTEXT_ID:-}" ]]; then
  issued_context=true
fi
if [[ -n "${NEXUS_G7_LIVE_IDP:-}" ]]; then
  idp="${NEXUS_G7_LIVE_IDP}"
elif [[ -n "${NEXUS_IAM_IDP:-}" && "${NEXUS_IAM_IDP}" != "stub" ]]; then
  idp="${NEXUS_IAM_IDP}"
fi

reason="no live JAR + issued-context IdP Playwright walk on this VM"
result=SKIP
if [[ "${jar_health}" != "PASS" ]]; then
  reason="SKIP: live JAR not healthy (${jar_reason})"
elif [[ "${issued_context}" != "true" ]]; then
  reason="SKIP: no issued X-Nexus-Context-Id (NEXUS_G7_LIVE_CONTEXT_ID unset)"
elif [[ "${idp}" == "absent" || "${idp}" == "stub" ]]; then
  reason="SKIP: live/Keycloak IdP not present (stub login is not a live walk)"
elif [[ "${chrome_present}" != "true" ]]; then
  reason="SKIP: system Chrome/Chromium missing"
else
  reason="SKIP: live JAR+IdP present but this leftover does not drive a product-JAR Playwright click; HTTP ITs + Vue reachability remain the evidence"
fi

python3 - "$OUT" "$result" "$reason" "$jar_health" "$jar_reason" "$issued_context" "$chrome_present" "$idp" <<'PY'
import json, sys
from datetime import datetime, timezone
out, result, reason, jar_health, jar_reason, issued, chrome, idp = sys.argv[1:]
body = {
    "recorded_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    "result": result,
    "reason": reason,
    "surface": "POST /api/v1/p3/path/instances/{id}/withdraw",
    "types": ["leave", "expense", "travel"],
    "live_jar": jar_health == "PASS",
    "jar_health": jar_health,
    "jar_reason": jar_reason,
    "issued_context": issued == "true",
    "chrome_present": chrome == "true",
    "idp": idp,
    "g7_product_close": False,
    "g18": "stays closed",
    "audit_port": "untouched",
    "http_already_proven": [
        "docs/g7-withdraw-evidence.md",
        "docs/g7-withdraw-expense-travel.md",
    ],
    "ui_reachability": "web/src/api/p3-withdraw-http-reachability.test.js",
    "control": "data-testid=todo-withdraw",
}
open(out, "w", encoding="utf-8").write(json.dumps(body, indent=2) + "\n")
print(f"{result}: live G7 withdraw browser — {reason}")
print(f"wrote {out}")
PY

node "$ROOT/scripts/g7/verify-withdraw-live-report.mjs" "$OUT"
