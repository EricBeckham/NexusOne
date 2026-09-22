#!/usr/bin/env bash
# G4 leftover: human-notebook + live Keycloak CA / SPA-generated PKCE verifier.
#
# Always-green: SPA S256 mint (same helper as H5) + stub client-held authorize-url.
# Live Authorization Code + PKCE against Keycloak is PASS only when OIDC discovery
# answers AND NEXUS_KEYCLOAK_SMOKE=1. Unreachable Keycloak is honest SKIP.
# This leftover does NOT start kc.sh (#108 / #120 own start) and does NOT redo
# stub Playwright (#125). Does not claim product G4 / G18. AuditPort untouched.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT}"
export PATH="${HOME}/tools/apache-maven-3.8.7/bin:${PATH}"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT=""
VERIFY_REPORT=""
REQUIRE_LIVE=0
SKIP_JAVA=0

usage() {
  cat <<'EOF'
Usage:
  scripts/g4-pkce-spa-verifier.sh [--out DIR] [--skip-java] [--require-live]
  scripts/g4-pkce-spa-verifier.sh --verify-report FILE

SPA-held PKCE evidence. Live Keycloak is SKIP unless OIDC discovery answers.
Never starts kc.sh. Never claims product G4 close.

  --out DIR           Output directory (default: .local/g4-pkce-spa-verifier/<utc>)
  --skip-java         Node probe + honesty only (do not run mvn)
  --require-live      Missing live Keycloak is FAIL instead of SKIP
  --verify-report F   Honesty gate on an existing report.env
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --out) OUT="${2:?}"; shift 2 ;;
    --skip-java) SKIP_JAVA=1; shift ;;
    --require-live) REQUIRE_LIVE=1; shift ;;
    --verify-report) VERIFY_REPORT="${2:?}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

fail() { echo "FAIL: $*" >&2; exit 1; }

report_get() {
  local file="$1" key="$2"
  awk -F= -v k="$key" '$1==k {sub(/^[^=]+=/, ""); print; exit}' "$file"
}

verify_report() {
  local file="$1"
  [ -f "$file" ] || fail "report not found: $file"
  local required="schema_version timestamp_utc java_feature boot_parent_version docker_used spa_verifier_mint rfc7636_appendix_b live_keycloak_pkce g4_close g18_status audit_port_changed p0_m0_redone pkce_108_120_125_redone"
  local key
  for key in $required; do
    [ -n "$(report_get "$file" "$key")" ] || fail "missing key $key in $file"
  done

  local java boot docker mint rfc live g4 g18 audit p0 redo
  java="$(report_get "$file" java_feature)"
  boot="$(report_get "$file" boot_parent_version)"
  docker="$(report_get "$file" docker_used)"
  mint="$(report_get "$file" spa_verifier_mint)"
  rfc="$(report_get "$file" rfc7636_appendix_b)"
  live="$(report_get "$file" live_keycloak_pkce)"
  g4="$(report_get "$file" g4_close)"
  g18="$(report_get "$file" g18_status)"
  audit="$(report_get "$file" audit_port_changed)"
  p0="$(report_get "$file" p0_m0_redone)"
  redo="$(report_get "$file" pkce_108_120_125_redone)"

  [ "$java" = "21" ] || fail "java_feature must be 21, got $java"
  [ "$boot" = "4.1.1" ] || fail "boot_parent_version must be 4.1.1, got $boot"
  [ "$docker" = "false" ] || fail "docker_used must be false"
  [ "$g18" = "closed_unchanged" ] || fail "g18_status must stay closed_unchanged"
  [ "$audit" = "false" ] || fail "audit_port_changed must be false"
  [ "$p0" = "false" ] || fail "p0_m0_redone must stay false"
  [ "$redo" = "false" ] || fail "pkce_108_120_125_redone must stay false"
  [ "$g4" = "NOT_CLAIMED" ] || fail "g4_close must stay NOT_CLAIMED (got $g4)"
  [ "$mint" = "PASS" ] || fail "spa_verifier_mint must be PASS"
  [ "$rfc" = "PASS" ] || fail "rfc7636_appendix_b must be PASS"

  if [ "$live" = "PASS" ]; then
    [ "$(report_get "$file" keycloak_http_discovery)" = "PASS" ] \
      || [ "$(report_get "$file" keycloak_https_discovery)" = "PASS" ] \
      || fail "live_keycloak_pkce=PASS requires HTTP or HTTPS OIDC discovery PASS"
    [ -n "$(report_get "$file" live_token_exchange)" ] || fail "live PASS needs live_token_exchange"
    [ "$(report_get "$file" live_token_exchange)" = "PASS" ] || fail "live PASS needs live_token_exchange=PASS"
  fi
  if [ "$live" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_keycloak_skip_reason)" ] || fail "live SKIP needs a reason"
  fi
  case "$live" in
    PASS|SKIP|FAIL) ;;
    *) fail "live_keycloak_pkce must be PASS/SKIP/FAIL" ;;
  esac
  if grep -Eiq 'PASSWORD=|passwd=|client_secret=|code_verifier=' "$file"; then
    fail "report must not contain secrets"
  fi
  echo "PASS: honesty gate $file"
}

if [ -n "$VERIFY_REPORT" ]; then
  verify_report "$VERIFY_REPORT"
  exit 0
fi

if [ -z "$OUT" ]; then
  OUT="${ROOT}/.local/g4-pkce-spa-verifier/${STAMP}"
fi
mkdir -p "$OUT"

echo "== G4 leftover: SPA-held PKCE / live Keycloak CA"
echo "cwd=$ROOT"
echo "out=$OUT"
java -version 2>&1 | head -n 1
node -v
command -v mvn >/dev/null 2>&1 && mvn -version | head -n 1 || echo "mvn: missing (Java skip if --skip-java)"

if [ "$SKIP_JAVA" -eq 0 ]; then
  mvn -pl nexus-p2-iam -am test \
    -Dtest=PkceTest,AuthPkceApiTest,SessionPkceLoginAuditTest,SessionSpaHeldPkceLoginAuditTest \
    -Dsurefire.failIfNoSpecifiedTests=false
fi

if [ -f "${ROOT}/web/package.json" ]; then
  (cd "${ROOT}/web" && npm install --ignore-scripts --no-audit --no-fund >/dev/null)
  node "${ROOT}/web/scripts/assert-g4-spa-verifier.mjs"
fi

HARNESS_JSON="${OUT}/walk.json"
node "${ROOT}/scripts/g4/spa-verifier-harness.mjs" --out "$OUT" --json-out "$HARNESS_JSON"
REPORT="${OUT}/report.env"
[ -f "$REPORT" ] || fail "harness did not write $REPORT"

LIVE="$(report_get "$REPORT" live_keycloak_pkce)"
SKIP_REASON="$(report_get "$REPORT" live_keycloak_skip_reason)"

if [ "${NEXUS_KEYCLOAK_SMOKE:-}" = "1" ] && [ "$SKIP_JAVA" -eq 0 ]; then
  HTTP_DISC="$(report_get "$REPORT" keycloak_http_discovery)"
  HTTPS_DISC="$(report_get "$REPORT" keycloak_https_discovery)"
  if [ "$HTTP_DISC" = "PASS" ] || [ "$HTTPS_DISC" = "PASS" ]; then
    if [ -n "${NEXUS_KEYCLOAK_SMOKE_PASSWORD:-}" ]; then
      set +e
      mvn -pl nexus-p2-iam -am test \
        -Dtest=LiveKeycloakSpaHeldPkceTest \
        -Dsurefire.failIfNoSpecifiedTests=false
      LIVE_RC=$?
      set -e
      if [ "$LIVE_RC" -eq 0 ]; then
        # rewrite live keys without reprinting secrets
        python3 - "$REPORT" <<'PY'
import pathlib, sys
path = pathlib.Path(sys.argv[1])
lines = path.read_text().splitlines()
out = []
seen = set()
for line in lines:
    key = line.split("=", 1)[0]
    if key in {"live_keycloak_pkce", "live_token_exchange", "live_keycloak_skip_reason"}:
        continue
    out.append(line)
    seen.add(key)
out.append("live_keycloak_pkce=PASS")
out.append("live_token_exchange=PASS")
out.append("live_keycloak_skip_reason=")
path.write_text("\n".join(out) + "\n")
PY
        LIVE="PASS"
        SKIP_REASON=""
      else
        python3 - "$REPORT" <<'PY'
import pathlib, sys
path = pathlib.Path(sys.argv[1])
lines = [ln for ln in path.read_text().splitlines()
         if not ln.startswith("live_keycloak_pkce=")
         and not ln.startswith("live_token_exchange=")
         and not ln.startswith("live_keycloak_skip_reason=")]
lines.append("live_keycloak_pkce=FAIL")
lines.append("live_token_exchange=FAIL")
lines.append("live_keycloak_skip_reason=LiveKeycloakSpaHeldPkceTest failed")
path.write_text("\n".join(lines) + "\n")
PY
        LIVE="FAIL"
        SKIP_REASON="LiveKeycloakSpaHeldPkceTest failed"
      fi
    fi
  fi
fi

if [ "$REQUIRE_LIVE" -eq 1 ] && [ "$LIVE" != "PASS" ]; then
  fail "live Keycloak required (--require-live) but live_keycloak_pkce=$LIVE ($SKIP_REASON)"
fi

verify_report "$REPORT"

SANITIZED="${ROOT}/docs/engineering/g4-pkce-spa-verifier/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

echo
echo "SPA_MINT=$(report_get "$REPORT" spa_verifier_mint)"
echo "LIVE_KEYCLOAK_PKCE=$LIVE"
echo "LIVE_SKIP_REASON=${SKIP_REASON:-n/a}"
echo "REPORT=$REPORT"
echo "SANITIZED=$SANITIZED"
echo "Not claimed: product G4 close; G18 reopen; AuditPort same-TX rewrite; #108/#120/#125 redo"
echo "See docs/g4-pkce-spa-verifier.md"
