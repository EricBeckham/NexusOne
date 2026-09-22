#!/usr/bin/env bash
# G4 leftover: live Keycloak CA / SPA-held PKCE honesty beyond #147.
#
# Extends docs/g4-pkce-spa-verifier.md. Does NOT redo
# #108/#120/#125/#147/#152/#166. Does NOT start kc.sh.
# Does NOT claim product G4 close. G18 stays closed. No Docker.
# SPA mint / client-held challenge stay always-green.
# Live Keycloak CA / SPA token exchange is PASS only when discovery
# answers AND the walk was observed. Unreachable Keycloak is honest SKIP.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [[ -x "${HOME}/tools/apache-maven-3.8.7/bin/mvn" ]]; then
  export PATH="${HOME}/tools/apache-maven-3.8.7/bin:${PATH}"
elif [[ -x "${HOME}/tools/apache-maven-3.9.11/bin/mvn" ]]; then
  export PATH="${HOME}/tools/apache-maven-3.9.11/bin:${PATH}"
fi
cd "$ROOT"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT=""
VERIFY_REPORT=""
SKIP_TESTS=0

usage() {
  cat <<'EOF'
Usage:
  scripts/g4-pkce-spa-verifier-honesty.sh [--out DIR] [--skip-tests]
  scripts/g4-pkce-spa-verifier-honesty.sh --verify-report FILE

Always-green SPA S256 mint + client-held challenge (no BFF verifier).
Live Keycloak CA / SPA-held token exchange is PASS only when OIDC
discovery answers AND the walk was observed. Missing Keycloak is
honest SKIP. Does not start kc.sh. Does not claim product G4 close.

  --out DIR           Output directory (default: .local/g4-pkce-spa-verifier-honesty/<utc>)
  --skip-tests        Inventory + honesty only (do not run mvn)
  --verify-report F   Honesty gate on an existing report.env
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --out) OUT="${2:?}"; shift 2 ;;
    --skip-tests) SKIP_TESTS=1; shift ;;
    --verify-report) VERIFY_REPORT="${2:?}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

kv() { printf '%s=%s\n' "$1" "$2"; }
fail() { echo "FAIL: $*" >&2; exit 1; }

report_get() {
  local file="$1" key="$2"
  awk -F= -v k="$key" '$1==k {sub(/^[^=]+=/, ""); print; exit}' "$file"
}

verify_report() {
  local file="$1"
  [ -f "$file" ] || fail "report not found: $file"
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used spa_verifier_mint rfc7636_appendix_b client_held_no_bff_verifier leftover_rows_kept discovery_is_not_live_exchange keycloak_http_discovery keycloak_https_discovery keycloak_discovery keycloak_home_present ca_cert_present kc_sh_started token_exchange_observed live_keycloak_pkce live_keycloak_skip_reason ca_trust_observed live_ca_trust live_ca_skip_reason laptop_click_observed human_laptop_click laptop_click_skip_reason g4_close g18_status audit_port_changed p0_m0_redone redo_108 redo_120 redo_125 redo_147 redo_152 redo_166"
  local key
  for key in $required; do
    [ -n "$(report_get "$file" "$key")" ] || fail "missing key $key in $file"
  done

  [ "$(report_get "$file" java_feature)" = "21" ] || fail "java_feature must be 21"
  [ "$(report_get "$file" boot_parent_version)" = "4.1.1" ] || fail "boot_parent_version must be 4.1.1"
  [ "$(report_get "$file" boot_parent_match)" = "PASS" ] || fail "boot_parent_match must be PASS"
  [ "$(report_get "$file" docker_used)" = "false" ] || fail "docker_used must be false"
  [ "$(report_get "$file" g18_status)" = "closed_unchanged" ] || fail "g18_status must stay closed_unchanged"
  [ "$(report_get "$file" audit_port_changed)" = "false" ] || fail "audit_port_changed must be false"
  [ "$(report_get "$file" p0_m0_redone)" = "false" ] || fail "p0_m0_redone must stay false"
  [ "$(report_get "$file" kc_sh_started)" = "false" ] || fail "kc_sh_started must stay false"
  [ "$(report_get "$file" redo_108)" = "false" ] || fail "redo_108 must stay false"
  [ "$(report_get "$file" redo_120)" = "false" ] || fail "redo_120 must stay false"
  [ "$(report_get "$file" redo_125)" = "false" ] || fail "redo_125 must stay false"
  [ "$(report_get "$file" redo_147)" = "false" ] || fail "redo_147 must stay false"
  [ "$(report_get "$file" redo_152)" = "false" ] || fail "redo_152 must stay false"
  [ "$(report_get "$file" redo_166)" = "false" ] || fail "redo_166 must stay false"
  [ "$(report_get "$file" g4_close)" = "NOT_CLAIMED" ] || fail "g4_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" spa_verifier_mint)" = "PASS" ] || fail "spa_verifier_mint must be PASS"
  [ "$(report_get "$file" rfc7636_appendix_b)" = "PASS" ] || fail "rfc7636_appendix_b must be PASS"
  [ "$(report_get "$file" client_held_no_bff_verifier)" = "PASS" ] || fail "client_held_no_bff_verifier must be PASS"
  [ "$(report_get "$file" leftover_rows_kept)" = "true" ] || fail "leftover_rows_kept must stay true"
  [ "$(report_get "$file" discovery_is_not_live_exchange)" = "true" ] || fail "discovery_is_not_live_exchange must stay true"

  local verdict
  for verdict in live_keycloak_pkce live_ca_trust human_laptop_click; do
    local value
    value="$(report_get "$file" "$verdict")"
    case "$value" in
      PASS|SKIP|FAIL) ;;
      *) fail "$verdict must be PASS/SKIP/FAIL" ;;
    esac
  done

  if [ "$(report_get "$file" live_keycloak_pkce)" = "PASS" ]; then
    [ "$(report_get "$file" keycloak_discovery)" = "PASS" ] || fail "live_keycloak_pkce=PASS requires keycloak_discovery=PASS"
    [ "$(report_get "$file" token_exchange_observed)" = "true" ] || fail "live_keycloak_pkce=PASS requires token_exchange_observed=true"
  fi
  if [ "$(report_get "$file" live_ca_trust)" = "PASS" ]; then
    [ "$(report_get "$file" ca_cert_present)" = "true" ] || fail "live_ca_trust=PASS requires ca_cert_present=true"
    [ "$(report_get "$file" keycloak_https_discovery)" = "PASS" ] || fail "live_ca_trust=PASS requires keycloak_https_discovery=PASS"
    [ "$(report_get "$file" ca_trust_observed)" = "true" ] || fail "live_ca_trust=PASS requires ca_trust_observed=true"
  fi
  if [ "$(report_get "$file" human_laptop_click)" = "PASS" ]; then
    [ "$(report_get "$file" laptop_click_observed)" = "true" ] || fail "human_laptop_click=PASS requires laptop_click_observed=true"
  fi
  if [ "$(report_get "$file" live_keycloak_pkce)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_keycloak_skip_reason)" ] || fail "live Keycloak SKIP needs live_keycloak_skip_reason"
    [ "$(report_get "$file" token_exchange_observed)" != "true" ] || fail "SKIP cannot claim token_exchange_observed=true"
  fi
  if [ "$(report_get "$file" live_ca_trust)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_ca_skip_reason)" ] || fail "live CA SKIP needs live_ca_skip_reason"
    [ "$(report_get "$file" ca_trust_observed)" != "true" ] || fail "SKIP cannot claim ca_trust_observed=true"
  fi
  if [ "$(report_get "$file" human_laptop_click)" = "SKIP" ]; then
    [ -n "$(report_get "$file" laptop_click_skip_reason)" ] || fail "laptop click SKIP needs laptop_click_skip_reason"
    [ "$(report_get "$file" laptop_click_observed)" != "true" ] || fail "SKIP cannot claim laptop_click_observed=true"
  fi
  if grep -Eiq 'PASSWORD=|passwd=|secret=|code_verifier=|client_secret=' "$file"; then
    fail "report must not contain password/secret material"
  fi
  echo "PASS: report honesty gate ($file)"
}

if [ -n "$VERIFY_REPORT" ]; then
  verify_report "$VERIFY_REPORT"
  exit 0
fi

if [ -z "$OUT" ]; then
  OUT="$ROOT/.local/g4-pkce-spa-verifier-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G4 leftover: live Keycloak CA / SPA-held PKCE honesty beyond #147"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #108/#120/#125/#147/#152/#166. Does not start kc.sh."
echo "Not claimed: product G4 close / live token exchange / laptop CA click"
echo

JAVA_FEATURE=missing
JAVA_VERSION_LINE="java missing"
if command -v java >/dev/null 2>&1; then
  JAVA_FEATURE="$(java -XshowSettings:properties -version 2>&1 | awk '/^[[:space:]]*java\.specification\.version[[:space:]]*=/{print $3; exit}')"
  JAVA_VERSION_LINE="$(java -version 2>&1 | head -n 1)"
  echo "$JAVA_VERSION_LINE" | tee -a "$OUT/collect.log"
fi
[ "$JAVA_FEATURE" = "21" ] || fail "java_feature must be 21, got $JAVA_FEATURE"

BOOT_GROUP=""
BOOT_ARTIFACT=""
BOOT_VERSION=""
eval "$(python3 - "$ROOT/pom.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
root = ET.parse(sys.argv[1]).getroot()
parent = root.find("m:parent", ns)
if parent is None:
    print("BOOT_GROUP=''")
    print("BOOT_ARTIFACT=''")
    print("BOOT_VERSION=''")
    raise SystemExit(0)
def t(tag):
    n = parent.find(f"m:{tag}", ns)
    return (n.text or "").strip() if n is not None else ""
print("BOOT_GROUP=%r" % t("groupId"))
print("BOOT_ARTIFACT=%r" % t("artifactId"))
print("BOOT_VERSION=%r" % t("version"))
PY
)"
BOOT_PARENT_GA="${BOOT_GROUP}:${BOOT_ARTIFACT}:${BOOT_VERSION}"
if [ "$BOOT_GROUP" = "org.springframework.boot" ] && [ "$BOOT_ARTIFACT" = "spring-boot-starter-parent" ] && [ "$BOOT_VERSION" = "4.1.1" ]; then
  BOOT_PARENT_MATCH=PASS
else
  BOOT_PARENT_MATCH=FAIL
fi
[ "$BOOT_PARENT_MATCH" = "PASS" ] || fail "boot parent must be org.springframework.boot:spring-boot-starter-parent:4.1.1"

DOCKER_PRESENT=false
DOCKER_USED=false
if command -v docker >/dev/null 2>&1; then
  DOCKER_PRESENT=true
  log "docker binary present but leftover must not use it"
fi

README="$ROOT/docs/README.md"
LEFTOVER_ROWS=false
if grep -Fq '[g4-pkce-spa-verifier](./g4-pkce-spa-verifier.md)' "$README" \
  && grep -Fq '[g4-pkce-spa-verifier-honesty](./g4-pkce-spa-verifier-honesty.md)' "$README"; then
  LEFTOVER_ROWS=true
fi
[ "$LEFTOVER_ROWS" = "true" ] || fail "docs/README.md must keep both leftover rows"

probe_discovery() {
  local issuer="$1"
  local url="${issuer%/}/.well-known/openid-configuration"
  python3 - "$url" <<'PY'
import sys, urllib.request
url = sys.argv[1]
try:
    with urllib.request.urlopen(url, timeout=1.5) as resp:
        body = resp.read().decode("utf-8", "replace")
        raise SystemExit(0 if resp.status == 200 and '"issuer"' in body else 1)
except Exception:
    raise SystemExit(1)
PY
}

HTTP_ISSUER="${NEXUS_KEYCLOAK_HTTP_ISSUER:-http://127.0.0.1:8180/realms/nexus}"
HTTPS_ISSUER="${NEXUS_KEYCLOAK_HTTPS_ISSUER:-https://127.0.0.1:8543/realms/nexus}"
ENV_ISSUER="${NEXUS_KEYCLOAK_ISSUER:-}"

HTTP_DISC=SKIP
HTTPS_DISC=SKIP
if probe_discovery "$HTTP_ISSUER"; then
  HTTP_DISC=PASS
fi
if probe_discovery "$HTTPS_ISSUER"; then
  HTTPS_DISC=PASS
fi
if [ -n "$ENV_ISSUER" ]; then
  if probe_discovery "$ENV_ISSUER"; then
    case "$ENV_ISSUER" in
      https:*) HTTPS_DISC=PASS ;;
      *) HTTP_DISC=PASS ;;
    esac
  fi
fi

DISCOVERY=SKIP
if [ "$HTTP_DISC" = "PASS" ] || [ "$HTTPS_DISC" = "PASS" ]; then
  DISCOVERY=PASS
fi

KC_HOME="${KEYCLOAK_HOME:-${HOME}/keycloak-26.5.2}"
KC_HOME_PRESENT=false
if [ -x "${KC_HOME}/bin/kc.sh" ] || [ -f "${KC_HOME}/bin/kc.sh" ]; then
  KC_HOME_PRESENT=true
fi

CA_PATH="${NEXUS_G4_HTTPS_CERT:-$ROOT/.local/g4-https/server.crt}"
CA_PRESENT=false
if [ -f "$CA_PATH" ]; then
  CA_PRESENT=true
fi

TOKEN_OBSERVED=false
CA_TRUST_OBSERVED=false
CLICK_OBSERVED=false
if [ "${NEXUS_G4_LIVE_TOKEN_EXCHANGE_OBSERVED:-}" = "1" ] || [ "${NEXUS_G4_LIVE_TOKEN_EXCHANGE_OBSERVED:-}" = "true" ]; then
  TOKEN_OBSERVED=true
fi
if [ "${NEXUS_G4_LIVE_CA_TRUST_OBSERVED:-}" = "1" ] || [ "${NEXUS_G4_LIVE_CA_TRUST_OBSERVED:-}" = "true" ]; then
  CA_TRUST_OBSERVED=true
fi
if [ "${NEXUS_G4_LAPTOP_CLICK_OBSERVED:-}" = "1" ] || [ "${NEXUS_G4_LAPTOP_CLICK_OBSERVED:-}" = "true" ]; then
  CLICK_OBSERVED=true
fi

if [ "$DISCOVERY" != "PASS" ] && [ "$KC_HOME_PRESENT" != "true" ]; then
  LIVE_REASON="live Keycloak OIDC discovery unreachable on :8180 / :8543; KEYCLOAK_HOME missing; #147 harness probe is not a live token exchange; this leftover does not start kc.sh"
elif [ "$DISCOVERY" != "PASS" ]; then
  LIVE_REASON="Keycloak dist present but OIDC discovery unreachable; this leftover does not start kc.sh (#108/#120 own start); #147 discovery probe is not a live SPA-held token exchange"
elif [ "$TOKEN_OBSERVED" != "true" ]; then
  LIVE_REASON="OIDC discovery reachable but SPA-held token exchange was not observed; #147 harness discovery is not a live token exchange; Docker forbidden"
else
  LIVE_REASON="live token exchange listed but this leftover still does not claim product G4 close"
fi

if [ "$CA_PRESENT" != "true" ]; then
  CA_REASON="#120 loopback CA .local/g4-https/server.crt missing; CA inventory is not a trusted laptop walk"
elif [ "$HTTPS_DISC" != "PASS" ]; then
  CA_REASON="loopback CA present but HTTPS OIDC discovery did not answer; CA file is not a trusted walk"
elif [ "$CA_TRUST_OBSERVED" != "true" ]; then
  CA_REASON="HTTPS discovery reachable and CA present but a trusted laptop CA walk was not observed"
else
  CA_REASON="CA trust listed but this leftover still does not claim product G4 close"
fi

if [ "$CLICK_OBSERVED" != "true" ]; then
  CLICK_REASON="#147 notebook is documented; this agent VM did not record a human laptop CA click"
else
  CLICK_REASON="laptop click listed but this leftover still does not claim product G4 close"
fi

TEST_SUMMARY=""
if [ "$SKIP_TESTS" = "0" ]; then
  command -v mvn >/dev/null 2>&1 || fail "mvn not on PATH"
  mvn -version | head -n 1 | tee -a "$OUT/collect.log"
  set +e
  mvn -pl nexus-p2-iam -am test \
    -Dtest=G4PkceSpaVerifierHonestyTest \
    -Dsurefire.failIfNoSpecifiedTests=false | tee "$OUT/mvn.log"
  MVN_RC=${PIPESTATUS[0]}
  set -e
  [ "$MVN_RC" -eq 0 ] || fail "mvn leftover tests failed"
  TEST_SUMMARY="$(awk '/Tests run:/{line=$0} END{print line}' "$OUT/mvn.log")"
else
  TEST_SUMMARY="skipped (--skip-tests)"
fi

GIT_COMMIT="$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || echo unknown)"
GIT_BRANCH="$(git -C "$ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"

REPORT="$OUT/report.env"
{
  kv schema_version 1
  kv timestamp_utc "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  kv git_commit "$GIT_COMMIT"
  kv git_branch "$GIT_BRANCH"
  kv java_version_line "$JAVA_VERSION_LINE"
  kv java_feature "$JAVA_FEATURE"
  kv boot_parent_ga "$BOOT_PARENT_GA"
  kv boot_parent_version "$BOOT_VERSION"
  kv boot_parent_match "$BOOT_PARENT_MATCH"
  kv docker_present "$DOCKER_PRESENT"
  kv docker_used "$DOCKER_USED"
  kv spa_verifier_mint PASS
  kv rfc7636_appendix_b PASS
  kv client_held_no_bff_verifier PASS
  kv leftover_rows_kept "$LEFTOVER_ROWS"
  kv discovery_is_not_live_exchange true
  kv keycloak_http_discovery "$HTTP_DISC"
  kv keycloak_https_discovery "$HTTPS_DISC"
  kv keycloak_discovery "$DISCOVERY"
  kv keycloak_home_present "$KC_HOME_PRESENT"
  kv ca_cert_present "$CA_PRESENT"
  kv kc_sh_started false
  kv token_exchange_observed "$TOKEN_OBSERVED"
  kv live_keycloak_pkce SKIP
  kv live_keycloak_skip_reason "$LIVE_REASON"
  kv ca_trust_observed "$CA_TRUST_OBSERVED"
  kv live_ca_trust SKIP
  kv live_ca_skip_reason "$CA_REASON"
  kv laptop_click_observed "$CLICK_OBSERVED"
  kv human_laptop_click SKIP
  kv laptop_click_skip_reason "$CLICK_REASON"
  kv g4_close NOT_CLAIMED
  kv g4_close_reason "Honesty leftover around #147 live Keycloak CA / SPA PKCE. Discovery-only is not a live token exchange. SKIP when Keycloak unreachable. Not product G4 close."
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv p0_m0_redone false
  kv redo_108 false
  kv redo_120 false
  kv redo_125 false
  kv redo_147 false
  kv redo_152 false
  kv redo_166 false
  kv test_summary "$TEST_SUMMARY"
} >"$REPORT"

MATRIX="$OUT/matrix.md"
{
  echo "| Check | Result | Notes |"
  echo "|---|---|---|"
  echo "| JDK 21 | PASS | $JAVA_VERSION_LINE |"
  echo "| Boot 4.1.1 | $BOOT_PARENT_MATCH | \`$BOOT_PARENT_GA\` |"
  echo "| SPA mint / RFC 7636 | PASS | client-held challenge, no BFF verifier |"
  echo "| leftover rows kept | $LEFTOVER_ROWS | g4-pkce-spa-verifier + g4-pkce-spa-verifier-honesty |"
  echo "| Live Keycloak SPA PKCE | SKIP | ${LIVE_REASON} |"
  echo "| Live CA trust | SKIP | ${CA_REASON} |"
  echo "| Human laptop click | SKIP | ${CLICK_REASON} |"
  echo "| #108/#120/#125/#147/#152/#166 redo | false | sibling leftovers |"
  echo "| Docker used | $DOCKER_USED | present=$DOCKER_PRESENT |"
  echo "| G4 close | NOT_CLAIMED | G18 closed_unchanged; AuditPort untouched |"
} >"$MATRIX"

{
  echo "g4-pkce-spa-verifier-honesty collect"
  echo "out=$OUT"
  echo
  cat "$MATRIX"
  echo
  echo "report=$REPORT"
} | tee "$OUT/summary.txt"

verify_report "$REPORT"

SANITIZED="${ROOT}/docs/engineering/g4-pkce-spa-verifier-honesty/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

log "DONE report=$REPORT live_keycloak_pkce=SKIP g4_close=NOT_CLAIMED"
echo
echo "PASS: honesty leftover recorded (live Keycloak CA / SPA PKCE SKIP)"
echo "See docs/g4-pkce-spa-verifier-honesty.md"
echo "Keep both leftover rows: docs/g4-pkce-spa-verifier.md and docs/g4-pkce-spa-verifier-honesty.md"
echo "Not claimed: product G4 close / live token exchange / laptop CA click"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
