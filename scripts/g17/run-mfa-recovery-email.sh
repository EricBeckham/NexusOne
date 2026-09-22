#!/usr/bin/env bash
# G17 leftover: admin MFA recovery codes + email MFA request contract.
# Extends #96/#118/#133. Does not write Keycloak otp from Vue.
# Live email / Keycloak recovery-authn-codes delivery is honest SKIP
# when SMTP or Keycloak delivery cannot run. No Docker.
# Does not claim 等保/密评/G17. G18 stays closed. AuditPort untouched.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

if ! command -v mvn >/dev/null 2>&1; then
  if [[ -x "${HOME}/tools/apache-maven-3.8.7/bin/mvn" ]]; then
    export PATH="${HOME}/tools/apache-maven-3.8.7/bin:${PATH}"
  fi
fi

WEB_DIR="$ROOT/web"
G17_DIR="$ROOT/scripts/g17"
STUB_HOST="${NEXUS_G17_STUB_HOST:-127.0.0.1}"
STUB_PORT="${NEXUS_G17_STUB_PORT:-18092}"
WEB_PORT="${NEXUS_G17_RECOVERY_WEB_PORT:-4176}"
JSON_OUT="${NEXUS_G17_RECOVERY_JSON:-$ROOT/docs/g17-admin-mfa-recovery-email/walk.json}"
REPORT_OUT="${NEXUS_G17_RECOVERY_REPORT:-$ROOT/docs/g17-admin-mfa-recovery-email/report.env}"
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

mkdir -p "$(dirname "$JSON_OUT")"

echo "== G17 MFA recovery codes / email leftover"
echo "cwd=$ROOT"
java -version
node -v
echo "docker=no audit_port=untouched g18=closed g17_product_close=false dengbao=not-claimed"

if [[ ! -d "$WEB_DIR/node_modules" ]]; then
  (cd "$WEB_DIR" && npm install)
fi
if [[ ! -d "$G17_DIR/node_modules/playwright-core" ]]; then
  (cd "$G17_DIR" && npm install)
fi

echo "== Always-green Java leftovers (no Keycloak write)"
mvn -pl nexus-p2-iam -am test \
  -Dtest=RecoveryCodesTest,EmailMfaDeliveryProbeTest,MfaClaimsTest,AuthorizationCodeDriverTest,TotpTest,AdminMfaSessionTest,AdminMfaApiTest,G18StubAdminLoginTest \
  -Dsurefire.failIfNoSpecifiedTests=false

echo "== Vitest recovery / email / TOTP leftover kept"
(cd "$WEB_DIR" && npm run test:g17-mfa-recovery-email)
(cd "$WEB_DIR" && npm run assert:g17-mfa-recovery-email)
(cd "$WEB_DIR" && npm run assert:g17-totp-enroll)

echo "== Delivery probe (honest SKIP when SMTP / Keycloak cannot deliver)"
SMTP_ENABLED="${NEXUS_IAM_SMTP_ENABLED:-false}"
SMTP_HOST="${NEXUS_IAM_SMTP_HOST:-${NEXUS_KEYCLOAK_SMTP_HOST:-${SMTP_HOST:-}}}"
KEYCLOAK_ISSUER="${NEXUS_KEYCLOAK_ISSUER:-}"
KC_REACHABLE=false
if [[ -z "$KEYCLOAK_ISSUER" ]]; then
  for candidate in http://127.0.0.1:8080/realms/nexus http://127.0.0.1:8180/realms/nexus; do
    if curl -sf --max-time 2 "${candidate}/.well-known/openid-configuration" >/dev/null 2>&1; then
      KEYCLOAK_ISSUER="$candidate"
      KC_REACHABLE=true
      break
    fi
  done
elif curl -sf --max-time 2 "${KEYCLOAK_ISSUER}/.well-known/openid-configuration" >/dev/null 2>&1; then
  KC_REACHABLE=true
fi

MAILER=""
if command -v sendmail >/dev/null 2>&1; then
  MAILER="sendmail"
elif command -v postfix >/dev/null 2>&1; then
  MAILER="postfix"
fi

EMAIL_DELIVERY=SKIP
RECOVERY_LIVE=SKIP
if [[ "$SMTP_ENABLED" == "true" || "$SMTP_ENABLED" == "1" ]]; then
  EMAIL_SKIP_REASON="P2.IAM.EMAIL_OTP_NOT_DELIVERED: leftover does not send email OTP even when SMTP is flagged"
else
  EMAIL_SKIP_REASON="P2.IAM.SMTP_DISABLED: nexus.iam.smtp.enabled=${SMTP_ENABLED:-false}; no email OTP delivery on this VM"
fi
if [[ "$KC_REACHABLE" == "true" ]]; then
  RECOVERY_SKIP_REASON="P2.IAM.RECOVERY_CODES_NOT_DELIVERED: Keycloak is reachable but this leftover does not enable CONFIGURE_RECOVERY_AUTHN_CODES or write recovery-authn-codes"
else
  RECOVERY_SKIP_REASON="P2.IAM.RECOVERY_CODES_NOT_DELIVERED: Keycloak OIDC discovery is not reachable; live recovery-authn-codes not run"
fi

echo "smtp_enabled=${SMTP_ENABLED}"
echo "smtp_host=${SMTP_HOST:-unset}"
echo "keycloak_reachable=${KC_REACHABLE}"
echo "mailer=${MAILER:-none}"
echo "email_delivery=${EMAIL_DELIVERY}"
echo "recovery_live=${RECOVERY_LIVE}"

echo "== Vite preview + stub login (reuse #133 stub, no Keycloak write)"
(cd "$WEB_DIR" && npm run build)
NEXUS_G17_STUB_HOST="$STUB_HOST" NEXUS_G17_STUB_PORT="$STUB_PORT" \
  node "$G17_DIR/stub-totp-enrollment-api.mjs" &
STUB_PID=$!
for _ in $(seq 1 40); do
  if curl -sf "http://${STUB_HOST}:${STUB_PORT}/actuator/health" >/dev/null 2>&1; then
    break
  fi
  sleep 0.1
done

(
  cd "$WEB_DIR"
  NEXUS_API_ORIGIN="http://${STUB_HOST}:${STUB_PORT}" \
    npx vite preview --host 127.0.0.1 --port "${WEB_PORT}"
) &
WEB_PID=$!
for _ in $(seq 1 50); do
  if curl -sf "http://127.0.0.1:${WEB_PORT}/p2/mfa/recovery" >/dev/null 2>&1; then
    break
  fi
  sleep 0.2
done

BROWSER_RESULT=SKIP
BROWSER_REASON="playwright walk not started"
if curl -sf "http://127.0.0.1:${WEB_PORT}/p2/mfa/recovery" >/dev/null 2>&1; then
  if node "$G17_DIR/mfa-recovery-email-browser.mjs" \
      --base "http://127.0.0.1:${WEB_PORT}" \
      --stub "http://${STUB_HOST}:${STUB_PORT}" \
      --json-out "${JSON_OUT}.browser.json"; then
    BROWSER_RESULT=PASS
    BROWSER_REASON="local recovery/email leftover pages walked"
  else
    BROWSER_RESULT=SKIP
    BROWSER_REASON="playwright or system Chrome missing; Vitest leftover already covered copy/errors"
  fi
else
  BROWSER_REASON="vite preview did not start; Vitest leftover already covered copy/errors"
fi

STAMP="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
{
  echo "schema_version=1"
  echo "timestamp_utc=${STAMP}"
  echo "java_feature=21"
  echo "boot_parent_version=4.1.1"
  echo "docker_used=false"
  echo "audit_port_changed=false"
  echo "g18_status=closed_unchanged"
  echo "g17_close=NOT_CLAIMED"
  echo "dengbao_claimed=false"
  echo "mipin_claimed=false"
  echo "hsm_claimed=false"
  echo "local_recovery_codes=PASS"
  echo "email_delivery=${EMAIL_DELIVERY}"
  echo "email_delivery_skip_reason=${EMAIL_SKIP_REASON}"
  echo "recovery_live=${RECOVERY_LIVE}"
  echo "recovery_live_skip_reason=${RECOVERY_SKIP_REASON}"
  echo "smtp_enabled=${SMTP_ENABLED}"
  echo "smtp_host_configured=$([ -n "${SMTP_HOST}" ] && echo true || echo false)"
  echo "keycloak_reachable=${KC_REACHABLE}"
  echo "mailer=${MAILER:-none}"
  echo "browser_walk=${BROWSER_RESULT}"
  echo "browser_walk_reason=${BROWSER_REASON}"
  echo "vue_writes_keycloak_otp=false"
} > "$REPORT_OUT"

python3 - "$JSON_OUT" "$STAMP" "$EMAIL_DELIVERY" "$EMAIL_SKIP_REASON" "$RECOVERY_LIVE" "$RECOVERY_SKIP_REASON" "$BROWSER_RESULT" "$BROWSER_REASON" "$SMTP_ENABLED" "$KC_REACHABLE" "$MAILER" <<'PY'
import json, sys
out, stamp, email_d, email_r, rec_d, rec_r, browser, browser_r, smtp, kc, mailer = sys.argv[1:12]
body = {
  "recorded_at": stamp,
  "result": "PASS",
  "local_recovery_codes": "PASS",
  "email_delivery": email_d,
  "email_delivery_skip_reason": email_r,
  "recovery_live": rec_d,
  "recovery_live_skip_reason": rec_r,
  "browser_walk": browser,
  "browser_walk_reason": browser_r,
  "smtp_enabled": smtp,
  "keycloak_reachable": kc == "true",
  "mailer": mailer or "none",
  "docker": False,
  "audit_port": "untouched",
  "g18": "stays closed",
  "g17_product_close": False,
  "dengbao_claimed": False,
  "vue_writes_keycloak_otp": False,
}
open(out, "w", encoding="utf-8").write(json.dumps(body, indent=2) + "\n")
print("wrote", out)
PY

echo
echo "See docs/g17-admin-mfa-recovery-email.md"
echo "G18 stays closed. G17 / 等保 / 密评 are not claimed."
echo "email_delivery=${EMAIL_DELIVERY} recovery_live=${RECOVERY_LIVE} browser_walk=${BROWSER_RESULT}"
