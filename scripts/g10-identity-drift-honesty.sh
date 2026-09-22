#!/usr/bin/env bash
# G10 leftover: identity drift-fix apply / dry-run honesty beyond #141.
#
# Extends docs/g10-identity-drift-dry-run.md. Does NOT redo identity apply
# product path or #141 / #169. Does NOT claim product G10 / SAML / LDAP /
# SMS MFA. G18 stays closed. No Docker.
# Live Keycloak 8080 dry-run is PASS only when reachable AND a dry-run walk
# was observed. Missing listener / OIDC discovery is honest SKIP.
# This leftover does not start Keycloak and does not run apply.
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
  scripts/g10-identity-drift-honesty.sh [--out DIR] [--skip-tests]
  scripts/g10-identity-drift-honesty.sh --verify-report FILE

Honesty leftover around identity drift-fix apply / dry-run beyond #141.
Live Keycloak 8080 is PASS only when OIDC discovery answers on :8080 and
a dry-run walk was observed. Missing Keycloak is honest SKIP.
Does not redo identity apply / #141 / #169. Product G10 stays NOT_CLAIMED.

  --out DIR           Output directory (default: .local/g10-identity-drift-honesty/<utc>)
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
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used dry_run_default_no_write keycloak_8080_listener keycloak_oidc_discovery live_keycloak_present live_keycloak_8080 live_keycloak_skip_reason live_dry_run_observed live_apply live_apply_skip_reason live_apply_observed identity_apply_product_redone identity_drift_141_redone file_reconcile_169_redone saml_claimed ldap_claimed sms_mfa_claimed g10_close g18_status audit_port_changed p0_m0_redone"
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
  [ "$(report_get "$file" dry_run_default_no_write)" = "true" ] || fail "dry_run_default_no_write must be true"
  [ "$(report_get "$file" identity_apply_product_redone)" = "false" ] || fail "identity_apply_product_redone must stay false"
  [ "$(report_get "$file" identity_drift_141_redone)" = "false" ] || fail "identity_drift_141_redone must stay false"
  [ "$(report_get "$file" file_reconcile_169_redone)" = "false" ] || fail "file_reconcile_169_redone must stay false"
  [ "$(report_get "$file" saml_claimed)" = "false" ] || fail "saml_claimed must stay false"
  [ "$(report_get "$file" ldap_claimed)" = "false" ] || fail "ldap_claimed must stay false"
  [ "$(report_get "$file" sms_mfa_claimed)" = "false" ] || fail "sms_mfa_claimed must stay false"
  [ "$(report_get "$file" g10_close)" = "NOT_CLAIMED" ] || fail "g10_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" live_apply)" = "SKIP" ] || fail "live_apply must stay SKIP (do not redo product apply)"
  [ "$(report_get "$file" live_apply_observed)" = "false" ] || fail "live_apply_observed must stay false"
  [ -n "$(report_get "$file" live_apply_skip_reason)" ] || fail "live_apply SKIP needs live_apply_skip_reason"

  local live
  live="$(report_get "$file" live_keycloak_8080)"
  case "$live" in
    PASS|SKIP|FAIL) ;;
    *) fail "live_keycloak_8080 must be PASS/SKIP/FAIL" ;;
  esac
  if [ "$live" = "PASS" ]; then
    [ "$(report_get "$file" live_keycloak_present)" = "true" ] || fail "live_keycloak_8080=PASS requires live_keycloak_present=true"
    [ "$(report_get "$file" keycloak_oidc_discovery)" = "true" ] || fail "live_keycloak_8080=PASS requires keycloak_oidc_discovery=true"
    [ "$(report_get "$file" live_dry_run_observed)" = "true" ] || fail "live_keycloak_8080=PASS requires live_dry_run_observed=true"
  fi
  if [ "$live" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_keycloak_skip_reason)" ] || fail "live Keycloak SKIP needs live_keycloak_skip_reason"
    [ "$(report_get "$file" live_dry_run_observed)" != "true" ] || fail "SKIP cannot claim a live dry-run walk"
  fi
  if grep -Eiq 'PASSWORD=|passwd=|secret=|CLIENT_SECRET=' "$file"; then
    fail "report must not contain password/secret material"
  fi
  echo "PASS: report honesty gate ($file)"
}

if [ -n "$VERIFY_REPORT" ]; then
  verify_report "$VERIFY_REPORT"
  exit 0
fi

if [[ "${NEXUS_G10_APPLY:-}" == "1" || "${NEXUS_G10_APPLY:-}" == "true" ]]; then
  echo "REFUSE: this leftover is honesty around detect + proposed-fix dry-run (no writes)." >&2
  echo "Apply stays on nexus-p2-iam/scripts/run-g10-identity-reconcile.sh" >&2
  exit 2
fi

if [ -z "$OUT" ]; then
  OUT="$ROOT/.local/g10-identity-drift-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G10 leftover: identity drift-fix apply/dry-run honesty (Keycloak 8080 SKIP when unavailable)"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo identity apply / #141 / #169. Not product G10 close."
echo

JAVA_FEATURE=missing
JAVA_VERSION_LINE=not-installed
if command -v java >/dev/null 2>&1; then
  JAVA_VERSION_LINE="$(java -version 2>&1 | head -n 1 | tr -d '\r')"
  JAVA_FEATURE="$(java -XshowSettings:properties -version 2>&1 | awk '/^[[:space:]]*java\.specification\.version[[:space:]]*=/{print $3; exit}')"
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

port_open() {
  local host="$1" port="$2"
  python3 - "$host" "$port" <<'PY'
import socket, sys
host, port = sys.argv[1], int(sys.argv[2])
s = socket.socket()
s.settimeout(0.2)
try:
    s.connect((host, port))
except OSError:
    raise SystemExit(1)
finally:
    s.close()
PY
}

kc_discovery() {
  local issuer="$1"
  curl -fsS --max-time 3 "${issuer}/.well-known/openid-configuration" 2>/dev/null | grep -q '"issuer"'
}

ISSUER="${NEXUS_KEYCLOAK_ISSUER:-http://127.0.0.1:8080/realms/nexus}"
LISTENER=false
if port_open 127.0.0.1 8080; then
  LISTENER=true
fi
DISCOVERY=false
if command -v curl >/dev/null 2>&1 && kc_discovery "$ISSUER"; then
  DISCOVERY=true
fi

LIVE_PRESENT=false
if [ "$LISTENER" = "true" ] && [ "$DISCOVERY" = "true" ]; then
  LIVE_PRESENT=true
fi

if [ "$LISTENER" != "true" ] && [ "$DISCOVERY" != "true" ]; then
  LIVE_REASON="Keycloak 8080 unavailable; nothing listening on 127.0.0.1:8080 and OIDC discovery not reachable at ${ISSUER}; Docker forbidden"
elif [ "$LISTENER" != "true" ]; then
  LIVE_REASON="OIDC discovery answered but nothing listening on 127.0.0.1:8080; live Keycloak 8080 SKIP"
elif [ "$DISCOVERY" != "true" ]; then
  LIVE_REASON="listener on 127.0.0.1:8080 but OIDC discovery not reachable at ${ISSUER}; live Keycloak 8080 SKIP"
else
  LIVE_REASON="Keycloak 8080 present (${ISSUER}) but this leftover does not claim a dry-run walk until it was observed; does not start Keycloak"
fi

APPLY_REASON="identity apply stays on nexus-p2-iam/scripts/run-g10-identity-reconcile.sh; this leftover does not redo the product apply path"

LIVE_KC=SKIP
LIVE_DRY=false
TEST_SUMMARY=""

if [ "$SKIP_TESTS" = "0" ]; then
  command -v mvn >/dev/null 2>&1 || fail "mvn not on PATH"
  mvn -version | head -n 1 | tee -a "$OUT/collect.log"
  set +e
  mvn -pl nexus-p2-iam -am test \
    -Dtest=G10IdentityDriftHonestyTest \
    -Dsurefire.failIfNoSpecifiedTests=false | tee "$OUT/mvn.log"
  MVN_RC=${PIPESTATUS[0]}
  set -e
  [ "$MVN_RC" -eq 0 ] || fail "mvn leftover tests failed"
  TEST_SUMMARY="$(awk '/Tests run:/{line=$0} END{print line}' "$OUT/mvn.log")"
  if grep -q 'G10_DRIFT_DRY_RUN_APPLIED=false' "$OUT/mvn.log"; then
    LIVE_KC=PASS
    LIVE_DRY=true
    LIVE_REASON="live dry-run walk observed on ${ISSUER}"
  else
    LIVE_KC=SKIP
  fi
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
  kv dry_run_default_no_write true
  kv keycloak_8080_listener "$LISTENER"
  kv keycloak_oidc_discovery "$DISCOVERY"
  kv live_keycloak_present "$LIVE_PRESENT"
  kv live_keycloak_8080 "$LIVE_KC"
  kv live_keycloak_skip_reason "$LIVE_REASON"
  kv live_dry_run_observed "$LIVE_DRY"
  kv live_apply SKIP
  kv live_apply_skip_reason "$APPLY_REASON"
  kv live_apply_observed false
  kv identity_apply_product_redone false
  kv identity_drift_141_redone false
  kv file_reconcile_169_redone false
  kv saml_claimed false
  kv ldap_claimed false
  kv sms_mfa_claimed false
  kv g10_close NOT_CLAIMED
  kv g10_close_reason "Honesty leftover around identity drift-fix apply/dry-run. Live Keycloak 8080 SKIP when unavailable. Not product G10 close."
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv p0_m0_redone false
  kv test_summary "$TEST_SUMMARY"
} >"$REPORT"

MATRIX="$OUT/matrix.md"
{
  echo "| Check | Result | Notes |"
  echo "|---|---|---|"
  echo "| JDK 21 | PASS | $JAVA_VERSION_LINE |"
  echo "| Boot 4.1.1 | $BOOT_PARENT_MATCH | \`$BOOT_PARENT_GA\` |"
  echo "| Dry-run default no write | true | leftover refuses NEXUS_G10_APPLY |"
  echo "| Live Keycloak 8080 dry-run | $LIVE_KC | ${LIVE_REASON} |"
  echo "| live_dry_run_observed | $LIVE_DRY | apply observed=false |"
  echo "| Live apply | SKIP | ${APPLY_REASON} |"
  echo "| #141 identity drift redo | false | sibling leftover |"
  echo "| #169 file reconcile redo | false | sibling leftover |"
  echo "| SAML / LDAP / SMS MFA | false | honesty gate |"
  echo "| Docker used | $DOCKER_USED | present=$DOCKER_PRESENT |"
  echo "| G10 close | NOT_CLAIMED | G18 closed_unchanged; AuditPort untouched |"
} >"$MATRIX"

{
  echo "g10-identity-drift-honesty collect"
  echo "out=$OUT"
  echo
  cat "$MATRIX"
  echo
  echo "report=$REPORT"
} | tee "$OUT/summary.txt"

verify_report "$REPORT"

SANITIZED="${ROOT}/docs/engineering/g10-identity-drift-honesty/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

log "DONE report=$REPORT live_keycloak_8080=$LIVE_KC g10_close=NOT_CLAIMED"
echo
echo "PASS: honesty leftover recorded (live Keycloak 8080 $LIVE_KC)"
echo "See docs/g10-identity-drift-honesty.md"
echo "Keep both leftover rows: docs/g10-identity-drift-dry-run.md and docs/g10-file-reconcile-honesty.md"
echo "Not claimed: product G10 close / identity apply / SAML / LDAP / SMS MFA / #141 / #169"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
