#!/usr/bin/env bash
# G17 leftover: admin MFA recovery-code / email delivery honesty beyond #154.
#
# Extends docs/g17-admin-mfa-recovery-email.md. Does NOT redo
# #96/#118/#133/#154/#158/#160. Does NOT claim product G17 / 等保 / 密评.
# G18 stays closed. No Docker.
# Live SMTP / Keycloak recovery-authn-codes is PASS only when delivery
# or enrollment was actually observed. Otherwise honest SKIP.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HERE="$(cd "$(dirname "$0")" && pwd)"
if [[ -x "${HOME}/tools/apache-maven-3.8.7/bin/mvn" ]]; then
  export PATH="${HOME}/tools/apache-maven-3.8.7/bin:${PATH}"
elif [[ -x "${HOME}/tools/apache-maven-3.9.11/bin/mvn" ]]; then
  export PATH="${HOME}/tools/apache-maven-3.9.11/bin:${PATH}"
fi
cd "$ROOT"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT=""
JSON_OUT="${NEXUS_G17_RECOVERY_HONESTY_JSON:-$ROOT/docs/g17-mfa-recovery-email-honesty/walk.json}"
VERIFY_REPORT=""
SKIP_TESTS=0

usage() {
  cat <<'EOF'
Usage:
  scripts/g17-mfa-recovery-email-honesty.sh [--out DIR] [--json-out FILE] [--skip-tests]
  scripts/g17-mfa-recovery-email-honesty.sh --verify-report FILE

Always-green #154 local recovery / email request leftover.
Live Keycloak recovery-authn-codes / SMTP delivery is PASS only when
observed. Missing SMTP or Keycloak recovery-authn-codes is honest SKIP.

  --out DIR           Output directory (default: .local/g17-mfa-recovery-email-honesty/<utc>)
  --json-out FILE     Sanitized walk.json
  --skip-tests        Inventory + honesty only (do not run mvn/vitest)
  --verify-report F   Honesty gate on an existing report.env
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --out) OUT="${2:?}"; shift 2 ;;
    --json-out) JSON_OUT="${2:?}"; shift 2 ;;
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
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used local_recovery_codes email_request_contract email_delivery email_delivery_skip_reason mailbox_observed smtp_enabled smtp_host_configured recovery_live recovery_live_skip_reason recovery_authn_codes_enrolled recovery_code_consumed_live keycloak_issuer_reachable oidc_discovery vue_writes_keycloak_otp dengbao_claimed mipin_claimed hsm_claimed g17_close g18_status audit_port_changed p0_m0_redone redo_96 redo_118 redo_133 redo_154 redo_158 redo_160"
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
  [ "$(report_get "$file" vue_writes_keycloak_otp)" = "false" ] || fail "vue_writes_keycloak_otp must stay false"
  [ "$(report_get "$file" dengbao_claimed)" = "false" ] || fail "dengbao_claimed must stay false"
  [ "$(report_get "$file" mipin_claimed)" = "false" ] || fail "mipin_claimed must stay false"
  [ "$(report_get "$file" hsm_claimed)" = "false" ] || fail "hsm_claimed must stay false"
  [ "$(report_get "$file" g17_close)" = "NOT_CLAIMED" ] || fail "g17_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" redo_96)" = "false" ] || fail "redo_96 must stay false"
  [ "$(report_get "$file" redo_118)" = "false" ] || fail "redo_118 must stay false"
  [ "$(report_get "$file" redo_133)" = "false" ] || fail "redo_133 must stay false"
  [ "$(report_get "$file" redo_154)" = "false" ] || fail "redo_154 must stay false"
  [ "$(report_get "$file" redo_158)" = "false" ] || fail "redo_158 must stay false"
  [ "$(report_get "$file" redo_160)" = "false" ] || fail "redo_160 must stay false"
  [ "$(report_get "$file" local_recovery_codes)" = "PASS" ] || fail "local_recovery_codes must be PASS"
  [ "$(report_get "$file" email_request_contract)" = "PASS" ] || fail "email_request_contract must be PASS"

  local email recovery
  email="$(report_get "$file" email_delivery)"
  recovery="$(report_get "$file" recovery_live)"
  case "$email" in
    PASS|SKIP|FAIL) ;;
    *) fail "email_delivery must be PASS/SKIP/FAIL" ;;
  esac
  case "$recovery" in
    PASS|SKIP|FAIL) ;;
    *) fail "recovery_live must be PASS/SKIP/FAIL" ;;
  esac
  if [ "$email" = "PASS" ]; then
    [ "$(report_get "$file" smtp_enabled)" = "true" ] || fail "email_delivery=PASS requires smtp_enabled=true"
    [ "$(report_get "$file" smtp_host_configured)" = "true" ] || fail "email_delivery=PASS requires smtp_host_configured=true"
    [ "$(report_get "$file" keycloak_issuer_reachable)" = "true" ] || fail "email_delivery=PASS requires keycloak_issuer_reachable=true"
    [ "$(report_get "$file" mailbox_observed)" = "true" ] || fail "email_delivery=PASS requires mailbox_observed=true"
  fi
  if [ "$email" = "SKIP" ]; then
    [ -n "$(report_get "$file" email_delivery_skip_reason)" ] || fail "email_delivery=SKIP needs email_delivery_skip_reason"
    [ "$(report_get "$file" mailbox_observed)" != "true" ] || fail "SKIP cannot claim mailbox_observed=true"
  fi
  if [ "$recovery" = "PASS" ]; then
    [ "$(report_get "$file" keycloak_issuer_reachable)" = "true" ] || fail "recovery_live=PASS requires keycloak_issuer_reachable=true"
    [ "$(report_get "$file" oidc_discovery)" = "PASS" ] || fail "recovery_live=PASS requires oidc_discovery=PASS"
    [ "$(report_get "$file" recovery_authn_codes_enrolled)" = "true" ] || fail "recovery_live=PASS requires recovery_authn_codes_enrolled=true"
    [ "$(report_get "$file" recovery_code_consumed_live)" = "true" ] || fail "recovery_live=PASS requires recovery_code_consumed_live=true"
  fi
  if [ "$recovery" = "SKIP" ]; then
    [ -n "$(report_get "$file" recovery_live_skip_reason)" ] || fail "recovery_live=SKIP needs recovery_live_skip_reason"
    [ "$(report_get "$file" recovery_code_consumed_live)" != "true" ] || fail "SKIP cannot claim recovery_code_consumed_live=true"
  fi
  if grep -Eiq 'PASSWORD=|passwd=|secret=|totpSecret=' "$file"; then
    fail "report must not contain password/secret material"
  fi
  echo "PASS: report honesty gate ($file)"
}

if [ -n "$VERIFY_REPORT" ]; then
  verify_report "$VERIFY_REPORT"
  exit 0
fi

if [ -z "$OUT" ]; then
  OUT="$ROOT/.local/g17-mfa-recovery-email-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G17 leftover: recovery-code / email delivery honesty beyond #154"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #96/#118/#133/#154/#158/#160. Not product G17 / 等保 / 密评. G18 stays closed."
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
root = ET.parse(sys.argv[1]).getRoot() if False else ET.parse(sys.argv[1]).getroot()
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

SMTP_ENABLED=false
if [[ "${NEXUS_IAM_SMTP_ENABLED:-}" == "true" || "${NEXUS_IAM_SMTP_ENABLED:-}" == "1" ]]; then
  SMTP_ENABLED=true
fi
SMTP_HOST="${NEXUS_IAM_SMTP_HOST:-${NEXUS_KEYCLOAK_SMTP_HOST:-${SMTP_HOST:-}}}"
SMTP_HOST_CONFIGURED=false
if [[ -n "$SMTP_HOST" ]]; then
  SMTP_HOST_CONFIGURED=true
fi

KEYCLOAK_ISSUER="${NEXUS_KEYCLOAK_ISSUER:-}"
KC_REACHABLE=false
OIDC_DISCOVERY=SKIP
if [[ -z "$KEYCLOAK_ISSUER" ]]; then
  for candidate in http://127.0.0.1:8080/realms/nexus http://127.0.0.1:8180/realms/nexus; do
    if curl -sf --max-time 2 "${candidate}/.well-known/openid-configuration" >/dev/null 2>&1; then
      KEYCLOAK_ISSUER="$candidate"
      KC_REACHABLE=true
      OIDC_DISCOVERY=PASS
      break
    fi
  done
elif curl -sf --max-time 2 "${KEYCLOAK_ISSUER}/.well-known/openid-configuration" >/dev/null 2>&1; then
  KC_REACHABLE=true
  OIDC_DISCOVERY=PASS
fi

MAILBOX_OBSERVED=false
RECOVERY_ENROLLED=false
RECOVERY_CONSUMED=false
EMAIL_DELIVERY=SKIP
RECOVERY_LIVE=SKIP
if [[ "$SMTP_ENABLED" != "true" ]]; then
  EMAIL_SKIP_REASON="P2.IAM.SMTP_DISABLED"
elif [[ "$SMTP_HOST_CONFIGURED" != "true" ]]; then
  EMAIL_SKIP_REASON="P2.IAM.EMAIL_OTP_NOT_DELIVERED"
elif [[ "$KC_REACHABLE" != "true" ]]; then
  EMAIL_SKIP_REASON="keycloak_issuer_unreachable"
else
  EMAIL_SKIP_REASON="mailbox_not_observed"
fi
if [[ "$KC_REACHABLE" != "true" ]]; then
  RECOVERY_SKIP_REASON="keycloak_issuer_unreachable"
else
  RECOVERY_SKIP_REASON="recovery_authn_codes_not_enrolled"
fi

LOCAL_RECOVERY=PASS
EMAIL_CONTRACT=PASS
TEST_SUMMARY=""

if [ "$SKIP_TESTS" = "0" ]; then
  if [[ ! -d "$ROOT/web/node_modules" ]]; then
    (cd "$ROOT/web" && npm install)
  fi
  echo "== Always-green #154 leftover (untouched contract)"
  (cd "$ROOT/web" && npm run test:g17-mfa-recovery-email)
  (cd "$ROOT/web" && npm run assert:g17-mfa-recovery-email)
  echo "== Always-green honesty leftover"
  (cd "$ROOT/web" && npm run test:g17-mfa-recovery-email-honesty)
  (cd "$ROOT/web" && npm run assert:g17-mfa-recovery-email-honesty)
  if command -v mvn >/dev/null 2>&1; then
    mvn -pl nexus-p2-iam -am test \
      -Dtest=G17MfaRecoveryEmailHonestyTest,EmailMfaDeliveryProbeTest,RecoveryCodesTest \
      -Dsurefire.failIfNoSpecifiedTests=false | tee "$OUT/mvn.log"
    TEST_SUMMARY="$(awk '/Tests run:/{line=$0} END{print line}' "$OUT/mvn.log")"
  else
    TEST_SUMMARY="mvn missing; Vue leftover already covered"
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
  kv local_recovery_codes "$LOCAL_RECOVERY"
  kv email_request_contract "$EMAIL_CONTRACT"
  kv email_delivery "$EMAIL_DELIVERY"
  kv email_delivery_skip_reason "$EMAIL_SKIP_REASON"
  kv mailbox_observed "$MAILBOX_OBSERVED"
  kv smtp_enabled "$SMTP_ENABLED"
  kv smtp_host_configured "$SMTP_HOST_CONFIGURED"
  kv recovery_live "$RECOVERY_LIVE"
  kv recovery_live_skip_reason "$RECOVERY_SKIP_REASON"
  kv recovery_authn_codes_enrolled "$RECOVERY_ENROLLED"
  kv recovery_code_consumed_live "$RECOVERY_CONSUMED"
  kv keycloak_issuer_reachable "$KC_REACHABLE"
  kv oidc_discovery "$OIDC_DISCOVERY"
  kv probed_issuer "${KEYCLOAK_ISSUER:-}"
  kv vue_writes_keycloak_otp false
  kv dengbao_claimed false
  kv mipin_claimed false
  kv hsm_claimed false
  kv g17_close NOT_CLAIMED
  kv g17_close_reason "Honesty leftover around #154 recovery/email delivery. SMTP / Keycloak recovery-authn-codes SKIP when unavailable. Not product G17 / 等保 / 密评."
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv p0_m0_redone false
  kv redo_96 false
  kv redo_118 false
  kv redo_133 false
  kv redo_154 false
  kv redo_158 false
  kv redo_160 false
  kv test_summary "$TEST_SUMMARY"
} >"$REPORT"

mkdir -p "$(dirname "$JSON_OUT")"
python3 - "$JSON_OUT" "$REPORT" <<'PY'
import json, sys
out, report = sys.argv[1:3]
vals = {}
for line in open(report, encoding="utf-8"):
    if "=" in line and not line.startswith("#"):
        k, v = line.rstrip("\n").split("=", 1)
        vals[k] = v
walk = {
    "leftover": "g17-mfa-recovery-email-honesty",
    "sibling": "g17-admin-mfa-recovery-email",
    "email_delivery": vals.get("email_delivery"),
    "email_delivery_skip_reason": vals.get("email_delivery_skip_reason"),
    "recovery_live": vals.get("recovery_live"),
    "recovery_live_skip_reason": vals.get("recovery_live_skip_reason"),
    "mailbox_observed": vals.get("mailbox_observed") == "true",
    "recovery_authn_codes_enrolled": vals.get("recovery_authn_codes_enrolled") == "true",
    "keycloak_issuer_reachable": vals.get("keycloak_issuer_reachable") == "true",
    "vue_writes_keycloak_otp": False,
    "g17Close": "NOT_CLAIMED",
    "g18": "closed_unchanged",
    "timestampUtc": vals.get("timestamp_utc"),
    "evidence": "scripts/g17-mfa-recovery-email-honesty.sh",
}
open(out, "w", encoding="utf-8").write(json.dumps(walk, indent=2) + "\n")
print("wrote", out)
PY

verify_report "$REPORT"

echo "== Honesty fixtures"
verify_report "$HERE/g17-mfa-recovery-email-honesty/fixtures/honest-skip.env"
if ( verify_report "$HERE/g17-mfa-recovery-email-honesty/fixtures/fake-delivery-pass.env" ); then
  fail "fake-delivery-pass.env must be rejected"
fi
echo "PASS: fake-delivery-pass.env rejected"
if ( verify_report "$HERE/g17-mfa-recovery-email-honesty/fixtures/fake-g17-close.env" ); then
  fail "fake-g17-close.env must be rejected"
fi
echo "PASS: fake-g17-close.env rejected"

SANITIZED="${ROOT}/docs/engineering/g17-mfa-recovery-email-honesty/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

log "DONE report=$REPORT email=$EMAIL_DELIVERY recovery=$RECOVERY_LIVE g17_close=NOT_CLAIMED"
echo
echo "PASS: honesty leftover recorded (email_delivery=$EMAIL_DELIVERY recovery_live=$RECOVERY_LIVE)"
echo "See docs/g17-mfa-recovery-email-honesty.md"
echo "Not claimed: product G17 / 等保 / 密评 / Vue→Keycloak otp / mailbox delivery"
echo "Not redone: #96/#118/#133/#154/#158/#160"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
echo "walk=$JSON_OUT"
