#!/usr/bin/env bash
# G17 leftover: MFA recovery email / Vue OTP writeback honesty
# PASS/SKIP beyond #172/#158.
# Does not redo #96/#118/#133/#154/#158/#172 product code.
# Does not claim product G17 / 等保 / 密评.
# G18 stays closed. P0/M0 not redone. No Docker.
# Live IdP / SMTP is PASS only when delivery / enrollment / write-back
# was actually observed. Otherwise honest SKIP.
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
VERIFY_REPORT=""
SKIP_TESTS=0

REQUIRED_LEFTOVER_ROWS="g17-mfa-recovery-email-honesty g17-vue-otp-writeback g17-admin-mfa-recovery-email g17-mfa-recovery-vue-otp-honesty g9-redis-down-invalidate-remainder g2-h5-every-page-form-write-honesty g4-export-wizard-deny-honesty g10-leftover-honesty g5-mid-publish-failure-honesty g3-outbox-consume-crash-honesty g3-outbox-consume-idempotent g3-claim-crash-window g1-queryast-boundsql-honesty g1-queryast-boundsql-guard g15-wx-dt-native-bridge-honesty s0-secret-rotate-without-restart-honesty s0-secret-getenv-snapshot-honesty linux-arm-profile-leftover-honesty g4-p1-staging-audit-events-honesty g6-recovery-port-dry-run-honesty s0s1-live-redis-minio-isolation-honesty g17-separation-honesty g4-hot-purge-90d-honesty g1-fieldpolicy-mask-second-auth-honesty g10-identity-drift-honesty g15-native-bridge-honesty g15-wx-dt-prod-gap-honesty g4-pkce-spa-verifier-honesty g5-browser-failed-publish g7-withdraw-browser-honesty g7-query-expense-travel-honesty g2-form-write-honesty g9-redis-down-invalidate-honesty g10-file-reconcile-honesty"

usage() {
  cat <<'EOF'
Usage:
  scripts/g17-mfa-recovery-vue-otp-honesty.sh [--out DIR] [--skip-tests]
  scripts/g17-mfa-recovery-vue-otp-honesty.sh --verify-report FILE

#172 / #158 leftover harness contracts stay always-green PASS.
Live email delivery, recovery-authn-codes, and Vue otp write-back
are PASS only when observed. Missing live IdP / SMTP is honest SKIP.
Product G17 close stays NOT_CLAIMED.

  --out DIR           Output directory (default: .local/g17-mfa-recovery-vue-otp-honesty/<utc>)
  --skip-tests        Inventory + honesty only
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
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used leftover_172_harness leftover_158_harness leftover_rows_kept leftover_rows email_delivery email_delivery_skip_reason mailbox_observed smtp_enabled smtp_host_configured recovery_live recovery_live_skip_reason recovery_authn_codes_enrolled recovery_code_consumed_live vue_otp_writeback vue_otp_writeback_skip_reason product_writeback_endpoint otp_credential_stored keycloak_issuer_reachable oidc_discovery vue_writes_keycloak_otp dengbao_claimed mipin_claimed hsm_claimed g17_close g18_status audit_port_changed p0_m0_redone redo_96 redo_118 redo_133 redo_154 redo_158 redo_172"
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
  [ "$(report_get "$file" leftover_172_harness)" = "PASS" ] || fail "leftover_172_harness must be PASS"
  [ "$(report_get "$file" leftover_158_harness)" = "PASS" ] || fail "leftover_158_harness must be PASS"
  [ "$(report_get "$file" leftover_rows_kept)" = "true" ] || fail "leftover_rows_kept must stay true"
  [ "$(report_get "$file" redo_96)" = "false" ] || fail "redo_96 must stay false"
  [ "$(report_get "$file" redo_118)" = "false" ] || fail "redo_118 must stay false"
  [ "$(report_get "$file" redo_133)" = "false" ] || fail "redo_133 must stay false"
  [ "$(report_get "$file" redo_154)" = "false" ] || fail "redo_154 must stay false"
  [ "$(report_get "$file" redo_158)" = "false" ] || fail "must not redo #158"
  [ "$(report_get "$file" redo_172)" = "false" ] || fail "must not redo #172"

  local rows
  rows="$(report_get "$file" leftover_rows)"
  local required_row
  for required_row in $REQUIRED_LEFTOVER_ROWS; do
    echo "$rows" | grep -Fq "$required_row" || fail "leftover_rows must keep $required_row"
  done

  local email recovery writeback
  email="$(report_get "$file" email_delivery)"
  recovery="$(report_get "$file" recovery_live)"
  writeback="$(report_get "$file" vue_otp_writeback)"
  case "$email" in
    PASS|SKIP|FAIL) ;;
    *) fail "email_delivery must be PASS/SKIP/FAIL" ;;
  esac
  case "$recovery" in
    PASS|SKIP|FAIL) ;;
    *) fail "recovery_live must be PASS/SKIP/FAIL" ;;
  esac
  case "$writeback" in
    PASS|SKIP|FAIL) ;;
    *) fail "vue_otp_writeback must be PASS/SKIP/FAIL" ;;
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
  if [ "$writeback" = "PASS" ]; then
    [ "$(report_get "$file" keycloak_issuer_reachable)" = "true" ] || fail "vue_otp_writeback=PASS requires keycloak_issuer_reachable=true"
    [ "$(report_get "$file" oidc_discovery)" = "PASS" ] || fail "vue_otp_writeback=PASS requires oidc_discovery=PASS"
    [ "$(report_get "$file" product_writeback_endpoint)" = "true" ] || fail "vue_otp_writeback=PASS requires product_writeback_endpoint=true"
    [ "$(report_get "$file" otp_credential_stored)" = "true" ] || fail "vue_otp_writeback=PASS requires otp_credential_stored=true"
  fi
  if [ "$writeback" = "SKIP" ]; then
    [ -n "$(report_get "$file" vue_otp_writeback_skip_reason)" ] || fail "vue_otp_writeback=SKIP needs vue_otp_writeback_skip_reason"
    [ "$(report_get "$file" otp_credential_stored)" != "true" ] || fail "SKIP cannot claim otp_credential_stored=true"
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
  OUT="$ROOT/.local/g17-mfa-recovery-vue-otp-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G17 leftover: MFA recovery email / Vue OTP writeback honesty beyond #172/#158"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #96/#118/#133/#154/#158/#172. Not product G17 / 等保 / 密评. G18 stays closed."
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

README="$ROOT/docs/README.md"
[ -f "$README" ] || fail "missing $README"
for row in $REQUIRED_LEFTOVER_ROWS; do
  grep -q "\[${row}\]" "$README" || fail "docs/README.md must keep leftover row ${row}"
done
grep -q '\[g17-mfa-recovery-vue-otp-honesty\](\./g17-mfa-recovery-vue-otp-honesty.md)' "$README" \
  || fail "docs/README.md must list this leftover row"

echo "== Honesty fixtures (this leftover)"
verify_report "$HERE/g17-mfa-recovery-vue-otp-honesty/fixtures/honest-skip.env"
if ( verify_report "$HERE/g17-mfa-recovery-vue-otp-honesty/fixtures/fake-delivery-pass.env" ); then
  fail "fake-delivery-pass.env must be rejected"
fi
echo "PASS: fake-delivery-pass.env rejected"
if ( verify_report "$HERE/g17-mfa-recovery-vue-otp-honesty/fixtures/fake-writeback-pass.env" ); then
  fail "fake-writeback-pass.env must be rejected"
fi
echo "PASS: fake-writeback-pass.env rejected"
if ( verify_report "$HERE/g17-mfa-recovery-vue-otp-honesty/fixtures/fake-g17-close.env" ); then
  fail "fake-g17-close.env must be rejected"
fi
echo "PASS: fake-g17-close.env rejected"
if ( verify_report "$HERE/g17-mfa-recovery-vue-otp-honesty/fixtures/fake-redone.env" ); then
  fail "fake-redone.env must be rejected"
fi
echo "PASS: fake-redone.env rejected"

echo
echo "== Parent leftover harness still PASS (#172 / #158 verify-report only; not a product redo)"
"$ROOT/scripts/g17-mfa-recovery-email-honesty.sh" --verify-report \
  "$ROOT/scripts/g17-mfa-recovery-email-honesty/fixtures/honest-skip.env"
"$ROOT/scripts/g17-vue-otp-writeback.sh" --verify-report \
  "$ROOT/scripts/g17-vue-otp-writeback/fixtures/honest-skip.env"
LEFTOVER_172=PASS
LEFTOVER_158=PASS

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
PRODUCT_ENDPOINT=false
OTP_STORED=false
EMAIL_DELIVERY=SKIP
RECOVERY_LIVE=SKIP
WRITEBACK=SKIP
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
  WRITEBACK_SKIP_REASON="keycloak_issuer_unreachable"
else
  RECOVERY_SKIP_REASON="recovery_authn_codes_not_enrolled"
  WRITEBACK_SKIP_REASON="product_vue_writeback_endpoint_absent"
fi

TEST_SUMMARY="parent --verify-report only; no #158/#172 product redo"
if [ "$SKIP_TESTS" = "1" ]; then
  TEST_SUMMARY="skipped (--skip-tests); parent --verify-report still PASS"
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
  kv leftover_172_harness "$LEFTOVER_172"
  kv leftover_158_harness "$LEFTOVER_158"
  kv leftover_rows_kept true
  kv leftover_rows "$(echo "$REQUIRED_LEFTOVER_ROWS" | tr ' ' ',')"
  kv email_delivery "$EMAIL_DELIVERY"
  kv email_delivery_skip_reason "$EMAIL_SKIP_REASON"
  kv mailbox_observed "$MAILBOX_OBSERVED"
  kv smtp_enabled "$SMTP_ENABLED"
  kv smtp_host_configured "$SMTP_HOST_CONFIGURED"
  kv recovery_live "$RECOVERY_LIVE"
  kv recovery_live_skip_reason "$RECOVERY_SKIP_REASON"
  kv recovery_authn_codes_enrolled "$RECOVERY_ENROLLED"
  kv recovery_code_consumed_live "$RECOVERY_CONSUMED"
  kv vue_otp_writeback "$WRITEBACK"
  kv vue_otp_writeback_skip_reason "$WRITEBACK_SKIP_REASON"
  kv product_writeback_endpoint "$PRODUCT_ENDPOINT"
  kv otp_credential_stored "$OTP_STORED"
  kv keycloak_issuer_reachable "$KC_REACHABLE"
  kv oidc_discovery "$OIDC_DISCOVERY"
  kv probed_issuer "${KEYCLOAK_ISSUER:-}"
  kv vue_writes_keycloak_otp false
  kv dengbao_claimed false
  kv mipin_claimed false
  kv hsm_claimed false
  kv g17_close NOT_CLAIMED
  kv g17_close_reason "Honesty leftover beyond #172/#158. Parent harness PASS. Live IdP/SMTP SKIP when unavailable. Not product G17 / 等保 / 密评."
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv p0_m0_redone false
  kv redo_96 false
  kv redo_118 false
  kv redo_133 false
  kv redo_154 false
  kv redo_158 false
  kv redo_172 false
  kv test_summary "$TEST_SUMMARY"
} >"$REPORT"

MATRIX="$OUT/matrix.md"
{
  echo "| Check | Result | Notes |"
  echo "|---|---|---|"
  echo "| JDK 21 | PASS | $JAVA_VERSION_LINE |"
  echo "| Boot 4.1.1 | $BOOT_PARENT_MATCH | \`$BOOT_PARENT_GA\` |"
  echo "| #172 leftover harness | $LEFTOVER_172 | parent --verify-report; not a product redo |"
  echo "| #158 leftover harness | $LEFTOVER_158 | parent --verify-report; not a product redo |"
  echo "| Live email OTP mailbox | $EMAIL_DELIVERY | ${EMAIL_SKIP_REASON} |"
  echo "| Live Keycloak recovery-authn-codes | $RECOVERY_LIVE | ${RECOVERY_SKIP_REASON} |"
  echo "| Live Vue otp write-back | $WRITEBACK | ${WRITEBACK_SKIP_REASON} |"
  echo "| Leftover rows kept | true | #172 + #158 + ALL siblings |"
  echo "| Docker used | $DOCKER_USED | present=$DOCKER_PRESENT |"
  echo "| Product G17 / 等保 / 密评 | NOT_CLAIMED | G18 closed_unchanged; AuditPort untouched |"
} >"$MATRIX"

{
  echo "g17-mfa-recovery-vue-otp-honesty collect"
  echo "out=$OUT"
  echo
  cat "$MATRIX"
  echo
  echo "report=$REPORT"
} | tee "$OUT/summary.txt"

verify_report "$REPORT"

SANITIZED="${ROOT}/docs/engineering/g17-mfa-recovery-vue-otp-honesty/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

log "DONE report=$REPORT email=$EMAIL_DELIVERY recovery=$RECOVERY_LIVE writeback=$WRITEBACK g17_close=NOT_CLAIMED"
echo
echo "PASS: honesty leftover recorded (#172/#158 harness PASS; email_delivery=$EMAIL_DELIVERY recovery_live=$RECOVERY_LIVE vue_otp_writeback=$WRITEBACK)"
echo "See docs/g17-mfa-recovery-vue-otp-honesty.md"
echo "Keep leftover rows: docs/g17-mfa-recovery-email-honesty.md and docs/g17-vue-otp-writeback.md"
echo "Not claimed: product G17 / 等保 / 密评 / Vue→Keycloak otp / mailbox delivery"
echo "Not redone: #96/#118/#133/#154/#158/#172 / P0 / M0"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
